package ru.opengd77.satupdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CodeplugModel {
    static final class DeviceInfo {
        final int minUhf;
        final int maxUhf;
        final int minVhf;
        final int maxVhf;
        final String model;
        final String serial;
        final String cpsVersion;
        final String hardwareVersion;
        final String firmwareVersion;
        final String dspVersion;

        DeviceInfo(int minUhf, int maxUhf, int minVhf, int maxVhf,
                   String model, String serial, String cpsVersion,
                   String hardwareVersion, String firmwareVersion, String dspVersion) {
            this.minUhf = minUhf;
            this.maxUhf = maxUhf;
            this.minVhf = minVhf;
            this.maxVhf = maxVhf;
            this.model = model;
            this.serial = serial;
            this.cpsVersion = cpsVersion;
            this.hardwareVersion = hardwareVersion;
            this.firmwareVersion = firmwareVersion;
            this.dspVersion = dspVersion;
        }
    }

    static final class General {
        final String radioName;
        final long dmrId;
        final int codeplugVersion;
        final int voxSense;
        final int flag1;
        final int flag2;
        final int flag3;
        final int flag4;

        General(String radioName, long dmrId, int codeplugVersion, int voxSense,
                int flag1, int flag2, int flag3, int flag4) {
            this.radioName = radioName;
            this.dmrId = dmrId;
            this.codeplugVersion = codeplugVersion;
            this.voxSense = voxSense;
            this.flag1 = flag1;
            this.flag2 = flag2;
            this.flag3 = flag3;
            this.flag4 = flag4;
        }
    }

    static final class BootInfo {
        final int introMode;
        final boolean passwordEnabled;
        final String line1;
        final String line2;
        final List<Integer> quickKeys;

        BootInfo(int introMode, boolean passwordEnabled, String line1, String line2,
                 List<Integer> quickKeys) {
            this.introMode = introMode;
            this.passwordEnabled = passwordEnabled;
            this.line1 = line1;
            this.line2 = line2;
            this.quickKeys = Collections.unmodifiableList(new ArrayList<>(quickKeys));
        }
    }

    static final class Tone {
        enum Type { NONE, CTCSS, DCS_NORMAL, DCS_INVERTED, UNKNOWN }

        final Type type;
        final int value;
        final int raw;

        Tone(Type type, int value, int raw) {
            this.type = type;
            this.value = value;
            this.raw = raw;
        }

        String displayText() {
            switch (type) {
                case NONE:
                    return "нет";
                case CTCSS:
                    return String.format(Locale.US, "CTCSS %.1f Hz", value / 10.0);
                case DCS_NORMAL:
                    return String.format(Locale.US, "DCS %03X N", value);
                case DCS_INVERTED:
                    return String.format(Locale.US, "DCS %03X I", value);
                default:
                    return String.format(Locale.US, "RAW 0x%04X", raw & 0xffff);
            }
        }
    }

    static final class Channel {
        private static final String[] MD9600_POWER_LEVELS = {
                "от Master", "100 mW", "250 mW", "500 mW", "750 mW",
                "1 W", "5 W", "10 W", "25 W", "40 W", "+W-"
        };
        private static final String[] STEP_TEXT = {
                "2.5 kHz", "5 kHz", "6.25 kHz", "10 kHz",
                "12.5 kHz", "25 kHz", "30 kHz", "50 kHz"
        };
        private static final String[] TA_TEXT = {"Off", "APRS", "Text", "APRS+Text"};

        final int index;
        final String name;
        final long rxHz;
        final long txHz;
        final boolean digital;
        final int colorCode;
        final int timeSlot;
        final int contactIndex;
        final int rxGroupIndex;
        final boolean rxOnly;
        final boolean wide25k;
        final Tone rxTone;
        final Tone txTone;
        final int powerSetting;
        final boolean beepEnabled;
        final boolean ecoEnabled;
        final int totSeconds;
        final boolean useLocation;
        final double latitude;
        final double longitude;
        final long optionalDmrId;
        final boolean forceDmo;
        final boolean roaming;
        final boolean fastCall;
        final boolean priority;
        final int aprsConfigIndex;
        final boolean vox;
        final boolean zoneSkip;
        final boolean allSkip;
        final boolean allowTalkaround;
        final boolean squelchOverride;
        final int squelchLevel;
        final int stepIndex;
        final int taTxTs1;
        final int taTxTs2;
        final int ste;
        final int nonSte;
        final boolean dataPl;
        final int pttidType;
        final boolean dualCapacity;
        final int timingPreference;
        final int ars;
        final int keySwitch;
        final boolean udpDataHead;
        final boolean allowTxInterrupt;
        final boolean txInterruptFreq;
        final boolean privateCall;
        final int rawOpenGd77Rus;
        final int rawLibreFlags;
        final int rawFlag1;
        final int rawFlag2;
        final int rawFlag3;
        final int rawFlag4;

        Channel(int index, String name, long rxHz, long txHz, boolean digital,
                int colorCode, int timeSlot, int contactIndex, int rxGroupIndex,
                boolean rxOnly, boolean wide25k, Tone rxTone, Tone txTone,
                int powerSetting, boolean beepEnabled, boolean ecoEnabled,
                int totSeconds, boolean useLocation, double latitude, double longitude,
                long optionalDmrId, boolean forceDmo, boolean roaming,
                boolean fastCall, boolean priority, int aprsConfigIndex,
                boolean vox, boolean zoneSkip, boolean allSkip, boolean allowTalkaround,
                boolean squelchOverride, int squelchLevel, int stepIndex,
                int taTxTs1, int taTxTs2, int ste, int nonSte, boolean dataPl,
                int pttidType, boolean dualCapacity, int timingPreference, int ars,
                int keySwitch, boolean udpDataHead, boolean allowTxInterrupt,
                boolean txInterruptFreq, boolean privateCall,
                int rawOpenGd77Rus, int rawLibreFlags, int rawFlag1, int rawFlag2,
                int rawFlag3, int rawFlag4) {
            this.index = index;
            this.name = name;
            this.rxHz = rxHz;
            this.txHz = txHz;
            this.digital = digital;
            this.colorCode = colorCode;
            this.timeSlot = timeSlot;
            this.contactIndex = contactIndex;
            this.rxGroupIndex = rxGroupIndex;
            this.rxOnly = rxOnly;
            this.wide25k = wide25k;
            this.rxTone = rxTone;
            this.txTone = txTone;
            this.powerSetting = powerSetting;
            this.beepEnabled = beepEnabled;
            this.ecoEnabled = ecoEnabled;
            this.totSeconds = totSeconds;
            this.useLocation = useLocation;
            this.latitude = latitude;
            this.longitude = longitude;
            this.optionalDmrId = optionalDmrId;
            this.forceDmo = forceDmo;
            this.roaming = roaming;
            this.fastCall = fastCall;
            this.priority = priority;
            this.aprsConfigIndex = aprsConfigIndex;
            this.vox = vox;
            this.zoneSkip = zoneSkip;
            this.allSkip = allSkip;
            this.allowTalkaround = allowTalkaround;
            this.squelchOverride = squelchOverride;
            this.squelchLevel = squelchLevel;
            this.stepIndex = stepIndex;
            this.taTxTs1 = taTxTs1;
            this.taTxTs2 = taTxTs2;
            this.ste = ste;
            this.nonSte = nonSte;
            this.dataPl = dataPl;
            this.pttidType = pttidType;
            this.dualCapacity = dualCapacity;
            this.timingPreference = timingPreference;
            this.ars = ars;
            this.keySwitch = keySwitch;
            this.udpDataHead = udpDataHead;
            this.allowTxInterrupt = allowTxInterrupt;
            this.txInterruptFreq = txInterruptFreq;
            this.privateCall = privateCall;
            this.rawOpenGd77Rus = rawOpenGd77Rus;
            this.rawLibreFlags = rawLibreFlags;
            this.rawFlag1 = rawFlag1;
            this.rawFlag2 = rawFlag2;
            this.rawFlag3 = rawFlag3;
            this.rawFlag4 = rawFlag4;
        }

        String powerText() {
            if (powerSetting >= 0 && powerSetting < MD9600_POWER_LEVELS.length) {
                return MD9600_POWER_LEVELS[powerSetting];
            }
            return "RAW " + powerSetting;
        }

        String stepText() {
            return stepIndex >= 0 && stepIndex < STEP_TEXT.length ? STEP_TEXT[stepIndex]
                    : "RAW " + stepIndex;
        }

        String taText(int value) {
            return value >= 0 && value < TA_TEXT.length ? TA_TEXT[value] : "RAW " + value;
        }

        String squelchText() {
            if (!squelchOverride || squelchLevel == 0) return "Master/Default";
            if (squelchLevel == 1) return "Open";
            if (squelchLevel >= 2 && squelchLevel <= 20) return ((squelchLevel - 1) * 5) + "%";
            if (squelchLevel == 21) return "Closed";
            return "RAW " + squelchLevel;
        }

        String oneLine() {
            String prefix = index > 0 ? index + ". " : "";
            String base = String.format(Locale.US, "%s%s  %.5f / %.5f MHz",
                    prefix, name, rxHz / 1_000_000.0, txHz / 1_000_000.0);
            if (digital) {
                return base + "  DMR CC" + colorCode + " TS" + timeSlot
                        + (contactIndex > 0 ? " C#" + contactIndex : "")
                        + (rxGroupIndex > 0 ? " RXG#" + rxGroupIndex : "")
                        + "  P:" + powerText();
            }
            String tones = "";
            if (rxTone.type != Tone.Type.NONE || txTone.type != Tone.Type.NONE) {
                tones = "  RX:" + rxTone.displayText() + " TX:" + txTone.displayText();
            }
            return base + "  FM " + (wide25k ? "25k" : "12.5k")
                    + (rxOnly ? " RX-only" : "") + tones + "  P:" + powerText();
        }
    }

    static final class Contact {
        final int index;
        final String name;
        final long number;
        final int type;
        final int tsOverride;

        Contact(int index, String name, long number, int type, int tsOverride) {
            this.index = index;
            this.name = name;
            this.number = number;
            this.type = type;
            this.tsOverride = tsOverride;
        }

        String typeText() {
            if (type == 0) return "TG";
            if (type == 1) return "PC";
            if (type == 2) return "ALL";
            return "TYPE" + type;
        }

        String oneLine() {
            String ts = tsOverride == 0x00 ? " TS1" : tsOverride == 0x02 ? " TS2" : "";
            return index + ". " + name + "  " + typeText() + " " + number + ts;
        }
    }

    static final class DtmfContact {
        final int index;
        final String name;
        final String code;

        DtmfContact(int index, String name, String code) {
            this.index = index;
            this.name = name;
            this.code = code;
        }

        String oneLine() { return index + ". " + name + "  " + code; }
    }

    static final class DtmfSettings {
        final String selfId;
        final String killCode;
        final String wakeCode;
        final int delimiter;
        final int groupCode;
        final int decodeResponse;
        final int autoResetSeconds;
        final boolean killWakeDecode;
        final int killType;
        final String pttUp;
        final String pttDown;
        final double responseHoldSeconds;
        final double decodeTimeSeconds;
        final int firstDigitDelayMs;
        final int firstDigitDurationMs;
        final int otherDurationMs;
        final int rate;
        final int tailMs;

        DtmfSettings(String selfId, String killCode, String wakeCode, int delimiter,
                     int groupCode, int decodeResponse, int autoResetSeconds,
                     boolean killWakeDecode, int killType, String pttUp, String pttDown,
                     double responseHoldSeconds, double decodeTimeSeconds,
                     int firstDigitDelayMs, int firstDigitDurationMs, int otherDurationMs,
                     int rate, int tailMs) {
            this.selfId = selfId;
            this.killCode = killCode;
            this.wakeCode = wakeCode;
            this.delimiter = delimiter;
            this.groupCode = groupCode;
            this.decodeResponse = decodeResponse;
            this.autoResetSeconds = autoResetSeconds;
            this.killWakeDecode = killWakeDecode;
            this.killType = killType;
            this.pttUp = pttUp;
            this.pttDown = pttDown;
            this.responseHoldSeconds = responseHoldSeconds;
            this.decodeTimeSeconds = decodeTimeSeconds;
            this.firstDigitDelayMs = firstDigitDelayMs;
            this.firstDigitDurationMs = firstDigitDurationMs;
            this.otherDurationMs = otherDurationMs;
            this.rate = rate;
            this.tailMs = tailMs;
        }
    }

    static final class AprsConfig {
        final int index;
        final String name;
        final int senderSsid;
        final double latitude;
        final double longitude;
        final String via1;
        final int via1Ssid;
        final String via2;
        final int via2Ssid;
        final int iconTable;
        final int iconIndex;
        final String comment;
        final long txHz;
        final int flags;
        final int magic;

        AprsConfig(int index, String name, int senderSsid, double latitude, double longitude,
                   String via1, int via1Ssid, String via2, int via2Ssid,
                   int iconTable, int iconIndex, String comment, long txHz,
                   int flags, int magic) {
            this.index = index;
            this.name = name;
            this.senderSsid = senderSsid;
            this.latitude = latitude;
            this.longitude = longitude;
            this.via1 = via1;
            this.via1Ssid = via1Ssid;
            this.via2 = via2;
            this.via2Ssid = via2Ssid;
            this.iconTable = iconTable;
            this.iconIndex = iconIndex;
            this.comment = comment;
            this.txHz = txHz;
            this.flags = flags;
            this.magic = magic;
        }

        String oneLine() {
            return index + ". " + name + String.format(Locale.US, "  %.5f MHz", txHz / 1_000_000.0);
        }
    }

    static final class Satellite {
        final String name;
        final double ageDays;
        Satellite(String name, double ageDays) { this.name = name; this.ageDays = ageDays; }
        String oneLine() {
            return Double.isNaN(ageDays) ? name + "  epoch ?"
                    : String.format(Locale.US, "%s  %.1f d", name, ageDays);
        }
    }

    static final class Zone {
        final int index;
        final String name;
        final List<Integer> channelIndices;

        Zone(int index, String name, List<Integer> channelIndices) {
            this.index = index;
            this.name = name;
            this.channelIndices = Collections.unmodifiableList(new ArrayList<>(channelIndices));
        }

        String oneLine() {
            return index + ". " + name + "  • " + channelIndices.size() + " каналов";
        }
    }

    static final class RxGroup {
        final int index;
        final String name;
        final List<Integer> contactIndices;

        RxGroup(int index, String name, List<Integer> contactIndices) {
            this.index = index;
            this.name = name;
            this.contactIndices = Collections.unmodifiableList(new ArrayList<>(contactIndices));
        }

        String oneLine() {
            return index + ". " + name + "  • " + contactIndices.size() + " контактов";
        }
    }

    static final class ScanList {
        final int index;
        final String name;
        final List<Integer> channelIndices;
        final int primary;
        final int secondary;
        final int revert;

        ScanList(int index, String name, List<Integer> channelIndices,
                 int primary, int secondary, int revert) {
            this.index = index;
            this.name = name;
            this.channelIndices = Collections.unmodifiableList(new ArrayList<>(channelIndices));
            this.primary = primary;
            this.secondary = secondary;
            this.revert = revert;
        }

        String oneLine() {
            return index + ". " + name + "  • " + channelIndices.size() + " каналов";
        }
    }

    final DeviceInfo deviceInfo;
    final General general;
    final BootInfo boot;
    final DtmfSettings dtmfSettings;
    final List<Channel> vfos;
    final List<Channel> channels;
    final List<Contact> contacts;
    final List<DtmfContact> dtmfContacts;
    final List<AprsConfig> aprsConfigs;
    final List<Satellite> satellites;
    final List<Zone> zones;
    final List<RxGroup> rxGroups;
    final List<ScanList> scanLists;
    final int rawBytes;

    CodeplugModel(DeviceInfo deviceInfo, General general, BootInfo boot, DtmfSettings dtmfSettings,
                  List<Channel> vfos, List<Channel> channels, List<Contact> contacts,
                  List<DtmfContact> dtmfContacts, List<AprsConfig> aprsConfigs,
                  List<Satellite> satellites, List<Zone> zones,
                  List<RxGroup> rxGroups, List<ScanList> scanLists, int rawBytes) {
        this.deviceInfo = deviceInfo;
        this.general = general;
        this.boot = boot;
        this.dtmfSettings = dtmfSettings;
        this.vfos = Collections.unmodifiableList(vfos);
        this.channels = Collections.unmodifiableList(channels);
        this.contacts = Collections.unmodifiableList(contacts);
        this.dtmfContacts = Collections.unmodifiableList(dtmfContacts);
        this.aprsConfigs = Collections.unmodifiableList(aprsConfigs);
        this.satellites = Collections.unmodifiableList(satellites);
        this.zones = Collections.unmodifiableList(zones);
        this.rxGroups = Collections.unmodifiableList(rxGroups);
        this.scanLists = Collections.unmodifiableList(scanLists);
        this.rawBytes = rawBytes;
    }

    String compactSummary() {
        return "Channels " + channels.size()
                + " • Zones " + zones.size()
                + " • Contacts " + contacts.size()
                + " • RX Groups " + rxGroups.size()
                + " • APRS " + aprsConfigs.size()
                + " • DTMF " + dtmfContacts.size();
    }
}
