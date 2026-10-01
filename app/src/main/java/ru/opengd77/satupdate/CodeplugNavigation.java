package ru.opengd77.satupdate;

/** The settings hierarchy, independent of screen history and shortcut entry points. */
final class CodeplugNavigation {
    static final int OVERVIEW=0, RADIO=100, CHANNELS=101, CONTACTS=102, SATELLITES=103;
    static final int RADIO_SETTINGS=15, VOX=16;
    private CodeplugNavigation() {}

    static int[] children(int page) {
        switch(page) {
            case OVERVIEW:return new int[]{RADIO,CHANNELS,CONTACTS,SATELLITES};
            case RADIO:return new int[]{10,13,RADIO_SETTINGS,11};
            case CHANNELS:return new int[]{1,3,2,6};
            case CONTACTS:return new int[]{4,8,5};
            case SATELLITES:return new int[]{7,12};
            case RADIO_SETTINGS:return new int[]{VOX,14,9};
            default:return new int[0];
        }
    }
    static int parent(int page) {
        if(page==OVERVIEW)return -1;
        for(int group:new int[]{OVERVIEW,RADIO,CHANNELS,CONTACTS,SATELLITES,RADIO_SETTINGS})
            for(int child:children(group))if(child==page)return group;
        return OVERVIEW;
    }
    static String title(int page) {
        switch(page) {
            case OVERVIEW:return "Обзор";
            case RADIO:return "Радиостанция";
            case CHANNELS:return "Каналы";
            case CONTACTS:return "Контакты";
            case SATELLITES:return "APRS и спутники";
            case 1:return "Список каналов";
            case 2:return "VFO A/B";
            case 3:return "Зоны";
            case 4:return "Контакты DMR";
            case 5:return "Группы приёма";
            case 6:return "Списки сканирования";
            case 7:return "Настройки APRS";
            case 8:return "Контакты DTMF";
            case 9:return "Настройки DTMF";
            case 10:return "Загрузочный экран";
            case 11:return "Сведения о станции";
            case 12:return "Спутники";
            case 13:return "DMR ID и позывной";
            case 14:return "Ограничения частот";
            case RADIO_SETTINGS:return "Настройки рации";
            case VOX:return "VOX и общие параметры";
            default:return "Обзор";
        }
    }
    static final class Link {
        final int page;
        Link(int page){this.page=page;}
    }
}
