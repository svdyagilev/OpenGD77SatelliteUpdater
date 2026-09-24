# OpenGD77 Satellite Updater for Android

Первый рабочий исходный прототип Android-приложения для обновления Keps/TLE в TYT MD-9600 с OpenGD77/OpenGD77RUS через USB OTG.

## Совместимость

- minSdk 23 = Android 6.0
- Java 8, обычные Android Views (без Compose)
- USB Host API / UsbManager, без стороннего USB-драйвера
- VID:PID OpenGD77 `1FC9:0094`
- 115200 8N1, CDC ACM

## Источники TLE

В UI предустановлены:

- `https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=tle`
- `https://r4uab.ru/satonline.txt`

Также есть импорт локального текстового TLE-файла — это полезный резервный путь для очень старых Android, если системное хранилище сертификатов не принимает современный HTTPS-сертификат сайта.

## Что уже реализовано

1. Парсер пользовательского `Satellites.txt`.
2. Парсер стандартных 3-line TLE наборов, сопоставление по NORAD Catalog Number.
3. Точный OpenGD77 encoder 40-байтной орбитальной части.
4. 100-байтная запись спутника с частотами, CTCSS + ArmCTCSS и APRS path.
5. Воспроизведение float32-округления CPS для частот.
6. Поиск TLV ID 3 в Additional Settings.
7. Безопасный Read-Modify-Write двух 4KB FLASH-секторов от `0x20000`.
8. OpenGD77 R/X/C протокол и проверка radioType=5 (MD-9600).
9. USB CDC через штатный Android UsbManager.
10. Unit test ISS, сравнивающий 100 байт с реальной записью CPS 2025.2.18.3 из кодплага 24.09.2026.

## Важная оговорка

Проект сформирован и алгоритм/fixtures проверены по реальным файлам, но APK в текущем окружении не собирался и USB-часть ещё не проверена физически на MD-9600 с Android. Первую проверку на телефоне рекомендуется начать с кнопки **«Подключить MD-9600 и прочитать»**, не нажимая запись, и сверить лог.

## Формат спутника

См. `docs/PROTOCOL.md`.
