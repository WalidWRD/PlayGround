package com.ashnapatch.xposed.hooks;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * استخراج نص الحوار بأمان عبر شجرة الـ Views (يعمل مع AlertDialog الأصلي
 * و AppCompat دون أسماء حقول ثابتة — مقاوم لاختلاف الإصدارات).
 */
final class DialogText {
    private DialogText() {}

    static String extract(Dialog d) {
        try {
            StringBuilder sb = new StringBuilder();
            // المحاولة 1: عناوين الرسائل المعيارية.
            appendIdText(d, android.R.id.message, sb);
            appendIdText(d, android.R.id.title, sb);
            // المحاولة 2: مسح شجرة الديكور كاملة (احتياط للإصدارات المختلفة).
            try {
                android.view.Window w = d.getWindow();
                if (w != null && w.getDecorView() != null) {
                    walk(w.getDecorView(), sb, 0);
                }
            } catch (Throwable ignored) {
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void appendIdText(Dialog d, int id, StringBuilder sb) {
        try {
            View v = d.findViewById(id);
            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null) sb.append(cs).append('\n');
            }
        } catch (Throwable ignored) {
        }
    }

    private static void walk(View v, StringBuilder sb, int depth) {
        try {
            if (v == null || depth > 8) return;
            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null && cs.length() > 0 && sb.length() < 2000) {
                    sb.append(cs).append('\n');
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    walk(g.getChildAt(i), sb, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
