package ru.opengd77.satupdate;

/** Raw read-only codeplug regions used by the v0.5 decoder. */
final class CodeplugSnapshot {
    final byte[] generalSettings;
    final byte[] scanLists;
    final byte[] channelBank0;
    final byte[] zones;
    final byte[] channelBanks1to7;
    final byte[] contacts;
    final byte[] rxGroups;

    CodeplugSnapshot(byte[] generalSettings,
                     byte[] scanLists,
                     byte[] channelBank0,
                     byte[] zones,
                     byte[] channelBanks1to7,
                     byte[] contacts,
                     byte[] rxGroups) {
        this.generalSettings = generalSettings;
        this.scanLists = scanLists;
        this.channelBank0 = channelBank0;
        this.zones = zones;
        this.channelBanks1to7 = channelBanks1to7;
        this.contacts = contacts;
        this.rxGroups = rxGroups;
    }

    int totalBytes() {
        return generalSettings.length + scanLists.length + channelBank0.length + zones.length
                + channelBanks1to7.length + contacts.length + rxGroups.length;
    }
}
