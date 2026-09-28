package ru.opengd77.satupdate;

/** Raw read-only codeplug regions used by the v0.5 decoder. */
final class CodeplugSnapshot {
    final byte[] deviceInfo;
    final byte[] generalSettings;
    final byte[] dtmfSettings;
    final byte[] aprsConfigs;
    final byte[] scanLists;
    final byte[] dtmfContacts;
    final byte[] channelBank0;
    final byte[] bootAndVfos;
    final byte[] zones;
    final byte[] channelBanks1to7;
    final byte[] contacts;
    final byte[] rxGroups;
    final byte[] additionalSettings;

    CodeplugSnapshot(byte[] deviceInfo,
                     byte[] generalSettings,
                     byte[] dtmfSettings,
                     byte[] aprsConfigs,
                     byte[] scanLists,
                     byte[] dtmfContacts,
                     byte[] channelBank0,
                     byte[] bootAndVfos,
                     byte[] zones,
                     byte[] channelBanks1to7,
                     byte[] contacts,
                     byte[] rxGroups,
                     byte[] additionalSettings) {
        this.deviceInfo = deviceInfo;
        this.generalSettings = generalSettings;
        this.dtmfSettings = dtmfSettings;
        this.aprsConfigs = aprsConfigs;
        this.scanLists = scanLists;
        this.dtmfContacts = dtmfContacts;
        this.channelBank0 = channelBank0;
        this.bootAndVfos = bootAndVfos;
        this.zones = zones;
        this.channelBanks1to7 = channelBanks1to7;
        this.contacts = contacts;
        this.rxGroups = rxGroups;
        this.additionalSettings = additionalSettings;
    }

    /** Compatibility constructor used by older synthetic unit tests. */
    CodeplugSnapshot(byte[] generalSettings,
                     byte[] scanLists,
                     byte[] channelBank0,
                     byte[] zones,
                     byte[] channelBanks1to7,
                     byte[] contacts,
                     byte[] rxGroups) {
        this(new byte[0], generalSettings, new byte[0], new byte[0], scanLists,
                new byte[0], channelBank0, new byte[0], zones, channelBanks1to7,
                contacts, rxGroups, new byte[0]);
    }

    int totalBytes() {
        return deviceInfo.length + generalSettings.length + dtmfSettings.length + aprsConfigs.length
                + scanLists.length + dtmfContacts.length + channelBank0.length + bootAndVfos.length
                + zones.length + channelBanks1to7.length + contacts.length + rxGroups.length
                + additionalSettings.length;
    }
}
