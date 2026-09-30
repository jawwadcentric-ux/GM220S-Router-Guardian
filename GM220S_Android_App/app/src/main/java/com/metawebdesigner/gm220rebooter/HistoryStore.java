package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded, local event history. Callers supply fixed messages, never router responses. */
public final class HistoryStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public HistoryStore(Context c) { prefs = c.getSharedPreferences("guardian_history", Context.MODE_PRIVATE); }
    public JSONArray all() {
        synchronized (LOCK) {
            try { return new JSONArray(prefs.getString("events", "[]")); }
            catch (Exception ignored) { return new JSONArray(); }
        }
    }
    public void add(String source, String reason, String result, boolean success) {
        synchronized (LOCK) {
            try {
                JSONArray old = all(), next = new JSONArray();
                next.put(new JSONObject().put("time", System.currentTimeMillis()).put("source", displaySource(source))
                    .put("reason", reason).put("result", result).put("success", success));
                for (int i = 0; i < Math.min(199, old.length()); i++) next.put(old.get(i));
                prefs.edit().putString("events", next.toString()).commit();
            } catch (Exception ignored) { /* History must not break router controls. */ }
        }
    }
    public void clear() { synchronized (LOCK) { prefs.edit().remove("events").commit(); } }
    public String export(boolean csv) {
        StringBuilder out = new StringBuilder(csv ? "Date,Source,Reason,Success,Result\r\n" : "GM220-S Guardian history\n\n");
        JSONArray events = all();
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String[] columns = {time(e.optLong("time")), e.optString("source"), e.optString("reason"),
                e.optBoolean("success") ? "Yes" : "No", e.optString("result")};
            for (int j = 0; j < columns.length; j++) {
                if (j > 0) out.append(csv ? "," : " | ");
                if (csv) out.append('"').append(columns[j].replace("\"", "\"\"")).append('"');
                else out.append(columns[j]);
            }
            out.append(csv ? "\r\n" : "\n\n");
        }
        return out.toString();
    }
    public static String time(long time) {
        return time == 0 ? "Not yet" : new SimpleDateFormat("dd MMM yyyy • HH:mm:ss Z", Locale.getDefault()).format(new Date(time));
    }
    private static String displaySource(String source) {
        if (source == null || source.isEmpty()) return "System";
        return source.substring(0, 1).toUpperCase(Locale.ROOT) + source.substring(1).toLowerCase(Locale.ROOT);
    }
}
