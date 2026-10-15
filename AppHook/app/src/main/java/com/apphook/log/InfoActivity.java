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
                "AppHook v4.2.0 (code 420) — WebView Body Patch\n"
                + "الحزمة: com.apphook.log\n"
                + "النطاق: ai.mainfunc.genspark (+ .test)\n"
                + "أندرويد: 8.0 (API 26) حتى 15 (API 35)\n"
                + "libxposed: 101 -> 102\n"
                + "الدخول: MainHook (حديث) + LegacyHook (قديم)\n\n"
                + ModuleInfo.describe() + "\n\n"
                + "الميزات:\n"
                + "1) fetch: تعديل /api/* الستة (Response مرقعة)\n"
                + "2) XHR: تعديل responseText/response\n"
                + "3) Bridge: تعديل postMessage قبل الإرسال\n"
                + "4) 25+ قاعدة JSON (اشتراك/خطة/حالة/trial)\n"
                + "5) حقن مبكر Started + احتياطي Finished\n"
                + "6) Console: الردود الكاملة ORIG[...]\n"
                + "7) FlutterChannel/Prefs/JSON: احتياطي\n"
                + "8) Scanner: اكتشاف الفئات بعد R8\n\n"
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
