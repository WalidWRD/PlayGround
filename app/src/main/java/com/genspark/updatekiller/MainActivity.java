package com.genspark.updatekiller;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * v2.0.3: the module finally has a launcher UI.
 * v2.0.0 shipped with NO activity, so tapping the app icon did nothing
 * and users reported "the App is broken". This activity shows status and
 * lets the user write the JSON config to a readable location.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            int layout = getResources().getIdentifier("activity_main", "layout", getPackageName());
            setContentView(layout);
        } catch (Throwable t) {
            TextView tv = new TextView(this);
            tv.setText("Genspark UpdateKiller v2.0.3\nEnable in LSPosed scope: Genspark AI.");
            setContentView(tv);
            return;
        }
        try {
            int titleId = getResources().getIdentifier("title", "id", getPackageName());
            int statusId = getResources().getIdentifier("status", "id", getPackageName());
            int configId = getResources().getIdentifier("config", "id", getPackageName());
            int saveId = getResources().getIdentifier("btnSave", "id", getPackageName());
            int loadId = getResources().getIdentifier("btnLoad", "id", getPackageName());
            TextView status = findViewById(statusId);
            EditText config = findViewById(configId);
            status.setText("Status: " + BuildInfo.VERSION_LABEL
                    + "\nTarget: " + BuildInfo.TARGET_PKG_PRO + " / " + BuildInfo.TARGET_PKG_FREE
                    + "\nScope: enable in LSPosed for Genspark AI");
            Button save = findViewById(saveId);
            Button load = findViewById(loadId);
            save.setOnClickListener(v -> {
                try {
                    String text = config.getText().toString();
                    // Validate JSON first.
                    new org.json.JSONObject(text);
                    java.io.FileWriter w = new java.io.FileWriter(new File(getFilesDir(), "genspark_updatekiller.json"));
                    w.write(text);
                    w.close();
                    Toast.makeText(this, "Saved to internal files. Copy to /data/local/tmp/ if rooted.", Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Toast.makeText(this, "Invalid JSON: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            load.setOnClickListener(v -> {
                try {
                    File f = new File(getFilesDir(), "genspark_updatekiller.json");
                    if (f.exists()) {
                        byte[] b = new byte[(int) f.length()];
                        java.io.FileInputStream in = new java.io.FileInputStream(f);
                        int n = in.read(b);
                        in.close();
                        config.setText(new String(b, 0, Math.max(n, 0), "UTF-8"));
                    } else {
                        config.setText("{\"enabled\":true,\"forceSubscribed\":true,\"killSwitchThreshold\":20}");
                    }
                } catch (Throwable t) {
                    Toast.makeText(this, "Load failed: " + t, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Throwable t) {
            Toast.makeText(this, "UI init failed: " + t, Toast.LENGTH_LONG).show();
        }
    }
}
