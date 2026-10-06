package com.kakuaudit.observe.report;

/** HTML-escape all untrusted text; never execute captured content (spec §10.4). */
public final class HtmlEscaper {
    private HtmlEscaper() {}

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder o = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': o.append("&amp;"); break;
                case '<': o.append("&lt;"); break;
                case '>': o.append("&gt;"); break;
                case '"': o.append("&quot;"); break;
                case '\'': o.append("&#x27;"); break;
                case '/': o.append("&#x2F;"); break;
                default: o.append(c);
            }
        }
        return o.toString();
    }
}
