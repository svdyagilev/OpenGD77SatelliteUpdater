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
        final Map<String,Field> fields=new LinkedHashMap<>();String group="";Spinner mode;boolean creating;
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
        void tone(String key,String title,CodeplugModel.Tone initial){
            label(title);
            String[] selected={initial.displayText()};
            Button button=new Button(activity);button.setText(selected[0]);target.addView(button);
            fields.put(key,new Field(selected[0],group,()->selected[0]));
            button.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle(title)
                .setItems(new String[]{"Нет","CTCSS","DCS N — обычный","DCS I — инверсный"},(dialog,kind)->{
                    if(kind==0){selected[0]="нет";button.setText(selected[0]);return;}
                    String[] choices=ToneChoices.values(kind);
                    int checked=Arrays.asList(choices).indexOf(selected[0]);
                    new AlertDialog.Builder(activity).setTitle(kind==1?"CTCSS, Гц":kind==2?"DCS N":"DCS I")
                        .setSingleChoiceItems(choices,checked,(picker,which)->{
                            selected[0]=choices[which];button.setText(selected[0]);picker.dismiss();
                        }).setNegativeButton("Отмена",null).show();
                }).setNegativeButton("Отмена",null).show());
        }
        void check(String key,String label,boolean initial){
            CheckBox c=new CheckBox(activity);c.setText(label);c.setChecked(initial);target.addView(c);
            fields.put(key,new Field(initial?"1":"0",group,()->c.isChecked()?"1":"0"));
        }
        void show(){
            ScrollView scroll=new ScrollView(activity);scroll.addView(body);
            AlertDialog d=new AlertDialog.Builder(activity).setTitle(title).setView(scroll)
                    .setNegativeButton("Отмена",null).setPositiveButton(creating?"Создать":"Сохранить",null).create();
            d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                try{
                    Map<String,String> values=new LinkedHashMap<>();
                    String active=mode==null?"":fields.get("mode").value.get();
                    for(Map.Entry<String,Field> entry:fields.entrySet()){
                        Field f=entry.getValue();if(!f.group.isEmpty()&&!f.group.equals(active))continue;
                        String value=f.value.get();if(creating||!value.equals(f.initial))values.put(entry.getKey(),value);
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
    static boolean supported(Object o){return o instanceof CodeplugModel.Channel
            ||o instanceof CodeplugModel.Zone||o instanceof CodeplugModel.Contact||o instanceof CodeplugModel.DtmfContact
            ||o instanceof CodeplugModel.RxGroup||o instanceof CodeplugModel.AprsConfig||o instanceof CodeplugModel.ScanList;}
    static void create(Activity a,CodeplugProject project,CodeplugRecords.Kind kind,Commit commit){
        int index=CodeplugRecords.next(project.working,kind);
        CodeplugSnapshot preview=CodeplugProject.copy(project.working);
        CodeplugRecords.seed(preview,kind,index);
        CodeplugModel m=OpenGd77CodeplugDecoder.decode(preview);
        edit(a,CodeplugRecords.record(m,kind,index),m,commit,kind);
    }
    static void edit(Activity a,Object o,CodeplugModel m,Commit commit){edit(a,o,m,commit,null);}
    private static Form recordForm(Activity a,String title,Commit commit,CodeplugRecords.Kind creating,int index,Save edit){
        Form f=new Form(a,creating==null?title:"Создать: "+title,
            creating==null?edit:values->commit.apply(s->CodeplugRecords.create(s,creating,index,values)));
        f.creating=creating!=null;return f;
    }
    private static void edit(Activity a,Object o,CodeplugModel m,Commit commit,CodeplugRecords.Kind creating){
        if(o instanceof CodeplugModel.Channel){channel(a,(CodeplugModel.Channel)o,m,commit,creating);return;}
        if(o instanceof CodeplugModel.Zone){zone(a,(CodeplugModel.Zone)o,m,commit,creating);return;}
        if(o instanceof CodeplugModel.RxGroup){rxGroup(a,(CodeplugModel.RxGroup)o,m,commit,creating);return;}
        if(o instanceof CodeplugModel.AprsConfig){aprs(a,(CodeplugModel.AprsConfig)o,commit,creating);return;}
        if(o instanceof CodeplugModel.ScanList){scan(a,(CodeplugModel.ScanList)o,m,commit,creating);return;}
        boolean dtmf=o instanceof CodeplugModel.DtmfContact;
        int index=dtmf?((CodeplugModel.DtmfContact)o).index:((CodeplugModel.Contact)o).index;
        String name=dtmf?((CodeplugModel.DtmfContact)o).name:((CodeplugModel.Contact)o).name;
        Form f=recordForm(a,(dtmf?"Контакт DTMF ":"Контакт DMR ")+index,commit,creating,index,
                changes->commit.apply(s->CodeplugEditor.contact(s,index,dtmf,changes)));
        f.text("name","Имя (до 16 символов)",creating==null?name:"",false);
        if(dtmf)f.text("code","Код DTMF",creating==null?((CodeplugModel.DtmfContact)o).code:"",false);
        else{CodeplugModel.Contact c=(CodeplugModel.Contact)o;
            EditText number=f.text("number","ID / TG", creating==null?""+c.number:"",true);
            Spinner callType=f.choice("type","Тип вызова",""+c.type,numbers(0,2),new String[]{"Групповой","Индивидуальный","Общий"});
            String[] previousNumber={c.type==2?"":number.getText().toString()};
            boolean[] wasAll={c.type==2};
            if(c.type==2){number.setText("16777215");number.setEnabled(false);}
            callType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                    boolean all=position==2;
                    if(all){if(!wasAll[0])previousNumber[0]=number.getText().toString();number.setText("16777215");}
                    else if(wasAll[0])number.setText(previousNumber[0]);
                    number.setEnabled(!all);wasAll[0]=all;
                }
                public void onNothingSelected(AdapterView<?> parent){}
            });
            f.choice("ts","Переопределение таймслота",""+c.tsOverride,new String[]{"3","0","2"},new String[]{"Нет","TS1","TS2"});}
        f.show();
    }
    static void boot(Activity a,CodeplugModel.BootInfo b,Commit commit,Runnable pickImage){
        Form f=new Form(a,"Загрузочный экран",changes->commit.apply(s->CodeplugEditor.boot(s,changes)));
        Spinner mode=f.choice("mode","Показывать при включении",""+b.introMode,new String[]{"0","1"},new String[]{"Изображение","Текст"});
        LinearLayout text=new LinearLayout(a);text.setOrientation(1);f.body.addView(text);f.target=text;f.group="1";f.mode=mode;
        f.text("line1","Строка 1 (до 16 символов)",b.line1,false);f.text("line2","Строка 2 (до 16 символов)",b.line2,false);
        f.target=f.body;f.group="";
        Button image=new Button(a);image.setText("Загрузить изображение…");f.body.addView(image);
        image.setOnClickListener(v->pickImage.run());
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View v,int pos,long id){text.setVisibility(pos==1?View.VISIBLE:View.GONE);image.setVisibility(pos==0?View.VISIBLE:View.GONE);}public void onNothingSelected(AdapterView<?> parent){}});
        text.setVisibility(b.introMode==1?View.VISIBLE:View.GONE);image.setVisibility(b.introMode==0?View.VISIBLE:View.GONE);
        f.show();
    }

    private static void rxGroup(Activity a,CodeplugModel.RxGroup g,CodeplugModel m,Commit commit,CodeplugRecords.Kind creating){
        Form f=recordForm(a,"Группа приёма "+g.index,commit,creating,g.index,changes->commit.apply(s->CodeplugEditor.rxGroup(s,g.index,changes)));
        f.text("name","Имя (до 15 символов)",creating==null?g.name:"",false);
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
            }).create();
            attachSelectAll(a,picker,checked,on->{selected.clear();if(on)for(CodeplugModel.Channel c:m.channels)selected.add(c.index);});picker.show();
        });f.show();
    }
    private static void aprs(Activity a,CodeplugModel.AprsConfig ap,Commit commit,CodeplugRecords.Kind creating){
        Form f=recordForm(a,"APRS "+ap.index,commit,creating,ap.index,changes->commit.apply(s->CodeplugEditor.aprs(s,ap.index,changes)));
        f.text("name","Имя (до 8 символов)",creating==null?ap.name:"",false);
        f.choice("ssid","SSID отправителя",""+ap.senderSsid,numbers(0,15),numbers(0,15));
        f.text("tx","Частота передачи, МГц (0 — частота канала)",String.format(Locale.US,"%.6f",ap.txHz/1000000.0),false);
        f.choice("baud300","Скорость",(ap.flags&1)!=0?"1":"0",new String[]{"0","1"},new String[]{"1200 бод","300 бод"});
        f.text("via1","Маршрут 1 (до 6 букв A–Z и цифр)",ap.via1,false);
        f.choice("via1Ssid","SSID маршрута 1",""+ap.via1Ssid,numbers(0,15),numbers(0,15));
        f.text("via2","Маршрут 2 (до 6 букв A–Z и цифр)",ap.via2,false);
        f.choice("via2Ssid","SSID маршрута 2",""+ap.via2Ssid,numbers(0,15),numbers(0,15));
        f.text("comment","Комментарий (до 23 символов ASCII)",ap.comment,false);
        String tables="/\\ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";String[] tableValues=new String[tables.length()];
        for(int n=0;n<tables.length();n++)tableValues[n]=tables.substring(n,n+1);
        f.choice("iconTable","Таблица символов APRS / оверлей",Character.toString((char)ap.iconTable),tableValues,tableValues);
        String[] icons=new String[94];for(int n=0;n<94;n++)icons[n]=Character.toString((char)(33+n));
        f.choice("icon","Символ APRS",Character.toString((char)ap.iconIndex),icons,icons);
        f.check("fixed","Использовать фиксированные координаты",(ap.flags&2)!=0);
        f.text("latitude","Широта, ° (−90…90)",String.format(Locale.US,"%.4f",ap.latitude),false);
        f.text("longitude","Долгота, ° (−180…180)",String.format(Locale.US,"%.4f",ap.longitude),false);
        f.check("qsy","Передавать QSY",(ap.flags&4)!=0);
        f.show();
    }
    private static void channel(Activity a,CodeplugModel.Channel c,CodeplugModel m,Commit commit,CodeplugRecords.Kind creating){
        final int vfo=c.index==0?m.vfos.indexOf(c):-1;
        Form f=recordForm(a,vfo>=0?"VFO "+(vfo==0?"A":"B"):"Канал "+c.index,commit,creating,c.index,
            changes->commit.apply(s->{if(vfo>=0)CodeplugLists.vfo(s,vfo,changes);else CodeplugEditor.channel(s,c.index,changes);}));
        channelFields(a,f,c,m,creating!=null);f.show();
    }
    private static void channelFields(Activity a,Form f,CodeplugModel.Channel c,CodeplugModel m,boolean fresh){
        f.label("Общие настройки");
        f.text("name","Имя (до 16 символов)",fresh?"":c.name,false);
        f.mode=f.choice("mode","Режим",c.digital?"1":"0",new String[]{"0","1"},new String[]{"Аналоговый (FM)","Цифровой (DMR)"});
        f.text("rx","Приём, МГц",fresh?"":String.format(Locale.US,"%.6f",c.rxHz/1000000.0),false);
        f.text("tx","Передача, МГц",fresh?"":String.format(Locale.US,"%.6f",c.txHz/1000000.0),false);
        f.choice("power","Мощность",""+c.powerSetting,numbers(0,10),new String[]{"От общей настройки","100 мВт","250 мВт","500 мВт","750 мВт","1 Вт","5 Вт","10 Вт","25 Вт","40 Вт","+Вт−"});
        f.text("tot","Ограничение передачи, с (0 — выкл., шаг 15)",""+c.totSeconds,true);
        f.choice("step","Шаг частоты",""+c.stepIndex,numbers(0,7),new String[]{"2.5 кГц","5 кГц","6.25 кГц","10 кГц","12.5 кГц","25 кГц","30 кГц","50 кГц"});
        f.check("rxOnly","Только приём",c.rxOnly);f.check("beep","Звуки включены",c.beepEnabled);f.check("eco","Экономайзер включён",c.ecoEnabled);
        f.check("vox","VOX",c.vox);f.check("zoneSkip","Пропуск при сканировании зоны",c.zoneSkip);f.check("allSkip","Пропуск при сканировании всех каналов",c.allSkip);
        f.check("fast","Быстрый вызов",c.fastCall);f.check("priority","Приоритетное сканирование",c.priority);
        f.check("location","Использовать заданные координаты",c.useLocation);
        f.text("latitude","Широта, °",String.format(Locale.US,"%.4f",c.latitude),false);
        f.text("longitude","Долгота, °",String.format(Locale.US,"%.4f",c.longitude),false);
        LinearLayout fm=new LinearLayout(a);fm.setOrientation(LinearLayout.VERTICAL);f.body.addView(fm);f.target=fm;f.group="0";
        f.label("Аналоговая связь");f.check("wide","Широкая полоса 25 кГц (иначе 12.5)",c.wide25k);
        f.tone("rxTone","Субтон приёма",c.rxTone);
        f.tone("txTone","Субтон передачи",c.txTone);
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
    }
    private static void zone(Activity a,CodeplugModel.Zone z,CodeplugModel m,Commit commit,CodeplugRecords.Kind creating){
        Form f=recordForm(a,"Зона "+z.index,commit,creating,z.index,changes->commit.apply(s->CodeplugEditor.zone(s,z.index,changes)));
        f.text("name","Имя (до 16 символов)",creating==null?z.name:"",false);
        StringBuilder initial=new StringBuilder();for(int id:z.channelIndices){if(initial.length()>0)initial.append(", ");initial.append(id);}
        EditText members=f.text("members","Номера каналов в нужном порядке (до 80)",initial.toString(),false);
        Button select=new Button(a);select.setText("Выбрать каналы по имени");f.body.addView(select);
        select.setOnClickListener(v->{
            List<Integer> selected=new ArrayList<>();
            try{for(String x:members.getText().toString().trim().split("[,;\\s]+"))if(!x.isEmpty())selected.add(Integer.parseInt(x));}
            catch(NumberFormatException e){new AlertDialog.Builder(a).setMessage("Проверьте номера каналов").setPositiveButton("OK",null).show();return;}
            String[] labels=new String[m.channels.size()];boolean[] checked=new boolean[labels.length];
            for(int i=0;i<labels.length;i++){CodeplugModel.Channel c=m.channels.get(i);labels[i]="#"+c.index+" · "+c.name;checked[i]=selected.contains(c.index);}
            AlertDialog picker=new AlertDialog.Builder(a).setTitle("Каналы зоны").setMultiChoiceItems(labels,checked,(d,which,on)->{
                int id=m.channels.get(which).index;if(on&&!selected.contains(id))selected.add(id);else if(!on)selected.remove((Integer)id);
            }).setNegativeButton("Отмена",null).setPositiveButton("Готово",null).create();
            attachSelectAll(a,picker,checked,on->{selected.clear();if(on)for(CodeplugModel.Channel c:m.channels)selected.add(c.index);});
            picker.setOnShowListener(vv->picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                if(selected.size()>80){new AlertDialog.Builder(a).setMessage("В зоне не более 80 каналов. Снимите лишние отметки.").setPositiveButton("OK",null).show();return;}
                members.setText(CodeplugRecords.join(selected));picker.dismiss();
            }));picker.show();
        });f.show();
    }

    static void bulkChannels(Activity a,CodeplugModel model,List<Integer> ids,Commit commit){
        Form f=new Form(a,"Массовая правка: "+ids.size()+" каналов",values->{
            Map<String,String> patch=new LinkedHashMap<>(values);
            boolean rx="1".equals(patch.remove("setRxTone")),tx="1".equals(patch.remove("setTxTone"));
            if(!rx)patch.remove("rxTone");else if(!patch.containsKey("rxTone"))patch.put("rxTone","нет");
            if(!tx)patch.remove("txTone");else if(!patch.containsKey("txTone"))patch.put("txTone","нет");
            patch.values().removeAll(Collections.singleton(""));
            commit.apply(snapshot->ChannelBatch.update(snapshot,ids,patch));
        });
        f.label("Мощность — для всех выбранных. Субтоны — только FM. Color Code и TS — только DMR. Режим каналов сохраняется.");
        f.choice("power","Мощность","",new String[]{"","0","1","2","3","4","5","6","7","8","9","10"},
            new String[]{"Не менять","От общей настройки","100 мВт","250 мВт","500 мВт","750 мВт","1 Вт","5 Вт","10 Вт","25 Вт","40 Вт","+Вт−"});
        f.check("setRxTone","Изменить субтон приёма FM",false);
        f.tone("rxTone","Новый субтон приёма",new CodeplugModel.Tone(CodeplugModel.Tone.Type.NONE,0,0xffff));
        f.check("setTxTone","Изменить субтон передачи FM",false);
        f.tone("txTone","Новый субтон передачи",new CodeplugModel.Tone(CodeplugModel.Tone.Type.NONE,0,0xffff));
        String[] cc=new String[17],ccLabels=new String[17];cc[0]="";ccLabels[0]="Не менять";
        for(int code=0;code<16;code++){cc[code+1]=""+code;ccLabels[code+1]=""+code;}
        f.choice("cc","Color Code DMR","",cc,ccLabels);
        f.choice("ts","Таймслот DMR","",new String[]{"","1","2"},new String[]{"Не менять","TS1","TS2"});f.show();
    }

    static void series(Activity a,CodeplugModel.Channel template,CodeplugModel model,Commit commit){
        Form f=new Form(a,"Добавить серию каналов",values->commit.apply(snapshot->ChannelBatch.configuredSeries(snapshot,template.index,values)));
        f.creating=true;f.label("Имя будет дополнено номером 1, 2, …; RX и TX увеличиваются на выбранный шаг серии.");
        f.text("count","Количество каналов","1",true);
        f.choice("spacing","Шаг серии","12.5",new String[]{"0","2.5","5","6.25","10","12.5","25","30","50"},new String[]{"0 — одинаковые частоты","2.5 кГц","5 кГц","6.25 кГц","10 кГц","12.5 кГц","25 кГц","30 кГц","50 кГц"});
        channelFields(a,f,template,model,template.index==0);f.show();
    }
    static void newSeries(Activity a,CodeplugProject project,Commit commit){
        CodeplugSnapshot preview=CodeplugProject.copy(project.working);int id=CodeplugRecords.next(preview,CodeplugRecords.Kind.CHANNEL);CodeplugRecords.seed(preview,CodeplugRecords.Kind.CHANNEL,id);
        CodeplugModel model=OpenGd77CodeplugDecoder.decode(preview);
        CodeplugModel.Channel channel=(CodeplugModel.Channel)CodeplugRecords.record(model,CodeplugRecords.Kind.CHANNEL,id);
        // index 0 denotes fresh creation rather than copying a physical record.
        seriesForm(a,channel,model,commit);
    }
    private static void seriesForm(Activity a,CodeplugModel.Channel channel,CodeplugModel model,Commit commit){
        Form f=new Form(a,"Добавить серию каналов",values->commit.apply(snapshot->ChannelBatch.configuredSeries(snapshot,0,values)));
        f.creating=true;f.label("Имя + номер 1, 2, …; RX/TX — начальные частоты.");f.text("count","Количество каналов","1",true);
        f.choice("spacing","Шаг серии","12.5",new String[]{"0","2.5","5","6.25","10","12.5","25","30","50"},new String[]{"0 — одинаковые частоты","2.5 кГц","5 кГц","6.25 кГц","10 кГц","12.5 кГц","25 кГц","30 кГц","50 кГц"});
        channelFields(a,f,channel,model,true);f.show();
    }

    static void zoneOrder(Activity a,CodeplugModel.Zone zone,CodeplugModel model,Commit commit){
        List<Integer> order=new ArrayList<>(zone.channelIndices);
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        TextView help=new TextView(a);
        ChannelOrderList list=new ChannelOrderList(a,order,model,()->{});
        Runnable refresh=()->{list.refresh();help.setText("Нажимайте каналы для выделения. Удерживайте и тяните канал или выделенную группу; у края список прокручивается. Выбрано: "+list.selected.size());};
        body.addView(help);
        body.addView(list,new LinearLayout.LayoutParams(-1,(int)Math.min(a.getResources().getDisplayMetrics().density*320,a.getResources().getDisplayMetrics().heightPixels*0.4f)));
        LinearLayout actions=new LinearLayout(a);body.addView(actions);
        Button remove=new Button(a);remove.setText("Убрать выбранные");actions.addView(remove,new LinearLayout.LayoutParams(0,-2,1));
        remove.setOnClickListener(v->{order.removeAll(list.selected);list.selected.clear();refresh.run();});
        Button move=new Button(a);move.setText("К позиции…");actions.addView(move,new LinearLayout.LayoutParams(0,-2,1));
        move.setOnClickListener(v->{
            if(list.selected.isEmpty())return;
            EditText position=new EditText(a);position.setInputType(InputType.TYPE_CLASS_NUMBER);position.setHint("Позиция 1…"+order.size());
            AlertDialog mover=new AlertDialog.Builder(a).setTitle("Переместить выбранные").setView(position).setNegativeButton("Отмена",null).setPositiveButton("Переместить",null).create();
            mover.setOnShowListener(w->mover.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{
                int target=Integer.parseInt(position.getText().toString())-1;if(target<0||target>=order.size())throw new IllegalArgumentException();
                int source=-1;for(int i=0;i<order.size();i++)if(list.selected.contains(order.get(i))){source=i;break;}
                ChannelOrder.move(order,list.selected,source,target);refresh.run();mover.dismiss();
            }catch(Exception e){position.setError("Укажите позицию от 1 до "+order.size());}}));mover.show();
        });
        list.setOnItemClickListener((parent,view,index,id)->{int channel=order.get(index);if(!list.selected.add(channel))list.selected.remove(channel);refresh.run();});
        refresh.run();
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Порядок каналов: "+zone.name).setView(body)
            .setNeutralButton("Добавить каналы",null).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button->{
                List<Integer> additions=new ArrayList<>();List<CodeplugModel.Channel> available=new ArrayList<>();
                for(CodeplugModel.Channel c:model.channels)if(!order.contains(c.index))available.add(c);
                String[] names=new String[available.size()];for(int i=0;i<names.length;i++)names[i]="#"+available.get(i).index+" · "+available.get(i).name;
                boolean[] allChecked=new boolean[names.length];
                AlertDialog picker=new AlertDialog.Builder(a).setTitle("Добавить в конец зоны").setMultiChoiceItems(names,allChecked,(d,which,on)->{
                    int channel=available.get(which).index;if(on)additions.add(channel);else additions.remove((Integer)channel);
                }).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();
                // The native callback below owns additions; the header selects the same IDs.
                attachSelectAll(a,picker,allChecked,on->{additions.clear();if(on)for(CodeplugModel.Channel c:available)additions.add(c.index);});
                picker.setOnShowListener(x->picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button2->{
                    if(order.size()+additions.size()>80){new AlertDialog.Builder(a).setMessage("В зоне не более 80 каналов").setPositiveButton("OK",null).show();return;}
                    order.addAll(additions);refresh.run();picker.dismiss();
                }));picker.show();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{
                Map<String,String> values=new LinkedHashMap<>();values.put("members",CodeplugRecords.join(order));
                commit.apply(snapshot->CodeplugEditor.zone(snapshot,zone.index,values));dialog.dismiss();
            }catch(Exception e){new AlertDialog.Builder(a).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}});
        });dialog.show();
    }

    private interface AllSelection {void apply(boolean on);}
    private static void attachSelectAll(Activity a,AlertDialog picker,boolean[] checked,AllSelection selection){
        CheckBox all=new CheckBox(a);all.setText("Выбрать все");all.setPadding(20,12,20,12);picker.setCustomTitle(all);
        all.setOnCheckedChangeListener((button,on)->{Arrays.fill(checked,on);selection.apply(on);ListView list=picker.getListView();if(list!=null)for(int i=0;i<checked.length;i++)list.setItemChecked(i,on);});
    }
    static void radio(Activity a,CodeplugModel m,Commit commit){
        Form f=new Form(a,"Настройки рации",v->commit.apply(s->CodeplugLists.radio(s,v)));
        f.choice("vox","Чувствительность VOX",""+m.general.voxSense,numbers(1,10),numbers(1,10));f.show();
    }
    static void bands(Activity a,CodeplugModel m,Commit commit){
        Form f=new Form(a,"Границы частот codeplug",v->commit.apply(s->CodeplugLists.bands(s,v)));
        f.label("Это значения codeplug. Фактические ограничения диапазонов задаёт прошивка.");
        f.text("uhfMin","Нижняя UHF, целые МГц",""+m.deviceInfo.minUhf,true);
        f.text("uhfMax","Верхняя UHF, целые МГц",""+m.deviceInfo.maxUhf,true);
        f.text("vhfMin","Нижняя VHF, целые МГц",""+m.deviceInfo.minVhf,true);
        f.text("vhfMax","Верхняя VHF, целые МГц",""+m.deviceInfo.maxVhf,true);f.show();
    }
    static void dtmfSettings(Activity a,CodeplugModel.DtmfSettings d,Commit commit){
        Form f=new Form(a,"Настройки DTMF",v->commit.apply(s->CodeplugLists.dtmf(s,v)));
        f.text("self","Собственный ID (до 8)",d.selfId,false);
        f.text("kill","Код блокировки (до 16)",d.killCode,false);f.text("wake","Код разблокировки (до 16)",d.wakeCode,false);
        String[] symbols="0 1 2 3 4 5 6 7 8 9 A B C D * #".split(" ");
        f.choice("delimiter","Разделитель",""+d.delimiter,numbers(0,15),symbols);
        f.choice("groupCode","Групповой символ",""+d.groupCode,numbers(0,15),symbols);
        f.choice("response","Ответ декодера (код CPS)",""+d.decodeResponse,numbers(0,3),numbers(0,3));
        f.text("reset","Автосброс, с",""+d.autoResetSeconds,true);
        f.check("killWake","Декодирование блокировки / разблокировки",d.killWakeDecode);
        f.choice("killType","Тип блокировки (код CPS)",""+d.killType,numbers(0,3),numbers(0,3));
        f.text("up","Код при нажатии PTT (до 30)",d.pttUp,false);f.text("down","Код при отпускании PTT (до 30)",d.pttDown,false);
        f.text("responseHold","Удержание ответа, с (шаг 0.1)",String.format(Locale.US,"%.1f",d.responseHoldSeconds),false);
        f.text("decodeTime","Время декодирования, с (шаг 0.1)",String.format(Locale.US,"%.1f",d.decodeTimeSeconds),false);
        f.text("firstDelay","Задержка первого символа, мс (шаг 100)",""+d.firstDigitDelayMs,true);
        f.text("firstDuration","Длительность первого символа, мс (шаг 100)",""+d.firstDigitDurationMs,true);
        f.text("otherDuration","Длительность * и #, мс (шаг 100)",""+d.otherDurationMs,true);
        f.choice("rate","Скорость, символов/с",""+d.rate,numbers(1,10),numbers(1,10));
        f.text("tail","Задержка завершения, мс (шаг 100)",""+d.tailMs,true);
        f.label("Использование функций декодирования и PTT-ID зависит от прошивки.");f.show();
    }
    private static void scan(Activity a,CodeplugModel.ScanList g,CodeplugModel m,Commit commit,CodeplugRecords.Kind creating){
        Form f=recordForm(a,"Список сканирования "+g.index,commit,creating,g.index,v->commit.apply(s->CodeplugLists.scan(s,g.index,v)));
        f.text("name","Имя (до 15 символов)",creating==null?g.name:"",false);
        EditText members=f.text("members","Каналы по порядку (до 32; −1 — текущий)",CodeplugRecords.join(g.channelIndices),false);
        List<Integer> ids=new ArrayList<>();List<String> names=new ArrayList<>();ids.add(-1);names.add("Текущий канал");
        for(CodeplugModel.Channel c:m.channels){ids.add(c.index);names.add(c.name);}
        Button select=new Button(a);select.setText("Выбрать каналы по имени");f.body.addView(select);
        select.setOnClickListener(view->{
            List<Integer> chosen;
            try{chosen=CodeplugLists.members(members.getText().toString(),32,1024,true);}catch(Exception e){new AlertDialog.Builder(a).setMessage(e.getMessage()).setPositiveButton("OK",null).show();return;}
            String[] labels=new String[ids.size()];boolean[] checked=new boolean[ids.size()];
            for(int i=0;i<ids.size();i++){labels[i]=ids.get(i)==-1?names.get(i):"#"+ids.get(i)+" · "+names.get(i);checked[i]=chosen.contains(ids.get(i));}
            new AlertDialog.Builder(a).setTitle("Каналы списка").setMultiChoiceItems(labels,checked,(d,which,on)->{int id=ids.get(which);if(on&&!chosen.contains(id))chosen.add(id);else if(!on)chosen.remove((Integer)id);})
                .setNegativeButton("Отмена",null).setPositiveButton("Готово",(d,w)->members.setText(CodeplugRecords.join(chosen))).show();
        });
        references(f,"primary","Приоритет 1",g.primary,ids,names);references(f,"secondary","Приоритет 2",g.secondary,ids,names);references(f,"revert","Канал ответа",g.revert,ids,names);
        f.text("hold","Задержка сканирования, мс (шаг 25)",""+g.holdMs,true);
        f.text("sample","Интервал проверки приоритета, мс (шаг 250)",""+g.sampleMs,true);
        f.label("Список сохраняется в codeplug. Использование списков сканирования зависит от прошивки; сканирование зон настраивается каналами зоны.");f.show();
    }
}
