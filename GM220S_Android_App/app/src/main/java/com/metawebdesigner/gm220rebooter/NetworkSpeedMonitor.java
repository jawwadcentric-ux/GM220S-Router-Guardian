package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Samples Android's device traffic counters while the dashboard is visible. */
public final class NetworkSpeedMonitor {
    public interface Listener { void onSample(Sample sample); }

    public static final class Sample {
        public final long downBytesPerSecond, upBytesPerSecond, receivedBytes, sentBytes;
        public final double averageDownBytesPerSecond, averageUpBytesPerSecond;
        public final String connection, networkName;
        public final boolean available;

        Sample(long down, long up, long received, long sent, double averageDown, double averageUp,
               String connection, String networkName, boolean available) {
            this.downBytesPerSecond = down;
            this.upBytesPerSecond = up;
            this.receivedBytes = received;
            this.sentBytes = sent;
            this.averageDownBytesPerSecond = averageDown;
            this.averageUpBytesPerSecond = averageUp;
            this.connection = connection;
            this.networkName = networkName;
            this.available = available;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long intervalMs;
    private long firstRx, firstTx, lastRx, lastTx, firstTime, lastTime;
    private boolean running;

    public NetworkSpeedMonitor(Context context, long intervalMs, Listener listener) {
        this.context = context.getApplicationContext();
        this.intervalMs = Math.max(1000L, intervalMs);
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        firstRx = lastRx = TrafficStats.getTotalRxBytes();
        firstTx = lastTx = TrafficStats.getTotalTxBytes();
        firstTime = lastTime = SystemClock.elapsedRealtime();
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();
            boolean counters = rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED;
            long elapsed = Math.max(1L, now - lastTime);
            long totalElapsed = Math.max(1L, now - firstTime);
            long down = counters ? Math.max(0L, rx - lastRx) * 1000L / elapsed : 0L;
            long up = counters ? Math.max(0L, tx - lastTx) * 1000L / elapsed : 0L;
            long received = counters ? Math.max(0L, rx - firstRx) : 0L;
            long sent = counters ? Math.max(0L, tx - firstTx) : 0L;
            NetworkInfo info = networkInfo();
            listener.onSample(new Sample(down, up, received, sent,
                received * 1000.0 / totalElapsed, sent * 1000.0 / totalElapsed,
                info.connection, info.name, counters && info.connected));
            lastRx = rx; lastTx = tx; lastTime = now;
            handler.postDelayed(this, intervalMs);
        }
    };

    private NetworkInfo networkInfo() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active = manager.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : manager.getNetworkCapabilities(active);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            return new NetworkInfo("Offline", "No active network", false);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            String ssid = NetworkHealth.ssid(context);
            return new NetworkInfo("Wi-Fi", ssid.isEmpty() ? "Wi-Fi network" : ssid, true);
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            return new NetworkInfo("Mobile data", "Mobile data", true);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            return new NetworkInfo("Ethernet", "Wired network", true);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            return new NetworkInfo("VPN", "Active VPN", true);
        return new NetworkInfo("Connected", "Active network", true);
    }

    private static final class NetworkInfo {
        final String connection, name; final boolean connected;
        NetworkInfo(String connection, String name, boolean connected) {
            this.connection = connection; this.name = name; this.connected = connected;
        }
    }
}
