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
                "AppHook v3.1.0 (code 310) — Flutter-Filtered\n"
                + "الحزمة: com.apphook.log\n"
                + "النطاق: ai.mainfunc.genspark (+ .test)\n"
                + "أندرويد: 8.0 (API 26) حتى 15 (API 35)\n"
                + "libxposed: 101 -> 102\n"
                + "الدخول: MainHook (حديث) + LegacyHook (قديم)\n\n"
                + ModuleInfo.describe() + "\n\n"
                + "الميزات:\n"
                + "1) FlutterChannel: اعتراض + فلترة ضجيج WebView\n"
                + "2) WebView: جسور JS + اعتراض URLs الدفع (مراقبة)\n"
                + "3) AppsFlyer: رصد أحداث الشراء (مراقبة)\n"
                + "4) UrlConnection: جانب Dart HTTP (مراقبة)\n"
                + "5) Prefs: getBoolean->true / getInt 0->1\n"
                + "6) JSON: ترقيع Gson/org.json\n"
                + "7) Billing/RevenueCat: احتياطي native\n"
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
