package ru.opengd77.satupdate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.Button;

public class CodeplugReadButton extends Button {
    public CodeplugReadButton(Context context) { super(context); init(); }
    public CodeplugReadButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CodeplugReadButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }
    private void init() {
        setOnClickListener(v -> {
            try {
                if (CodeplugSession.project == null) CodeplugSession.install(CodeplugProjectStore.load(getContext()));
                if (CodeplugSession.project != null && CodeplugSession.project.changedBytes()>0) {
                    new AlertDialog.Builder(getContext()).setTitle("Прочитать заново?")
                            .setMessage("Новое чтение заменит рабочий проект. Сохраните копию правок через меню «Проект», если они нужны.")
                            .setNegativeButton("Отмена",null).setPositiveButton("Прочитать",(d,w)->startRead()).show();
                } else startRead();
            } catch (Exception e) {
                new AlertDialog.Builder(getContext()).setTitle("Не удалось открыть сохранённый проект")
                        .setMessage(e.getMessage()).setNegativeButton("Отмена",null)
                        .setPositiveButton("Прочитать заново",(d,w)->startRead()).show();
            }
        });
    }
    private void startRead() {
        Context c=getContext();c.startActivity(new Intent(c,CodeplugReadActivity.class));
        if(c instanceof Activity)((Activity)c).finish();
    }
}
