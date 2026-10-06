package com.kakuaudit.observe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Early, rootless init hook. ContentProviders start before Application.onCreate,
 * which gives LSPatch/NPatch local-mode (no-root app) a reliable entry even in
 * packed apps where Application is wrapped. Does nothing but warm the clock;
 * real hooking happens in the Xposed entry.
 */
public final class KakuAuditInitProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] sa) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
}
