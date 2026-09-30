package com.metawebdesigner.gm220rebooter;
import android.content.*;

public final class RebootAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null) return;
        SecurePrefs prefs = new SecurePrefs(c);
        if (!prefs.raw().getBoolean("enabled", false) || i.getLongExtra("generation", -1) != prefs.raw().getLong("generation", 0)) return;
        boolean retry = AlarmScheduler.ACTION_RETRY.equals(i.getAction());
        if (!retry && !AlarmScheduler.ACTION_DAILY.equals(i.getAction())) return;
        int retryCount = retry ? i.getIntExtra("retry_count", 0) : 0;
        AlarmScheduler.cancelRetry(c);
        if (!retry) AlarmScheduler.schedule(c);
        Automation.enqueueScheduled(c, retryCount);
    }
}
