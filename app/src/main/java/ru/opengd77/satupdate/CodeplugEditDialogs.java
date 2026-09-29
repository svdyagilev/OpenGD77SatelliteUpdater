package ru.opengd77.satupdate;

import android.app.*;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import java.util.*;

final class CodeplugEditDialogs {
    interface Commit { void apply(CodeplugProject.Change change) throws Exception; }
    interface Save { void apply(Map<String,String> changes) throws Exception; }
    interface Value { String get(); }
    private static final class Field {
        final String initial,group; final Value value;
        Field(String initial,String group,Value value){this.initial=initial;this.group=group;this.value=value;}
    }
    private static final class Form {
        final Activity activity;final LinearLayout body;LinearLayout target;
        final Map<String,Field> fields=new LinkedHashMap<>();String group="";Spinner mode;
        final String title; final Save save;
        Form(Activity a,String title,Save save){this.activity=a;this.title=title;this.save=save;
            body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);int p=(int)(16*a.getResources().getDisplayMetrics().density);body.setPadding(p,p,p,p);target=body;
            label("Правки сохраняются в проекте на телефоне. Радиостанция не изменяется.");}
        void label(String text){TextView t=new TextView(activity);t.setText(text);t.setPadding(0,10,0,4);target.addView(t);}
        EditText text(String key,String label,String value,boolean numeric){
            label(label);EditText e=new EditText(activity);e.setSingleLine(true);e.setText(value);
            e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            target.addView(e);fields.put(key,new Field(value,group,()->e.getText().toString().trim()));return e;
        }
        Spinner choice(String key,String label,String initial,String[] values,String[] names){
            label(label);List<String> vv=new ArrayList<>(Arrays.asList(values)),nn=new ArrayList<>(Arrays.asList(names));
            if(!vv.contains(initial)){vv.add(initial);nn.add("Исходное значение: "+initial);}
            Spinner spinner=new Spinner(activity);spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,nn));
            spinner.setSelection(vv.indexOf(initial));target.addView(spinner);
            fields.put(key,new Field(initial,group,()->vv.get(spinner.getSelectedItemPosition())));return spinner;
        }
        void check(String key,String label,boolean initial){
            CheckBox c=new CheckBox(activity);c.setText(label);c.setChecked(initial);target.addView(c);
            fields.put(key,new Field(initial?"1":"0",group,()->c.isChecked()?"1":"0"));
        }
        void show(){
            ScrollView scroll=new ScrollView(activity);scroll.addView(body);
            AlertDialog d=new AlertDialog.Builder(activity).setTitle(title).setView(scroll)
                    .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
            d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                try{
                    Map<String,String> values=new LinkedHashMap<>();
                    String active=mode==null?"":fields.get("mode").value.get();
                    for(Map.Entry<String,Field> entry:fields.entrySet()){
                        Field f=entry.getValue();if(!f.group.isEmpty()&&!f.group.equals(active))continue;
                        String value=f.value.get();if(!value.equals(f.initial))values.put(entry.getKey(),value);
                    }
                    save.apply(values);d.dismiss();
                }catch(Exception e){new AlertDialog.Builder(activity).setTitle("Проверьте значения").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
            }));d.show();
        }
    }
    private static String[] numbers(int min,int max){String[] a=new String[max-min+1];for(int i=min;i<=max;i++)a[i-min]=""+i;return a;}
    private static void references(Form f,String key,String label,int initial,List<Integer> ids,List<String> names){
        List<String> values=new ArrayList<>();values.add("0");values.addAll(toStrings(ids));
        List<String> labels=new ArrayList<>();labels.add("Нет");for(int i=0;i<ids.size();i++)labels.add("#"+ids.get(i)+" · "+names.get(i));
        f.choice(key,label,""+initial,values.toArray(new String[0]),labels.toArray(new String[0]));
    }
    private static List<String> toStrings(List<Integer> ids){List<String> out=new ArrayList<>();for(int i:ids)out.add(""+i);return out;}
    static void general(Activity a,CodeplugModel.General g,Commit commit){
        Form f=new Form(a,"DMR ID и позывной",changes->commit.apply(s->CodeplugEditor.general(s,changes)));
        f.text("name","Позывной / имя (до 8 символов)",g.radioName,false);f.text("id","DMR ID", ""+g.dmrId,true);f.show();
    }
    static boolean supported(Object o){return o instanceof CodeplugModel.Channel&&((CodeplugModel.Channel)o).index>0
            ||o instanceof CodeplugModel.Zone||o instanceof CodeplugModel.Contact||o instanceof CodeplugModel.DtmfContact
            ||o instanceof CodeplugModel.RxGroup||o instanceof CodeplugModel.AprsConfig;}
    static void edit(Activity a,Object o,CodeplugModel m,Commit commit){
        if(o instanceof CodeplugModel.Channel){channel(a,(CodeplugModel.Channel)o,m,commit);return;}
        if(o instanceof CodeplugModel.Zone){zone(a,(CodeplugModel.Zone)o,m,commit);return;}
        if(o instanceof CodeplugModel.RxGroup){rxGroup(a,(CodeplugModel.RxGroup)o,m,commit);return;}
        if(o instanceof CodeplugModel.AprsConfig){aprs(a,(CodeplugModel.AprsConfig)o,commit);return;}
        boolean dtmf=o instanceof CodeplugModel.DtmfContact;
        int index=dtmf?((CodeplugModel.DtmfContact)o).index:((CodeplugModel.Contact)o).index;
        String name=dtmf?((CodeplugModel.DtmfContact)o).name:((CodeplugModel.Contact)o).name;
        Form f=new Form(a,(dtmf?"Контакт DTMF ":"Контакт DMR ")+index,
                changes->commit.apply(s->CodeplugEditor.contact(s,index,dtmf,changes)));
        f.text("name","Имя (до 16 символов)",name,false);
        if(dtmf)f.text("code","Код DTMF",((CodeplugModel.DtmfContact)o).code,false);
        else{CodeplugModel.Contact c=(CodeplugModel.Contact)o;
            f.text("number","ID / TG", ""+c.number,true);
            f.choice("type","Тип вызова",""+c.type,numbers(0,2),new String[]{"Групповой","Индивидуальный","Общий"});
            f.choice("ts","Переопределение таймслота",""+c.tsOverride,new String[]{"3","0","2"},new String[]{"Нет","TS1","TS2"});}
        f.show();
    }
    static void boot(Activity a,CodeplugModel.BootInfo b,Commit commit){
        Form f=new Form(a,"Загрузочный экран",changes->commit.apply(s->CodeplugEditor.boot(s,changes)));
        f.choice("mode","Показывать при включении",""+b.introMode,new String[]{"0","1"},new String[]{"Изображение","Текст"});
        f.text("line1","Строка 1 (до 16 символов, можно пустую)",b.line1,false);
        f.text("line2","Строка 2 (до 16 символов, можно пустую)",b.line2,false);
        f.label("Строки отображаются в режиме «Текст». Изображение заставки здесь не меняется.");f.show();
    }
    private static void rxGroup(Activity a,CodeplugModel.RxGroup g,CodeplugModel m,Commit commit){
        Form f=new Form(a,"Группа приёма "+g.index,changes->commit.apply(s->CodeplugEditor.rxGroup(s,g.index,changes)));
        f.text("name","Имя (до 15 символов)",g.name,false);
        StringBuilder text=new StringBuilder();for(int id:g.contactIndices){if(text.length()>0)text.append(", ");text.append(id);}
        EditText members=f.text("members","Номера контактов в нужном порядке (до 32)",text.toString(),false);
        Button select=new Button(a);select.setText("Выбрать контакты по имени");f.body.addView(select);
        select.setOnClickListener(v->{
            List<Integer> selected=new ArrayList<>();
            try{for(String x:members.getText().toString().trim().split("[,;\\s]+"))if(!x.isEmpty())selected.add(Integer.parseInt(x));}
            catch(NumberFormatException e){new AlertDialog.Builder(a).setMessage("Проверьте номера контактов").setPositiveButton("OK",null).show();return;}
            String[] labels=new String[m.contacts.size()];boolean[] checked=new boolean[labels.length];
            for(int i=0;i<labels.length;i++){CodeplugModel.Contact c=m.contacts.get(i);labels[i]="#"+c.index+" · "+c.name+" · "+c.number;checked[i]=selected.contains(c.index);}
            new AlertDialog.Builder(a).setTitle("Контакты группы").setMultiChoiceItems(labels,checked,(d,which,on)->{
                int id=m.contacts.get(which).index;if(on&&!selected.contains(id))selected.add(id);else if(!on)selected.remove((Integer)id);
            }).setNegativeButton("Отмена",null).setPositiveButton("Готово",(d,w)->{
                StringBuilder value=new StringBuilder();for(int id:selected){if(value.length()>0)value.append(", ");value.append(id);}members.setText(value);
            }).show();
        });f.show();
    }
    private static void aprs(Activity a,CodeplugModel.AprsConfig ap,Commit commit){
        Form f=new Form(a,"APRS "+ap.index,changes->commit.apply(s->CodeplugEditor.aprs(s,ap.index,changes)));
        f.text("name","Имя (до 8 символов)",ap.name,false);
        f.choice("ssid","SSID отправителя",""+ap.senderSsid,numbers(0,15),numbers(0,15));
        f.text("tx","Частота передачи, МГц (0 — частота канала)",String.format(Locale.US,"%.6f",ap.txHz/1000000.0),false);
        f.choice("baud300","Скорость",(ap.flags&1)!=0?"1":"0",new String[]{"0","1"},new String[]{"1200 бод","300 бод"});
        f.text("via1","Маршрут 1 (до 6 букв A–Z и цифр)",ap.via1,false);
        f.choice("via1Ssid","SSID маршрута 1",""+ap.via1Ssid,numbers(0,15),numbers(0,15));
        f.text("via2","Маршрут 2 (до 6 букв A–Z и цифр)",ap.via2,false);
        f.choice("via2Ssid","SSID маршрута 2",""+ap.via2Ssid,numbers(0,15),numbers(0,15));
        f.text("comment","Комментарий (до 23 символов ASCII)",ap.comment,false);
        f.check("fixed","Использовать фиксированные координаты",(ap.flags&2)!=0);
        f.text("latitude","Широта, ° (−90…90)",String.format(Locale.US,"%.4f",ap.latitude),false);
        f.text("longitude","Долгота, ° (−180…180)",String.format(Locale.US,"%.4f",ap.longitude),false);
        f.check("qsy","Передавать QSY",(ap.flags&4)!=0);
        f.show();
    }
    private static void channel(Activity a,CodeplugModel.Channel c,CodeplugModel m,Commit commit){
        Form f=new Form(a,"Канал "+c.index,changes->commit.apply(s->CodeplugEditor.channel(s,c.index,changes)));
        f.label("Общие настройки");
        f.text("name","Имя (до 16 символов)",c.name,false);
        f.mode=f.choice("mode","Режим",c.digital?"1":"0",new String[]{"0","1"},new String[]{"Аналоговый (FM)","Цифровой (DMR)"});
        f.text("rx","Приём, МГц",String.format(Locale.US,"%.6f",c.rxHz/1000000.0),false);
        f.text("tx","Передача, МГц",String.format(Locale.US,"%.6f",c.txHz/1000000.0),false);
        f.choice("power","Мощность",""+c.powerSetting,numbers(0,10),new String[]{"От общей настройки","100 мВт","250 мВт","500 мВт","750 мВт","1 Вт","5 Вт","10 Вт","25 Вт","40 Вт","+Вт−"});
        f.text("tot","Ограничение передачи, с (0 — выкл., шаг 15)",""+c.totSeconds,true);
        f.choice("step","Шаг частоты",""+c.stepIndex,numbers(0,7),new String[]{"2.5 кГц","5 кГц","6.25 кГц","10 кГц","12.5 кГц","25 кГц","30 кГц","50 кГц"});
        f.check("rxOnly","Только приём",c.rxOnly);f.check("beep","Звуки включены",c.beepEnabled);f.check("eco","Экономайзер включён",c.ecoEnabled);
        f.check("vox","VOX",c.vox);f.check("zoneSkip","Пропуск при сканировании зоны",c.zoneSkip);f.check("allSkip","Пропуск при сканировании всех каналов",c.allSkip);
        f.check("fast","Быстрый вызов",c.fastCall);f.check("priority","Приоритетное сканирование",c.priority);
        LinearLayout fm=new LinearLayout(a);fm.setOrientation(LinearLayout.VERTICAL);f.body.addView(fm);f.target=fm;f.group="0";
        f.label("Аналоговая связь");f.check("wide","Широкая полоса 25 кГц (иначе 12.5)",c.wide25k);
        f.text("rxTone","Субтон приёма: нет / CTCSS 88.5 / DCS 023 N",c.rxTone.displayText(),false);
        f.text("txTone","Субтон передачи: нет / CTCSS 88.5 / DCS 023 I",c.txTone.displayText(),false);
        String[] sql=new String[22];sql[0]="От общей настройки";sql[1]="Открыт";for(int i=2;i<=20;i++)sql[i]=((i-1)*5)+"%";sql[21]="Закрыт";
        f.choice("sql","Шумоподавитель",""+(c.squelchOverride?c.squelchLevel:0),numbers(0,21),sql);
        List<Integer> ids=new ArrayList<>();List<String> names=new ArrayList<>();for(CodeplugModel.AprsConfig ap:m.aprsConfigs){ids.add(ap.index);names.add(ap.name);}references(f,"aprs","APRS",c.aprsConfigIndex,ids,names);
        LinearLayout dmr=new LinearLayout(a);dmr.setOrientation(LinearLayout.VERTICAL);f.body.addView(dmr);f.target=dmr;f.group="1";
        f.label("Цифровая связь");f.choice("cc","Цветовой код",""+c.colorCode,numbers(0,15),numbers(0,15));
        f.choice("ts","Таймслот",""+c.timeSlot,numbers(1,2),new String[]{"TS1","TS2"});
        ids.clear();names.clear();for(CodeplugModel.Contact co:m.contacts){ids.add(co.index);names.add(co.name);}references(f,"contact","Контакт",c.contactIndex,ids,names);
        ids.clear();names.clear();for(CodeplugModel.RxGroup g:m.rxGroups){ids.add(g.index);names.add(g.name);}references(f,"group","Группа приёма",c.rxGroupIndex,ids,names);
        f.text("optionalId","DMR ID канала (0 — общий)",""+c.optionalDmrId,true);
        f.check("dmo","Прямая связь (DMO)",c.forceDmo);f.check("roaming","Роуминг",c.roaming);
        f.choice("ta1","Алиас в TS1",""+c.taTxTs1,numbers(0,3),new String[]{"Выкл.","APRS","Текст","APRS + текст"});
        f.choice("ta2","Алиас в TS2",""+c.taTxTs2,numbers(0,3),new String[]{"Выкл.","APRS","Текст","APRS + текст"});
        f.mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View v,int position,long id){boolean digital=position==1;fm.setVisibility(digital?View.GONE:View.VISIBLE);dmr.setVisibility(digital?View.VISIBLE:View.GONE);}
            public void onNothingSelected(AdapterView<?> parent){}
        });
        fm.setVisibility(c.digital?View.GONE:View.VISIBLE);dmr.setVisibility(c.digital?View.VISIBLE:View.GONE);
        f.show();
    }
    private static void zone(Activity a,CodeplugModel.Zone z,CodeplugModel m,Commit commit){
        Form f=new Form(a,"Зона "+z.index,changes->commit.apply(s->CodeplugEditor.zone(s,z.index,changes)));
        f.text("name","Имя (до 16 символов)",z.name,false);
        StringBuilder initial=new StringBuilder();for(int id:z.channelIndices){if(initial.length()>0)initial.append(", ");initial.append(id);}
        EditText members=f.text("members","Номера каналов в нужном порядке (до 80)",initial.toString(),false);
        Button select=new Button(a);select.setText("Выбрать каналы по имени");f.body.addView(select);
        select.setOnClickListener(v->{
            List<Integer> selected=new ArrayList<>();
            try{for(String x:members.getText().toString().trim().split("[,;\\s]+"))if(!x.isEmpty())selected.add(Integer.parseInt(x));}
            catch(NumberFormatException e){new AlertDialog.Builder(a).setMessage("Проверьте номера каналов").setPositiveButton("OK",null).show();return;}
            String[] labels=new String[m.channels.size()];boolean[] checked=new boolean[labels.length];
            for(int i=0;i<labels.length;i++){CodeplugModel.Channel c=m.channels.get(i);labels[i]="#"+c.index+" · "+c.name;checked[i]=selected.contains(c.index);}
            new AlertDialog.Builder(a).setTitle("Каналы зоны").setMultiChoiceItems(labels,checked,(d,which,on)->{
                int id=m.channels.get(which).index;if(on&&!selected.contains(id))selected.add(id);else if(!on)selected.remove((Integer)id);
            }).setNegativeButton("Отмена",null).setPositiveButton("Готово",(d,w)->{
                StringBuilder text=new StringBuilder();for(int id:selected){if(text.length()>0)text.append(", ");text.append(id);}members.setText(text);
            }).show();
        });f.show();
    }
}
