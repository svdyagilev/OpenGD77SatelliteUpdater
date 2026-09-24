package ru.opengd77.satupdate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.Button;

/** Starts the isolated v0.5 read-only CPS session. */
public class CodeplugReadButton extends Button {
    public CodeplugReadButton(Context context) { super(context); init(); }
    public CodeplugReadButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CodeplugReadButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setOnClickListener(v -> {
            Context c = getContext();
            Intent i = new Intent(c, CodeplugReadActivity.class);
            c.startActivity(i);
            // MainActivity owns the Satellite module USB session. Closing it before the
            // dedicated codeplug reader starts prevents two activities claiming one CDC port.
            if (c instanceof Activity) ((Activity)c).finish();
        });
    }
}
