package ru.opengd77.satupdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CodeplugModel {
    static final class General {
        final String radioName;
        final long dmrId;
        General(String radioName, long dmrId) {
            this.radioName = radioName;
            this.dmrId = dmrId;
        }
    }

    static final class Channel {
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

        Channel(int index, String name, long rxHz, long txHz, boolean digital,
                int colorCode, int timeSlot, int contactIndex, int rxGroupIndex,
                boolean rxOnly, boolean wide25k) {
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
        }

        String oneLine() {
            String base = String.format(Locale.US, "%d. %s  %.5f / %.5f MHz",
                    index, name, rxHz / 1_000_000.0, txHz / 1_000_000.0);
            if (digital) {
                return base + "  DMR CC" + colorCode + " TS" + timeSlot
                        + (contactIndex > 0 ? " C#" + contactIndex : "")
                        + (rxGroupIndex > 0 ? " RXG#" + rxGroupIndex : "");
            }
            return base + "  FM " + (wide25k ? "25k" : "12.5k") + (rxOnly ? " RX-only" : "");
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

    final General general;
    final List<Channel> channels;
    final List<Contact> contacts;
    final List<Zone> zones;
    final List<RxGroup> rxGroups;
    final List<ScanList> scanLists;
    final int rawBytes;

    CodeplugModel(General general,
                  List<Channel> channels,
                  List<Contact> contacts,
                  List<Zone> zones,
                  List<RxGroup> rxGroups,
                  List<ScanList> scanLists,
                  int rawBytes) {
        this.general = general;
        this.channels = Collections.unmodifiableList(channels);
        this.contacts = Collections.unmodifiableList(contacts);
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
                + " • Scan Lists " + scanLists.size();
    }
}
