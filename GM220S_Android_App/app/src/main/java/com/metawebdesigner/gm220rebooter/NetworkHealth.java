package com.metawebdesigner.gm220rebooter;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

public final class NetworkHealth {
    private static final ExecutorService DNS = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private NetworkHealth() {}
    public static Network wifi(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        // Never choose a secondary Wi-Fi while the phone is using cellular or a VPN.
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? active : null;
    }
    public static String transport(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return "No network";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN active • automation paused";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi connected";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile data • automation paused";
        return "Not connected to Wi-Fi";
    }
    @SuppressWarnings("deprecation")
    public static String ssid(Context c) {
        try {
            WifiManager wm = (WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String value = wm.getConnectionInfo().getSSID();
            if (value == null || value.equals("<unknown ssid>")) return "";
            return value.replaceAll("^\"|\"$", "");
        } catch (Exception ignored) { return ""; }
    }
    public static boolean allowed(Context c, SecurePrefs prefs) {
        if (wifi(c) == null) return false;
        String bound = prefs.raw().getString("bound_ssid", "");
        return bound.isEmpty() || bound.equals(ssid(c));
    }
    public static boolean reachable(Network network, String base) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)network.openConnection(new URL(base + "/"));
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            return status >= 200 && status < 500;
        } catch (Exception ignored) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }
    public static boolean internet(Context c, Network network) {
        if (network == null) return false;
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return false;
        // Two independent providers; DNS and HTTPS must succeed on this Wi-Fi.
        String[] urls = {"https://www.google.com/generate_204", "https://cp.cloudflare.com/generate_204"};
        for (String endpoint : urls) {
            HttpURLConnection connection = null;
            Future<?> dns = null;
            try {
                URL url = new URL(endpoint);
                dns = DNS.submit(() -> { try { return network.getAllByName(url.getHost()); } catch (Exception e) { throw new RuntimeException(); } });
                dns.get(4, TimeUnit.SECONDS);
                connection = (HttpURLConnection)network.openConnection(url);
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() == 204) return true;
            } catch (Exception ignored) {
            } finally {
                if (dns != null) dns.cancel(true);
                if (connection != null) connection.disconnect();
            }
        }
        // A recent OS validation is positive evidence; a failed probe alone must not reboot.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
