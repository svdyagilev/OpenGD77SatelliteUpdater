package ru.opengd77.satupdate;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

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

    double ageDays(long nowMillis) {
        return (nowMillis - epochMillis()) / 86400000.0d;
    }

    long epochMillis() {
        int yy = Integer.parseInt(TleParser.field(line1, 18, 2).trim());
        double day = Double.parseDouble(TleParser.field(line1, 20, 12).trim());
        int year = (yy >= 57) ? (1900 + yy) : (2000 + yy);

        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.DAY_OF_YEAR, 1);
        long jan1 = c.getTimeInMillis();
        return jan1 + Math.round((day - 1.0d) * 86400000.0d);
    }
}
