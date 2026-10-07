package ru.opengd77.satupdate;

import android.app.*;
import android.widget.*;
import java.util.*;

/** A readable list with per-record details, shared by compare, recovery and write preview. */
final class ProjectReportDialogs {
    static void show(Activity activity,String title,String context,ProjectDiff diff,Runnable apply){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,0,pad,0);
        TextView summary=new TextView(activity);summary.setText(context+"\nИзменений записей/настроек: "+diff.changes.size()+" • байтов: "+diff.bytes);body.addView(summary);
        ListView list=new ListView(activity);int height=(int)Math.min(activity.getResources().getDisplayMetrics().density*360,activity.getResources().getDisplayMetrics().heightPixels*0.45f);
        body.addView(list,new LinearLayout.LayoutParams(-1,height));List<String> rows=new ArrayList<>();
        for(ProjectDiff.Change change:diff.changes)rows.add(change.title);
        if(rows.isEmpty())rows.add("Различий нет");list.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,rows));
        list.setOnItemClickListener((parent,view,position,id)->{if(position>=diff.changes.size())return;ProjectDiff.Change change=diff.changes.get(position);
            new AlertDialog.Builder(activity).setTitle(change.title).setMessage(change.detail).setPositiveButton("Закрыть",null).show();});
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle(title).setView(body);
        if(apply==null)dialog.setPositiveButton("Закрыть",null);
        else dialog.setNegativeButton("Отмена",null).setPositiveButton("Применить к проекту",(d,w)->apply.run());
        dialog.show();
    }
}
