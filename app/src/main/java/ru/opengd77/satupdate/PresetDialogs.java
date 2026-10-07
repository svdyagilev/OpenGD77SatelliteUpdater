package ru.opengd77.satupdate;
import android.app.*;
import android.widget.*;
import java.util.*;
final class PresetDialogs {
    static void show(Activity a,CodeplugModel m,CodeplugEditDialogs.Commit commit){
        new AlertDialog.Builder(a).setTitle("Предустановленные каналы").setItems(ChannelPresets.NAMES,(d,p)->choose(a,m,p,commit)).setNegativeButton("Отмена",null).show();
    }
    private static void choose(Activity a,CodeplugModel m,int plan,CodeplugEditDialogs.Commit commit){
        long[] hz=ChannelPresets.frequencies(plan);String[] names=new String[hz.length];boolean[] selected=new boolean[hz.length];Arrays.fill(selected,true);
        for(int i=0;i<hz.length;i++)names[i]=ChannelPresets.label(plan,i);
        LinearLayout body=new LinearLayout(a);body.setOrientation(1);int pad=(int)(16*a.getResources().getDisplayMetrics().density);body.setPadding(pad,0,pad,0);
        TextView hint=new TextView(a);hint.setText("FM, без субтонов. LPD: 25 кГц; PMR/FRS: 12,5 кГц. FRS — план США. Набор частот сам по себе не определяет разрешённый режим работы радиостанции.");body.addView(hint);
        CheckBox skip=new CheckBox(a);skip.setText("Использовать существующие каналы с теми же RX/TX");skip.setChecked(true);body.addView(skip);
        CheckBox rx=new CheckBox(a);rx.setText("Только приём для новых каналов");body.addView(rx);
        TextView pl=new TextView(a);pl.setText("Мощность новых каналов");body.addView(pl);Spinner power=new Spinner(a);
        power.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"От Master","100 мВт","250 мВт","500 мВт","750 мВт","1 Вт","5 Вт","10 Вт","25 Вт","40 Вт","+W−"}));body.addView(power);
        TextView zl=new TextView(a);zl.setText("Зона");body.addView(zl);List<String> zn=new ArrayList<>();zn.add("Создать новую зону");zn.add("Не добавлять в зону");for(CodeplugModel.Zone z:m.zones)zn.add(z.oneLine());Spinner zone=new Spinner(a);zone.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,zn));body.addView(zone);
        EditText zoneName=new EditText(a);zoneName.setSingleLine(true);zoneName.setText(ChannelPresets.PREFIX[plan]);zoneName.setHint("Имя новой зоны");body.addView(zoneName);
        ScrollView options=new ScrollView(a);options.addView(body);options.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(240*a.getResources().getDisplayMetrics().density)));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(ChannelPresets.NAMES[plan]).setView(options).setMultiChoiceItems(names,selected,(d,i,on)->selected[i]=on).setNegativeButton("Отмена",null).setNeutralButton("Все / снять",null).setPositiveButton("Предпросмотр",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b->{boolean all=true;for(boolean on:selected)all&=on;Arrays.fill(selected,!all);for(int i=0;i<selected.length;i++)dialog.getListView().setItemChecked(i,!all);});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{
                List<Integer> nums=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])nums.add(i);if(nums.isEmpty())throw new IllegalArgumentException("Выберите каналы");
                int z=zone.getSelectedItemPosition(),target=z==0?-1:z==1?0:m.zones.get(z-2).index;String name=zoneName.getText().toString().trim();if(target<0&&(name.isEmpty()||name.length()>16))throw new IllegalArgumentException("Имя зоны: 1–16 символов");
                boolean duplicates=skip.isChecked(),receive=rx.isChecked();int level=power.getSelectedItemPosition();
                commit.apply(s->ChannelPresets.append(s,plan,nums,duplicates,receive,level,target,name));dialog.dismiss();
            }catch(Exception e){new AlertDialog.Builder(a).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}});
        });dialog.show();
    }
}
