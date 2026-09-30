package com.metawebdesigner.gm220rebooter;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Local HTTP fixture. Never connects to real router hardware. */
public final class ProtocolBaselineTest {
    public static void main(String[] args) throws Exception {
        AtomicReference<String> error = new AtomicReference<>();
        AtomicInteger reboots = new AtomicInteger();
        AtomicReference<String> tokenPage = new AtomicReference<>("<script>var session_token = 'dynamic_987654321';</script>");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String response = "";
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                require(cookie != null && cookie.contains("_TESTCOOKIESUPPORT=1"), "Cookie support missing");
                if (path.equals("/") && method.equals("GET")) {
                    exchange.getResponseHeaders().add("Set-Cookie", "sid=before; Path=/");
                    response = "<input name='Frm_Logintoken' value='5731'>";
                } else if (path.equals("/") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    require(body.equals("frashnum=&action=login&Frm_Logintoken=5731&username=fixture&Password=fixture%2Bpassword"), "Login form changed");
                    require(cookie.contains("sid=before"), "Initial session missing");
                    require(base.equals(exchange.getRequestHeaders().getFirst("Origin")), "Login origin changed");
                    exchange.getResponseHeaders().add("Set-Cookie", "sid=after; Path=/");
                    exchange.getResponseHeaders().add("Location", "/start.ghtml");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                } else if (path.equals("/start.ghtml")) {
                    require(cookie.contains("sid=after") && !cookie.contains("sid=before"), "Cookie merge changed");
                    response = "<a>Logout</a>";
                } else if (path.equals("/template.gch")) {
                    require("pid=1002&nextpage=manager_dev_conf_t.gch".equals(exchange.getRequestURI().getQuery()), "Token endpoint changed");
                    require(cookie.contains("sid=after"), "Authenticated session missing");
                    response = tokenPage.get();
                } else if (method.equals("GET") && (path.equals("/getpage.gch") || path.equals("/top.gch") || path.equals("/keepAlive.gch"))) {
                    response = "No token here";
                } else if (path.equals("/getpage.gch") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    require(body.equals("IF_ACTION=devrestart&IF_ERRORSTR=SUCC&IF_ERRORPARAM=SUCC&IF_ERRORTYPE=-1281035768&flag=1&_SESSION_TOKEN=dynamic_987654321"), "Reboot form changed");
                    require(cookie.contains("sid=after"), "Reboot session missing");
                    require((base + "/template.gch?pid=1002&nextpage=manager_dev_conf_t.gch").equals(exchange.getRequestHeaders().getFirst("Referer")), "Reboot referer changed");
                    reboots.incrementAndGet();
                    response = "Restarting";
                } else {
                    throw new AssertionError("Unexpected endpoint");
                }
            } catch (Throwable failure) {
                error.compareAndSet(null, failure.getMessage());
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            RouterClient.Result tested = RouterClient.test(base, "fixture", "fixture+password");
            require(error.get() == null, error.get());
            require(tested.ok, "Test Connection failed: " + tested.message);
            require(reboots.get() == 0, "Test Connection rebooted");
            require(RouterClient.reboot(base, "fixture", "fixture+password").ok, "Reboot failed");
            require(reboots.get() == 1, "Expected exactly one reboot POST");
            tokenPage.set("<input name='_SESSION_TOKEN' value='dynamic_987654321'>");
            require(RouterClient.test(base, "fixture", "fixture+password").ok, "Hidden token failed");
            tokenPage.set("<input value='dynamic_987654321' id='_SESSION_TOKEN'>");
            require(RouterClient.test(base, "fixture", "fixture+password").ok, "Reversed attribute token failed");
            tokenPage.set("No session token");
            require(!RouterClient.test(base, "fixture", "fixture+password").ok, "Missing token accepted by Test Connection");
            require(!RouterClient.reboot(base, "fixture", "fixture+password").ok, "Missing token accepted by reboot");
            require(reboots.get() == 1, "Missing token caused a reboot POST");
            require(error.get() == null, error.get());
            System.out.println("PASS: login, redirect, cookie replacement, dynamic token, Test Connection and exact reboot payload");
        } finally {
            server.stop(0);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
