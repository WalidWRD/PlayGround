package com.kakuaudit.observe;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * In-manager description screen: version, target, features.
 * Opened from the module manager ("Open module") so the detailed
 * description lives INSIDE the module itself, not only in docs.
 */
public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            TextView tv = new TextView(this);
            tv.setText(ModuleDescription.full()
                    + "\nالتثبيت بدون روت: LSPatch/NPatch/HKPatch — Local mode ثم تفعيل النطاق على الحزمة الهدف فقط.\n"
                    + "التقارير: Download/KakuAudit/<package>/<version>/<session>/ (أو المسار الاحتياطي الموثق في actualRoot).\n");
            tv.setTextIsSelectable(true);
            tv.setPadding(32, 32, 32, 32);
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            setContentView(sv);
        } catch (Throwable ignore) {
            finish();
        }
    }
}
