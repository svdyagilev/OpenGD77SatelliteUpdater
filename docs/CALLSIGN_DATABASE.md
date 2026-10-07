# MD-9600 callsign database (v0.8.2)

The database is separate from codeplug contacts and is replaced as a whole.
No call-sign data is uploaded to a service; RadioID is downloaded via HTTPS.
CSV import requires a header and supports comma/semicolon delimiters, quotes,
escaped quotes and multiline fields. UTF-8 is strict; Windows-1251 is selectable.

## Encoding

12-byte header: `IdN`, byte 3 = `0x4A + recordSize`, bytes 4..6 = `001`,
byte 7 = zero, bytes 8..11 = little-endian entry count.
Each sorted record has a 24-bit little-endian DMR ID followed by 6-bit text.
Character table: space, digits 0..9, A..Z, a..z, period. Four characters
occupy three bytes, high bits first. Supported lengths 16,20,24,32,40,48
include callsign, separators and selected details. Empty padding is space/code 0.
Diacritics are stripped; Russian Cyrillic is transliterated before packing.
IDs 0 and 0xFFFFFF are rejected. Duplicate IDs retain the first CSV row.

## Memory and failure handling

Physical region 0: `0x50000..0x8FFFF` (0x40000 bytes, including header).
Physical region 1: `0xD8000..0xDFFFFF` (0xD28000 bytes).
The first region ends at a complete record; remaining records begin at region 1.
Unused bytes in touched sectors, all unused sectors, codeplug, satellites and
voice prompt areas remain unchanged. Lowercase `Idn` (voice-memory reuse) is
never generated. Maximum at 16 text characters is 937163 records.

Only radioType 5 and Radio Info 3/4 are accepted. A reported real flash ID
must have low16 0x4018; the RUS v4 `0xDEFECA7E` sentinel (or zero) means that
firmware does not expose flash ID, so writes remain confined to the two
documented callsign regions. Existing
satellite/codeplug code retains its original hardware handling. USB Flash uses
physical addresses and the existing X1/X2/X3 sector protocol.

Before any mutation, save and fsync a ZIP of every touched before/after sector.
Re-read every sector and reject a stale plan before the first write. During a
multi-sector replacement, zero the 12-byte header and verify it, write and
verify non-header sectors, then write and verify the final header sector.
Stop on the first error, without automatic retries or rollback. A failed
replacement can be repeated explicitly after restarting the radio. All dated
backups remain available for export, including the original pre-failure state.
An explicit clear writes a valid zero-entry header, preserving all other bytes.
This is logical removal, not secure erasure of old records.

## Sources used to check format and address split

- Windows CPS compressor/header and split logic:
  https://github.com/open-ham/OpenGD77CPS/blob/main/Extras/DMRID/DMRDataItem.cs
  https://github.com/open-ham/OpenGD77CPS/blob/main/Extras/DMRID/DMRIDForm.cs
- MD-9600 firmware database lookup, packing table, Radio Info and flash ID:
  https://github.com/Telectroboy/opengd77/blob/main/OPENGD77_MD9600_20260131/MD9600_firmware/application/source/user_interface/uiUtilities.c
  https://github.com/Telectroboy/opengd77/blob/main/OPENGD77_MD9600_20260131/MD9600_firmware/application/source/usb/usb_com.c
  https://github.com/Telectroboy/opengd77/blob/main/OPENGD77_MD9600_20260131/MD9600_firmware/application/source/hardware/SPI_Flash.c
- QDMR OpenUV380-family physical mapping and safe region sizes:
  https://github.com/hmatuschek/qdmr/blob/master/lib/openuv380_callsigndb.hh
- Download source (HTTP 200 and CSV header checked on 2026-09-30):
  https://database.radioid.net/static/user.csv

The default text length is 48; the preview shows all selected fields and marks
any text truncated by the chosen on-radio entry length.

## Reading and local editing

The Android reader first checks the `IdN001` header and count, then reads only
the record-aligned ranges in the two callsign regions. Bulk requests are 1024
bytes, below the firmware CPS response limit. It checks that the header did not
change during the read, that the returned ranges are complete, and that decoded
IDs are valid and strictly increasing. Unknown formats and incomplete images
are rejected without writing to flash.

The editor adds, changes, or removes entries in the in-memory prepared database.
IDs must be unique and in the supported 24-bit range. Text is normalized to the
radio character table and clipped to the selected entry length. Changes reach
the radio only after the separate full-database write action; the existing
backup, preflight, sector verification, and header-last write sequence remains
in force. Unit coverage exercises split-region reads, binary round trips,
invalid headers/counts, and local add/edit/delete behavior.

The module has unit coverage for encoding, record-aligned split, CSV quoting,
filtering, sorting, duplicate/invalid rows, all length choices, exact preview,
hardware/region gates, backup failure, stale preflight, no-op, interrupted write,
header-last publication, read-back failure and preservation of unrelated bytes.
The user confirmed on a physical MD-9600 that database download, radio read,
write, and clear all work correctly in v0.8.2.
