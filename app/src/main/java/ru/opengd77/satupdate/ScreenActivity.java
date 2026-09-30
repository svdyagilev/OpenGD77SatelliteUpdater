package ru.opengd77.satupdate;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Keeps every screen inside the usable area, including on Android 15+. */
public abstract class ScreenActivity extends Activity {
    private View insetContent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) {
            // We own the insets on modern Android; the decor must not add them too.
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            int mode = getWindow().getAttributes().softInputMode;
            getWindow().setSoftInputMode((mode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            getWindow().getDecorView();
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(appearance, appearance);
            }
        }
        // Android 6–10 retain the framework's fitted window and keyboard resizing.
    }

    @Override public void onContentChanged() {
        super.onContentChanged();
        if (Build.VERSION.SDK_INT < 30) return;
        View content = findViewById(android.R.id.content);
        if (content == null || content == insetContent) return;
        insetContent = content;
        // Insets go on the outer container, preserving each screen's own padding.
        content.setBackgroundColor(Color.WHITE);
        content.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            // Absolute values prevent padding from accumulating on each dispatch.
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsets.CONSUMED;
        });
        content.requestApplyInsets();
    }
}
