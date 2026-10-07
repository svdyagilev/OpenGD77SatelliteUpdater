# Master power and squelch

The radio settings tree contains **Общая мощность (Master)** and **Общий шумоподавитель**. Both link to a read-only USB view.

The provided OpenGD77 RUS Windows CPS defines a 64-byte `RadioSettings` transfer via `R 0B`, gated by radio feature bit 8. Known schema version `0xDEFECE7E`: byte 20 is the 0-based power index; bytes 45/46/47 are VHF/UHF/220 MHz squelch. Squelch 1…21 represents 0…100% in steps of 5. Unknown schema versions and invalid values are rejected. The operation does not replace the current codeplug project.

The supplied CPS write-button handler and worker do not implement a working settings transfer. Android therefore has no enabled master-settings write control. Master settings are not the codeplug's per-channel power/squelch, nor the 40-byte general/VOX block. Change Master on the radio until a write protocol for the user's firmware has been verified. No guessed storage offsets or commands are used.

The read implementation still requires a device test.
