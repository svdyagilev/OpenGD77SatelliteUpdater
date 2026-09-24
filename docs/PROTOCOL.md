# OpenGD77 MD-9600 satellite data / protocol notes

## SatelliteElement (100 bytes)

```
00..07  name, ASCII[8]
08..2F  orbital/TLE packed data, 40 bytes
30..33  RX1 Hz, uint32 LE
34..37  TX1 Hz, uint32 LE
38..39  CTCSS x10, uint16 LE
3A..3B  ArmCTCSS x10, uint16 LE
3C..3F  RX2 Hz, uint32 LE
40..43  TX2 Hz, uint32 LE
44..47  RX3 Hz, uint32 LE
48..4B  zero/reserved (Tx3 from Satellites.txt is not present in observed record)
4C..63  APRS Config/path, ASCII[24]
```

Observed satellite TLV payload: `0x09D8` = 2520 bytes = 25 * 100 + 20 zero bytes.

## Exact TLE packing

The 40 bytes are 80 nibbles. 79 characters are taken directly from fixed TLE columns, followed by one space/padding nibble.

Order:

- line 1 cols 19-20: epoch year (2)
- line 1 cols 21-32: epoch day (12)
- line 1 cols 34-43: first derivative of mean motion (10)
- line 2 cols 9-16: inclination (8)
- line 2 cols 18-25: RAAN (8)
- line 2 cols 27-33: eccentricity (7)
- line 2 cols 35-42: argument of perigee (8)
- line 2 cols 44-51: mean anomaly (8)
- line 2 cols 53-63: mean motion (11)
- line 2 cols 64-68: revolution number (5)
- padding space (1)

Nibble mapping:

```
'0'..'9' -> 0..9
'.'      -> A
' '      -> B
'-'      -> C
```

## Additional Settings

For MD-9600 / OpenUV380 family:

```
FLASH 0x00020000
magic   "OpenGD77" (8)
version uint32 LE = 1
TLVs start at +0x0C
TLV header: uint32 LE id, uint32 LE payloadLength
satellite TLV id = 3
```

Always read two complete 4K sectors (`0x20000..0x21FFF`), patch TLV 3 in RAM, and write only changed sectors.

## USB protocol

VID:PID `1FC9:0094`, serial 115200.

Read request (8 bytes):

```
'R' | command | address BE32 | length BE16
```

Flash command = 1; firmware info = 9. Read response:

```
'R' | length BE16 | payload
```

MD-9600 radioType in firmware info = 5.

Flash write (OpenUV380/MD-9600 uses `X`):

```
X 01 sector[24-bit BE]
X 02 address[BE32] length[BE16] data
X 03
```

Each X command is acknowledged by two bytes `X <command>`.
