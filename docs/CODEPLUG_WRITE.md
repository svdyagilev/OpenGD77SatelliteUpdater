# Selective codeplug write (0.6.1)

MD-9600 / OpenGD77RUS only (firmware radio type 5). Existing records only:
DMR ID/name, channels 1–1024, DMR and DTMF contacts, zones. Multiple sections
can be selected. Editor saving still only saves the local project.

## Protocol evidence

Primary implementation: hmatuschek/qdmr `lib/openuv380_codeplug.hh`
(commit file blob b6459273c64de37239fd7d0e3f640e6cfc6ee45b),
`lib/opengd77_interface.cc` (blob b823678875ee86288a0d8456a3159a3696e38972),
`lib/opengd77_interface.hh` (blob 2d489d025f67bd5593fbb52481fa5ae828a8682a).
https://github.com/hmatuschek/qdmr/blob/master/lib/openuv380_codeplug.hh
https://github.com/hmatuschek/qdmr/blob/master/lib/opengd77_interface.cc

MD-9600 uses the OpenUV380 protocol variant: physical FLASH for low
EEPROM-compatible regions as well as high codeplug banks. Low addresses are
unchanged; high banks have the existing +0x20000 remap. Use R01 and X01/X02/X03,
4096-byte sectors, 32-byte transfer payloads. No X04 EEPROM writes are used.
The original project's R02 low-block images must exactly match R01 before
writing: a differing firmware memory mapping fails closed.

Zone record geometry remains the existing OpenGD77RUS reader/editor geometry;
it is not replaced by qDMR's standard-firmware 68-zone limit.

## Sequence and recovery

1. User selects sections and confirms the concrete changed-byte summary.
2. Match type, firmware revision and info version; enter CPS and save settings/VFOs.
3. Compare every changed selected block against the original project, plus general
   settings as an identity anchor. Serial number is unavailable: this is not a
   cryptographic or serial-number device binding.
4. Read complete dirty physical sectors. Recheck selected overlaps. Overlay ONLY
   changed allowed bits/bytes of existing editor-supported records. Preserve
   neighboring bytes including unselected section bytes.
5. Durably save a timestamped ZIP with original/updated project and complete
   before/after sectors. Create a durable pending marker BEFORE first X write.
6. Write each complete sector and read it back. Stop on first error; never retry
   automatically. Final pass verifies every dirty sector again.
7. Save updated project baseline for written blocks only; keep unselected edits.
   Clear pending marker, close CPS, reboot. Reboot acknowledgement loss is reported
   separately from a verified data write.

Pending marker blocks another write until a complete fresh radio read succeeds.
After a failed write, export project/backup, restart radio and read again. No
automatic rollback is attempted. Export latest recovery ZIP from the write screen.
ZIP is diagnostic recovery evidence, NOT a Windows .ogd or a firmware image.

USB ownership is exclusive within the app. Rotation/configuration changes keep
the write activity alive; screen stays awake, Back is disabled during the session.
OS process death / power or USB loss can still leave a partial write; pending
marker survives and requires a fresh read. A single attempt is allowed per screen.

## Validation

JVM tests cover multi-section overlays and channel bank boundaries, unrelated byte
preservation, stale data in a late block (zero writes), identity anchor mismatch,
backup failure (zero writes), first-sector verification failure/disconnect (no
retry or later writes), unsupported raw edits, no-op selection, subset baseline,
and preserving externally changed unselected bytes in a shared sector.
Physical MD-9600 write testing must be performed on hardware; not claimed by CI.

## 0.6.2 extension

Three additional selectable sections: boot screen (snapshot block 7), RX groups
(block 11), APRS (block 3). Boot preflight compares only the first 0x48 bytes,
excluding cached VFOs in the same snapshot block. Full-sector reads preserve the
actual current VFO bytes when boot is written. RX group writes additionally
compare the original contact bank before any write.

Primary references: qDMR `lib/opengd77base_codeplug.hh`
(blob a98168b651ce0097600b51a3b0e317c5f3c146f4) and `.cc`, classes
BootSettingsElement, GroupListElement/GroupListBankElement, APRSSettingsElement.
Boot addresses independently match open-ham/OpenGD77
`firmware/source/functions/codeplug.c` (0x7518, 0x7540, 0x7550).

- Boot whitelist: relative byte 0 and 0x28..0x47 only.
- RX groups: only original existing slots and their length byte; count is N+1,
  contact references are 1-based little-endian words, unused entries zero.
  Names use up to 15 characters plus 0xFF terminator.
- APRS: existing records only. Name is CP1251 with the RUS lowercase-ya mapping;
  route and comment are ASCII and zero-padded. Comment accepts 23 characters in
  its 24-byte terminated field. TX is binary little-endian Hz/10 (NOT BCD).
  Coordinates use sign bit 23, degrees in bits 15..22, decimal fraction x10000
  in the low 15 bits. Only flag bits 0..2 are writable; symbol bytes 29..30,
  reserved 59..60 and magic 62..63 remain unchanged.

11 additional tests cover wire values, slot boundaries, empty groups, validation
atomicity, field preservation, no-op edits, project round-trip, write subset
rebasing, live VFO preservation and stale contact dependencies.
