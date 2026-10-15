package com.apphook.log;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * شاشة معلومات الموديول (تُفتح من اللانشر) — تعرض الاسم/الإصدار/التوافق.
 */
public final class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this);
        tv.setText(
                "AppHook v5.1.0 (code 510) — NUXT+DOM Final\n"
                + "الحزمة: com.apphook.log\n"
                + "النطاق: ai.mainfunc.genspark (+ .test)\n"
                + "أندرويد: 8.0 (API 26) حتى 15 (API 35)\n"
                + "libxposed: 101 -> 102\n"
                + "الدخول: MainHook (حديث) + LegacyHook (قديم)\n\n"
                + ModuleInfo.describe() + "\n\n"
                + "الميزات:\n"
                + "1) WebView: حقن patchJson (fetch/XHR/bridge) + نشر console\n"
                + "2) Balance: 0→999999 + subType/membership→plus/active\n"
                + "3) Gson input + JSONObject getInt/getString (نفس القواعد)\n"
                + "4) ShapeHook: ربط بالبصمة لا بالاسم الصريح\n"
                + "5) FlutterChannel/Prefs/Billing: دفاع إضافي\n"
                + "6) Scanner: اكتشاف الفئات بعد R8\n\n"
                + "التثبيت بلا روت:\n"
                + "- LSPatch: دمج APK التطبيق + نطاق AppHook\n"
                + "- NPatch / HKP: نفس الخطوات، فعّل النطاق ثم ثبّت\n"
                + "- LSPosed (روت): فعّل + حدد النطاق + أعد التشغيل\n");
        tv.setTextSize(14);
        tv.setPadding(32, 32, 32, 32);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);
    }
}
