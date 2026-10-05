package com.ashnapatch.xposed;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * شاشة تحكم الموديول v2 — مفاتيح حيّة + قوائم مخصصة + حالة تشخيصية.
 * واجهة برمجية خالصة (بلا XML/مكتبات) لتبقى خفيفة ومتوافقة minSdk 24.
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            final SharedPreferences prefs = getSharedPreferences(
                    PrefsManager.PREFS_FILE, MODE_PRIVATE);

            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);
            scroll.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView title = new TextView(this);
            title.setText(ModuleInfo.MODULE_NAME + " v" + ModuleInfo.MODULE_VERSION
                    + "\n" + ModuleInfo.TARGET_APP_NAME
                    + " (" + ModuleInfo.TARGET_PACKAGE + ")");
            title.setTextSize(18);
            root.addView(title);

            TextView hint = new TextView(this);
            hint.setText("المفاتيح والقوائم تُطبَّق حيّاً (أعد فتح التطبيق الهدف). "
                    + "القائمة البيضاء تحمي الدخول/OTP/الدفع دائماً ولا يمكن تجاوزها. "
                    + "التشخيص الشبكي يسجّل المضيف+المسار فقط (بلا query) ويتجاهل مسارات المصادقة/الدفع.");
            hint.setTextSize(13);
            root.addView(hint);

            addSwitch(root, prefs, PrefsManager.KEY_UPDATE,
                    "تعطيل التحديث الإجباري", Config.ENABLE_UPDATE_BLOCK);
            addSwitch(root, prefs, PrefsManager.KEY_WARNING,
                    "كتم التحذيرات المزعجة", Config.ENABLE_WARNING_FILTER);
            addSwitch(root, prefs, PrefsManager.KEY_AD,
                    "حجب الإعلانات", Config.ENABLE_AD_BLOCK);
            addSwitch(root, prefs, PrefsManager.KEY_JS,
                    "إخفاء عناصر الويب (JS)", Config.ENABLE_JS_HIDE);
            addSwitch(root, prefs, PrefsManager.KEY_NETDIAG,
                    "التشخيص الشبكي (سجل قراءة فقط)", false);

            TextView hostsLabel = new TextView(this);
            hostsLabel.setText("\nنطاقات إعلانات مخصصة (افصل بفاصلة أو سطر جديد):");
            root.addView(hostsLabel);
            final EditText hostsEdit = new EditText(this);
            hostsEdit.setMinLines(2);
            try {
                hostsEdit.setText(prefs.getString(PrefsManager.KEY_CUSTOM_HOSTS, ""));
                hostsEdit.setHint("مثال: ads.example.com, promo.example.net");
            } catch (Throwable ignored) {
            }
            root.addView(hostsEdit);

            TextView warnLabel = new TextView(this);
            warnLabel.setText("\nكلمات تحذير مخصصة (افصل بفاصلة أو سطر جديد):");
            root.addView(warnLabel);
            final EditText warnEdit = new EditText(this);
            warnEdit.setMinLines(2);
            try {
                warnEdit.setText(prefs.getString(PrefsManager.KEY_CUSTOM_WARN, ""));
                warnEdit.setHint("مثال: عرض خاص, spin bonus");
            } catch (Throwable ignored) {
            }
            root.addView(warnEdit);

            Button save = new Button(this);
            save.setText("حفظ القوائم المخصصة");
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String h = "";
                        String w = "";
                        try {
                            h = String.valueOf(hostsEdit.getText());
                        } catch (Throwable ignored) {
                        }
                        try {
                            w = String.valueOf(warnEdit.getText());
                        } catch (Throwable ignored) {
                        }
                        // تقليم دفاعي: حد أقصى 4000 حرف لكل حقل.
                        if (h != null && h.length() > 4000) h = h.substring(0, 4000);
                        if (w != null && w.length() > 4000) w = w.substring(0, 4000);
                        prefs.edit()
                                .putString(PrefsManager.KEY_CUSTOM_HOSTS, h == null ? "" : h)
                                .putString(PrefsManager.KEY_CUSTOM_WARN, w == null ? "" : w)
                                .apply();
                        makeWorldReadable();
                        try {
                            Toast.makeText(SettingsActivity.this, "تم الحفظ", Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) {
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            root.addView(save);

            // بطاقة حالة الاشتراك — عرض محلي فقط (تذكير يدوي + زر فتح التطبيق).
            TextView subTitle = new TextView(this);
            subTitle.setText("\nحالة الاشتراك (عرض محلي فقط — لا يقرأ الخادم ولا يعدّله):");
            subTitle.setTextSize(15);
            root.addView(subTitle);

            final EditText planEdit = new EditText(this);
            planEdit.setSingleLine(true);
            try {
                planEdit.setText(prefs.getString(PrefsManager.KEY_SUB_PLAN, ""));
                planEdit.setHint("اسم الخطة (مثال: شهري)");
            } catch (Throwable ignored) {
            }
            root.addView(planEdit);

            final EditText expiryEdit = new EditText(this);
            expiryEdit.setSingleLine(true);
            try {
                expiryEdit.setText(prefs.getString(PrefsManager.KEY_SUB_EXPIRY, ""));
                expiryEdit.setHint("تاريخ الانتهاء YYYY-MM-DD");
            } catch (Throwable ignored) {
            }
            root.addView(expiryEdit);

            final TextView subStatus = new TextView(this);
            subStatus.setTextSize(13);
            try {
                subStatus.setText(PrefsManager.subStatusLine()
                        + "\nتنبيه: تذكير محلي على جهازك فقط — لإدارة اشتراكك استخدم التطبيق الرسمي.");
            } catch (Throwable ignored) {
            }
            root.addView(subStatus);

            Button saveSub = new Button(this);
            saveSub.setText("حفظ تذكير الاشتراك");
            saveSub.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String p = "";
                        String e = "";
                        try {
                            p = String.valueOf(planEdit.getText()).trim();
                        } catch (Throwable ignored) {
                        }
                        try {
                            e = String.valueOf(expiryEdit.getText()).trim();
                        } catch (Throwable ignored) {
                        }
                        if (p != null && p.length() > 60) p = p.substring(0, 60);
                        if (e != null && e.length() > 10) e = e.substring(0, 10);
                        prefs.edit()
                                .putString(PrefsManager.KEY_SUB_PLAN, p == null ? "" : p)
                                .putString(PrefsManager.KEY_SUB_EXPIRY, e == null ? "" : e)
                                .apply();
                        makeWorldReadable();
                        try {
                            subStatus.setText(PrefsManager.subStatusLine()
                                    + "\nتنبيه: تذكير محلي على جهازك فقط — لإدارة اشتراكك استخدم التطبيق الرسمي.");
                        } catch (Throwable ignored) {
                        }
                        try {
                            Toast.makeText(SettingsActivity.this, "تم حفظ التذكير", Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) {
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            root.addView(saveSub);

            Button openApp = new Button(this);
            openApp.setText("فتح تطبيق AshnaAI (إدارة الاشتراك الرسمية)");
            openApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        android.content.Intent i = getPackageManager()
                                .getLaunchIntentForPackage(ModuleInfo.TARGET_PACKAGE);
                        if (i != null) {
                            startActivity(i);
                        } else {
                            Toast.makeText(SettingsActivity.this,
                                    "تطبيق AshnaAI غير مثبّت", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable t) {
                        try {
                            Toast.makeText(SettingsActivity.this,
                                    "تعذّر فتح التطبيق", Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
            root.addView(openApp);

            TextView status = new TextView(this);
            try {
                java.util.Set<String> hosts = PrefsManager.parseList(
                        prefs.getString(PrefsManager.KEY_CUSTOM_HOSTS, ""));
                java.util.Set<String> warns = PrefsManager.parseList(
                        prefs.getString(PrefsManager.KEY_CUSTOM_WARN, ""));
                status.setText("\nالحالة: مخصص hosts=" + hosts.size()
                        + " warn=" + warns.size()
                        + "\n" + ModuleInfo.SCOPE_NOTE);
            } catch (Throwable t) {
                status.setText("\n" + ModuleInfo.SCOPE_NOTE);
            }
            status.setTextSize(12);
            root.addView(status);

            setContentView(scroll);
        } catch (Throwable t) {
            finish();
        }
    }

    private void addSwitch(LinearLayout root, final SharedPreferences prefs,
                           final String key, String label, boolean def) {
        try {
            Switch sw = new Switch(this);
            sw.setText(label);
            boolean cur;
            try {
                cur = prefs.getBoolean(key, def);
            } catch (Throwable t) {
                cur = def;
            }
            sw.setChecked(cur);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    try {
                        prefs.edit().putBoolean(key, isChecked).apply();
                        makeWorldReadable();
                    } catch (Throwable ignored) {
                    }
                }
            });
            root.addView(sw);
        } catch (Throwable ignored) {
        }
    }

    /** ضروري لقراءة XSharedPreferences من عملية التطبيق الهدف. */
    private void makeWorldReadable() {
        try {
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            File f = new File(prefsDir, PrefsManager.PREFS_FILE + ".xml");
            try {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            } catch (Throwable ignored) {
            }
            try {
                if (f.exists()) f.setReadable(true, false);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }
}
