package ru.opengd77.satupdate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OpenGd77ToneDecoderTest {
    @Test public void decodesNoTone() {
        CodeplugModel.Tone none = OpenGd77CodeplugDecoder.decodeTone(0xFFFF);
        assertEquals(CodeplugModel.Tone.Type.NONE, none.type);
        assertEquals("нет", none.displayText());
    }

    @Test public void decodesCtcss() {
        CodeplugModel.Tone t885 = OpenGd77CodeplugDecoder.decodeTone(0x0885);
        assertEquals(CodeplugModel.Tone.Type.CTCSS, t885.type);
        assertEquals(885, t885.value);
        assertEquals("CTCSS 88.5 Hz", t885.displayText());

        CodeplugModel.Tone t1230 = OpenGd77CodeplugDecoder.decodeTone(0x1230);
        assertEquals(1230, t1230.value);
        assertEquals("CTCSS 123.0 Hz", t1230.displayText());
    }

    @Test public void decodesDcsNormalAndInverted() {
        CodeplugModel.Tone normal = OpenGd77CodeplugDecoder.decodeTone(0x8023);
        assertEquals(CodeplugModel.Tone.Type.DCS_NORMAL, normal.type);
        assertEquals(0x023, normal.value);
        assertEquals("DCS 023 N", normal.displayText());

        CodeplugModel.Tone inverted = OpenGd77CodeplugDecoder.decodeTone(0xC754);
        assertEquals(CodeplugModel.Tone.Type.DCS_INVERTED, inverted.type);
        assertEquals(0x754, inverted.value);
        assertEquals("DCS 754 I", inverted.displayText());
    }
}
