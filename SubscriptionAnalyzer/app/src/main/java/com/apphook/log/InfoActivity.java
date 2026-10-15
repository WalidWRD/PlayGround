package com.apphook.log;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** شاشة معلومات الموديول — الاسم/الإصدار/الوضع/التوافق. */
public final class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this);
        tv.setText(
                "SubscriptionAnalyzer v6.0.0 (code 600)\n"
                + "الحزمة: com.apphook.log\n"
                + "الوضع: " + Config.modeName()
                + " (يُبدّل من Config.java وقت البناء)\n"
                + "أندرويد: 8.0 (API 26) حتى 15 (API 35)\n"
                + "libxposed: 101 -> 102\n\n"
                + ModuleInfo.describe() + "\n\n"
                + "الطبقات (تسجيل دائم + ترقيع اختياري):\n"
                + "Billing / RevenueCat / FlutterChannel / WebView /\n"
                + "Prefs(+JSON) / Json / Network / Integrity / UpdateGate\n\n"
                + "التقرير: /sdcard/SubscriptionAnalyzer/report_*.json\n"
                + "(احتياطي: /data/data/<pkg>/cache/SubscriptionAnalyzer/)\n");
        tv.setTextSize(14);
        tv.setPadding(32, 32, 32, 32);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);
    }
}
