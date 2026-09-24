package ru.opengd77.satupdate;

final class SatelliteConfig {
    final int catalogNumber;
    final String name;
    final String rx1, tx1, ctcss, armCtcss, rx2, tx2, rx3, tx3, aprsConfig;

    SatelliteConfig(int catalogNumber, String name, String rx1, String tx1,
                    String ctcss, String armCtcss, String rx2, String tx2,
                    String rx3, String tx3, String aprsConfig) {
        this.catalogNumber = catalogNumber;
        this.name = name;
        this.rx1 = rx1;
        this.tx1 = tx1;
        this.ctcss = ctcss;
        this.armCtcss = armCtcss;
        this.rx2 = rx2;
        this.tx2 = tx2;
        this.rx3 = rx3;
        this.tx3 = tx3;
        this.aprsConfig = aprsConfig;
    }
}
