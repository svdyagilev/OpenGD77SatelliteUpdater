# Windows CPS exchange in v0.8.11

The editor imports and exports OpenGD77 RUS `.ogd`: exactly 131072 bytes and the `RUSSIAN FF` header. `.g77`, firmware BIN, ZIP and `.ogcproj` are different formats.

Use **Действия → Проект → Импорт Windows CPS (.ogd)** from an open radio project. Import replaces supported records, retaining physical slot numbers, displays a preview, and can be undone. It never writes the radio automatically. The radio identity, hardware band limits, reserved bits of existing records, and unknown additional-data blocks stay with the receiving project. New records are rebuilt using supported fields, rather than copying arbitrary reserved bytes from another radio. Lost references and unsupported values stop the import atomically.

The packed file has shared low-memory offsets through 0x8010, 68 zones (80 members each), channel banks 129–1024 at 0xB1B0, DMR contacts at 0x17620, receive groups at 0x1D620 and 4512 bytes of TLV data at 0x1EE60. The MD-9600 uses different FLASH addresses; the packed file is never sent directly to physical memory. Legacy 16-member zones are converted to 80-member records using the Windows CPS upgrade rule.

Boot image (TLV 1, 1024 bytes), melody (TLV 2, 512 bytes) and satellites (TLV 3, 2520 bytes) are imported. Unknown TLV IDs cannot overwrite the receiving radio's data. The write section is labeled **Заставка / мелодия / спутники** to make these changes visible before writing.

Export refuses occupied zone slots #69–250 or additional data outside the file's 4512-byte capacity. It does not silently truncate them. File bytes outside the mapped regions are retained from the imported OGD template. A new export without a template initializes unused bytes to FF. Native project format 2 stores the template with the existing SHA-256 checksum; format 1 projects remain readable. Older Android CPS versions cannot open format 2.

Hardware round-trip through Windows CPS and the radio remains to be tested by the user.
