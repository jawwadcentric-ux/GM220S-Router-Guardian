package com.metawebdesigner.gm220rebooter;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class HistoryActivity extends GuardianActivity {
    @Override protected void onCreate(Bundle state) { super.onCreate(state); render(); }
    private void render() {
        page("Restart history", "Latest 200 events • stored only on this phone");
        navigation(TAB_HISTORY);
        LinearLayout controls = card("Your activity log");
        button(controls, "Share CSV", false, () -> share(true));
        button(controls, "Share plain text", false, () -> share(false));
        button(controls, "Clear history", false, () -> new AlertDialog.Builder(this).setTitle("Clear history?")
            .setMessage("This removes the displayed history. Safety limits remain in effect.")
            .setNegativeButton("Cancel", null).setPositiveButton("Clear", (d,w) -> { new HistoryStore(this).clear(); render(); }).show());
        JSONArray events = new HistoryStore(this).all();
        if (events.length() == 0) detail(controls, "No restarts yet. Your manual, scheduled and watchdog activity will appear here.");
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            LinearLayout row = card((event.optBoolean("success") ? "✓  " : "!  ") + event.optString("source") + " • " + HistoryStore.time(event.optLong("time")));
            detail(row, event.optString("reason") + "\n" + event.optString("result"));
        }
    }
    private void share(boolean csv) {
        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException();
            File file = new File(dir, csv ? "guardian-history.csv" : "guardian-history.txt");
            try (OutputStream stream = new FileOutputStream(file)) { stream.write(new HistoryStore(this).export(csv).getBytes(StandardCharsets.UTF_8)); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType(csv ? "text/csv" : "text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Guardian history", uri));
            startActivity(Intent.createChooser(intent, "Share history"));
        } catch (Exception ignored) { message("Unable to share history on this phone"); }
    }
}
