package ru.opengd77.satupdate;

import java.io.IOException;

/**
 * Model-specific radio boundary. UI/codeplug code should not depend on raw USB commands.
 * More OpenGD77 radios can implement this interface later.
 */
interface RadioDriver {
    final class Identity {
        final String model;
        final long radioType;
        final long infoVersion;
        final String firmware;

        Identity(String model, long radioType, long infoVersion, String firmware) {
            this.model = model;
            this.radioType = radioType;
            this.infoVersion = infoVersion;
            this.firmware = firmware;
        }

        String compactText() {
            return model + " • FW " + firmware + " • info v" + infoVersion;
        }
    }

    interface Progress {
        void onMessage(String text);
    }

    Identity identify() throws IOException;
    byte[] readAdditionalSettings() throws IOException;
    void writeVerified(UpdatePlan plan, Progress progress) throws IOException;
    void reboot() throws IOException;
}
