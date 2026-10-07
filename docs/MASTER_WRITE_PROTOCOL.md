# Master settings write investigation (2026-10-08)

User hardware results: v0.8.13 preset insertion and core functionality work; Master reading succeeds. MD-9600, FW RUSSIAN, info v4, RadioSettings magic 0xDEFECA7E. Example values: power index 5 (5 W); VHF/UHF/220 squelch indexes 6/5/3 (25/20/10%).

## Scope and result

The supplied OpenMD9600_V5HW_RUS(2).bin contains an X/0C handler, but it reads from a different buffer than the USB receive path. No Master write command is enabled in Android CPS. The finding is specific to the supplied binary; the installed device binary was not dumped or compared, and no hardware write was attempted.

Firmware SHA-256: d9a6bece2e01ded8ee0a8678fda345de7e7aec7ad89041c2015fb2fa5cfc7bae.

## Binary evidence

| Instruction | Behavior |
|---|---|
| 0x08009E8A | USB receive destination is 0x20013428 |
| R/0B dispatch -> 0x0801AA38 | Packs native settings into a 64-byte read response |
| 0x0801AB02 | Response payload starts at 0x20012C2B |
| 0x0801A8EE | Response header starts at 0x20012C28 |
| X/0C dispatch -> 0x0801AE86 | Loads source 0x20012C30, five bytes into the preceding read payload |
| 0x0801AF70 | Calls settingsSaveSettings after copying fields into native settings |

The ordinary X/02 sector staging buffer is 0x10002918, distinct from the X/0C source. Staging normal codeplug sector data cannot fix this mismatch. X/03 sector commit must not be used as a guessed Master write path.

## Verification

The actual Thumb instructions at 0x0801AE86 through 0x0801AF70 were executed with Unicorn in mapped firmware/RAM/CCM memory. A valid new X/0C request was placed in the USB receive buffer, while the response buffer held the preceding R/0B response. The settings magic written into native memory became 0 rather than 0xDEFECA7E. Execution stopped before the persistence call. This demonstrates the wrong-buffer read in the provided file; it does not establish the behavior of a different installed firmware build or alternate undocumented transfer sequence.

## Required before enabling writes

Obtain a corrected firmware implementation/source or an independently verified Windows CPS transfer trace for the exact firmware in use. A working implementation must preserve other settings, back up the original block, verify the write by reading, and confirm persistence after reboot. Read support and feature bit 8 alone do not prove a usable write path.
