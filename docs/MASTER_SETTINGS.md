# Master power and squelch

The radio settings tree contains **Общая мощность (Master)** and **Общий шумоподавитель**. Both link to a read-only USB view.

The provided OpenGD77 RUS Windows CPS defines a 64-byte `RadioSettings` transfer via `R 0B`, gated by radio feature bit 8. The read block begins with firmware `magicNumber`, not the XML/write `SettingsBlockVersion` (`0xDEFECE7E`). The latter must not validate reads. The Windows CPS requests R/0B with address and requested length zero, then receives 64 bytes. In the read layout, byte 20 is the 0-based power index; bytes 45/46/47 are VHF/UHF/220 MHz squelch. Squelch 1…21 represents 0…100% in steps of 5. The firmware feature, exact response length and valid power/squelch ranges are checked. The marker is displayed for diagnostics. The operation does not replace the current codeplug project.

The supplied CPS write-button handler and worker do not implement a working settings transfer. Android therefore has no enabled master-settings write control. Master settings are not the codeplug's per-channel power/squelch, nor the 40-byte general/VOX block. Change Master on the radio until a write protocol for the user's firmware has been verified. No guessed storage offsets or commands are used.

The user confirmed successful Master power and squelch reads on their MD-9600 RUS firmware with v0.8.12. Writing remains unsupported.
