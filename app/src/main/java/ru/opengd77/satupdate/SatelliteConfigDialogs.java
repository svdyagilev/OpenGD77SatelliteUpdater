package ru.opengd77.satupdate;
import android.app.*;
import android.widget.*;
import java.util.*;
final class SatelliteConfigDialogs {
    interface Save {void apply(List<SatelliteConfig> configs)throws Exception;}
    static void show(Activity activity,List<SatelliteConfig> configs,Save save){
        List<SatelliteConfig> draft=new ArrayList<>(configs);String[] labels=new String[draft.size()+1];labels[0]="+ Добавить спутник";for(int i=0;i<draft.size();i++)labels[i+1]=draft.get(i).name+" · NORAD "+draft.get(i).catalogNumber;
        new AlertDialog.Builder(activity).setTitle("Список для обновления Keps").setItems(labels,(d,index)->edit(activity,draft,index-1,save)).setNegativeButton("Закрыть",null).show();
    }
    private static void edit(Activity a,List<SatelliteConfig> configs,int index,Save save){
        SatelliteConfig c=index<0?new SatelliteConfig(1,"","0","0","0","0","0","0","0","0",""):configs.get(index);
        String[] values={""+c.catalogNumber,c.name,c.rx1,c.tx1,c.ctcss,c.armCtcss,c.rx2,c.tx2,c.rx3,c.tx3,c.aprsConfig};
        String[] labels={"NORAD","Имя (до 8 символов)","RX 1, МГц","TX 1, МГц","CTCSS, Гц (0 — нет)","Arm CTCSS, Гц","RX 2, МГц","TX 2, МГц","RX 3, МГц","TX 3, МГц","Путь APRS"};
        LinearLayout body=new LinearLayout(a);body.setOrientation(1);TextView help=new TextView(a);help.setText("Это список для следующего обновления Keps. Изменения попадут в рацию после загрузки TLE и отдельной записи.");body.addView(help);EditText[] fields=new EditText[values.length];
        for(int i=0;i<values.length;i++){TextView label=new TextView(a);label.setText(labels[i]);body.addView(label);fields[i]=new EditText(a);fields[i].setSingleLine(true);fields[i].setText(values[i]);body.addView(fields[i]);}
        ScrollView scroll=new ScrollView(a);scroll.addView(body);AlertDialog.Builder builder=new AlertDialog.Builder(a).setTitle(index<0?"Добавить спутник":c.name).setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);if(index>=0)builder.setNeutralButton("Удалить",null);
        AlertDialog dialog=builder.create();dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{
            String[] f=new String[11];for(int i=0;i<11;i++)f[i]=fields[i].getText().toString().trim();SatelliteConfig next=new SatelliteConfig(Integer.parseInt(f[0]),f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8],f[9],f[10]);
            List<SatelliteConfig> updated=new ArrayList<>(configs);if(index<0)updated.add(next);else updated.set(index,next);SatelliteConfigStore.validate(updated);save.apply(updated);dialog.dismiss();show(a,updated,save);
        }catch(Exception e){help.setText(e.getMessage());}});
        if(index>=0)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button->new AlertDialog.Builder(a).setTitle("Удалить "+c.name+" из списка обновления?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{try{List<SatelliteConfig> updated=new ArrayList<>(configs);updated.remove(index);save.apply(updated);dialog.dismiss();show(a,updated,save);}catch(Exception e){help.setText(e.getMessage());}}).show());});dialog.show();
    }
}
