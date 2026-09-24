package ru.opengd77.satupdate;

final class TleEntry {
    final String name;
    final int catalogNumber;
    final String line1;
    final String line2;

    TleEntry(String name, int catalogNumber, String line1, String line2) {
        this.name = name;
        this.catalogNumber = catalogNumber;
        this.line1 = line1;
        this.line2 = line2;
    }

    String epochText() {
        String year = TleParser.field(line1, 18, 2).trim();
        String day = TleParser.field(line1, 20, 12).trim();
        return year + "/" + day;
    }
}
