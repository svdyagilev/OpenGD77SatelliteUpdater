package ru.opengd77.satupdate;
/** Read-only, versioned 64-byte RUS CPS settings transfer (R/0x0B), not codeplug general bytes. */
final class RadioMasterSettings {
    static final long VERSION=0xdefece7eL;
    private static final String[] POWER={"100 мВт","250 мВт","500 мВт","750 мВт","1 Вт","5 Вт","10 Вт","25 Вт","40 Вт","+W−"};
    final int power,vhf,uhf,band220;
    RadioMasterSettings(byte[] bytes){
        if(bytes.length!=64||ByteUtil.u32le(bytes,0)!=VERSION)throw new IllegalArgumentException("Версия блока общих настроек не поддерживается. Настройки рации не изменены.");
        power=bytes[20]&255;vhf=bytes[45]&255;uhf=bytes[46]&255;band220=bytes[47]&255;
        if(power>=POWER.length||vhf<1||vhf>21||uhf<1||uhf>21||band220<1||band220>21)throw new IllegalArgumentException("Неизвестные значения общих настроек. Настройки рации не изменены.");
    }
    static String sql(int value){return value==1?"0% (открыт)":value==21?"100% (закрыт)":((value-1)*5)+"%";}
    String text(){return "Общая мощность (Master): "+POWER[power]+"\n\nОбщий шумоподавитель VHF: "+sql(vhf)+"\nОбщий шумоподавитель UHF: "+sql(uhf)+"\nОбщий шумоподавитель 220 МГц: "+sql(band220)+"\n\nЭти значения используются каналами с настройкой «От Master / Общая настройка».\n\nПросмотр доступен через RUS CPS. Запись этих параметров через Android CPS пока не поддерживается. Изменить Master можно на рации; индивидуальные значения — в редакторе каналов.";}
}
