package ru.opengd77.satupdate;

import java.util.Locale;

/** Tables from OpenGD77 firmware/source/functions/trx.c (TRX_CTCSSTones/TRX_DCSCodes). */
final class ToneChoices {
    private static final int[] CTCSS = {670,693,719,744,770,797,825,854,885,915,948,974,1000,1035,1072,1109,1148,1188,1230,1273,1318,1365,1413,1462,1514,1567,1598,1622,1655,1679,1713,1738,1773,1799,1835,1862,1899,1928,1966,1995,2035,2065,2107,2181,2257,2291,2336,2418,2503,2541};
    private static final String[] DCS = {"023","025","026","031","032","043","047","051","054","065","071","072","073","074","114","115","116","125","131","132","134","143","152","155","156","162","165","172","174","205","223","226","243","244","245","251","261","263","265","271","306","311","315","331","343","345","351","364","365","371","411","412","413","423","431","432","445","464","465","466","503","506","516","532","546","565","606","612","624","627","631","632","654","662","664","703","712","723","731","732","734","743","754"};
    static String[] values(int kind){
        if(kind==0)return new String[]{"нет"};
        if(kind<1||kind>3)throw new IllegalArgumentException("Тип субтона");
        String[] out=new String[kind==1?CTCSS.length:DCS.length];
        for(int i=0;i<out.length;i++)out[i]=kind==1?String.format(Locale.US,"CTCSS %.1f Гц",CTCSS[i]/10.0):"DCS "+DCS[i]+(kind==2?" N":" I");
        return out;
    }
}
