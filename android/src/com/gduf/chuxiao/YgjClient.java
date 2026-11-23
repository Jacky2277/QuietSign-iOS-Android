package com.gduf.chuxiao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 易广金会话：登录 + 设备校验，供签到页复用。 */
class YgjClient {

    static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 yiban_iOS/5.2.5(5104)";
    static final String BASE = "https://ygj.gduf.edu.cn";

    final Map<String, String> cookies = new LinkedHashMap<>();
    Map<String, String> gh;
    String studentID = "";
    String lastError = "";

    static class Resp {
        int code;
        String body = "";
        String location = null;
    }

    String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    void captureCookies(HttpURLConnection c) {
        Map<String, List<String>> hf = c.getHeaderFields();
        for (Map.Entry<String, List<String>> e : hf.entrySet()) {
            if (e.getKey() == null || !"set-cookie".equalsIgnoreCase(e.getKey())) continue;
            for (String line : e.getValue()) {
                if (line == null) continue;
                String kv = line.split(";", 2)[0];
                int i = kv.indexOf('=');
                if (i > 0) cookies.put(kv.substring(0, i).trim(), kv.substring(i + 1).trim());
            }
        }
    }

    static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    Resp req(String method, String url, String body, Map<String, String> extra) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/json,application/xhtml+xml,*/*");
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        String cookie = (extra != null && extra.containsKey("Cookie")) ? extra.get("Cookie") : cookieHeader();
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (!"Cookie".equals(e.getKey()) && e.getValue() != null) c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if (body != null) {
            c.setDoOutput(true);
            byte[] bb = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bb.length);
            if (c.getRequestProperty("Content-Type") == null)
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = c.getOutputStream();
            os.write(bb);
            os.close();
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        captureCookies(c);
        r.location = c.getHeaderField("Location");
        try {
            r.body = read(r.code >= 400 ? c.getErrorStream() : c.getInputStream());
        } catch (Exception ignored) { }
        c.disconnect();
        return r;
    }

    static String form(Map<String, String> m) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
              .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static String param(String url, String key) {
        try {
            int q = url.indexOf('?');
            if (q < 0) return null;
            for (String kv : url.substring(q + 1).split("&")) {
                int i = kv.indexOf('=');
                if (i > 0 && key.equals(kv.substring(0, i)))
                    return java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8");
            }
        } catch (Exception ignored) { }
        return null;
    }

    String fetchVerify(String tok) throws Exception {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + tok);
        h.put("loginToken", tok);
        h.put("AppVersion", "5.2.5");
        h.put("Cookie", "client=iOS; loginToken=" + tok + "; yiban_user_token=" + tok);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        Resp r = req("GET", "https://f.yiban.cn/iapp378946", null, h);
        String loc = r.location;
        if (loc != null && loc.contains("verify_request=")) return param(loc, "verify_request");
        if (loc != null && (loc.contains("act=iapp378946") || loc.contains("/iapp/index"))) {
            if (loc.startsWith("/")) loc = "https://f.yiban.cn" + loc;
            else if (!loc.startsWith("http")) loc = "https://f.yiban.cn/" + loc;
            Resp r2 = req("GET", loc, null, h);
            if (r2.location != null && r2.location.contains("verify_request=")) return param(r2.location, "verify_request");
        }
        return null;
    }

    /** 用 App token 完成易广金登录 + 设备校验，返回 studentID。 */
    String prepare(String tok, String fallbackUuid, String fallbackModel) throws Exception {
        cookies.clear();
        String verify = fetchVerify(tok);
        if (verify == null || verify.isEmpty()) {
            lastError = "TOKEN_EXPIRED";
            throw new Exception("TOKEN_EXPIRED");
        }
        String b = "----GDUFBoundary" + System.currentTimeMillis();
        String body = "--" + b + "\r\nContent-Disposition: form-data; name=\"loginToken\"\r\n\r\n\r\n"
                + "--" + b + "\r\nContent-Disposition: form-data; name=\"verify_request\"\r\n\r\n" + verify + "\r\n"
                + "--" + b + "--\r\n";
        Map<String, String> lh = new HashMap<>();
        lh.put("Content-Type", "multipart/form-data; boundary=" + b);
        lh.put("X-Requested-With", "XMLHttpRequest");
        lh.put("Cookie", "client=iOS");
        Resp lr = req("POST", BASE + "/Handler/login.ashx?flag=login", body, lh);
        JSONObject lj = new JSONObject(lr.body);
        if (lj.optInt("code") != 0) throw new Exception("易广金登录失败：" + MainActivity.clean(lj.optString("msg")));
        String goTo = lj.optString("goToUrl", "");
        int si = goTo.indexOf("studentID=");
        String sid = si >= 0 ? goTo.substring(si + 10).split("[^0-9]")[0] : "";
        studentID = sid;
        cookies.put("client", "iOS");
        if (!sid.isEmpty()) cookies.put("studentID", sid);

        gh = new HashMap<>();
        gh.put("X-Requested-With", "XMLHttpRequest");
        gh.put("Referer", BASE + "/ygj2024/student-default.aspx?studentID=" + sid);
        req("GET", BASE + goTo, null, gh);
        req("POST", BASE + "/Handler/device.ashx?flag=getNotice", form(mapOf("studentID", sid)), gh);

        String duuid = fallbackUuid;
        String dmodel = fallbackModel;
        String dsys = android.os.Build.VERSION.RELEASE;
        String dapp = "5.2.5";
        try {
            Resp sr = req("POST", BASE + "/Handler/device.ashx?flag=getStudentDevice",
                    form(mapOf("studentID", sid)), gh);
            JSONArray sa = new JSONObject(sr.body).optJSONArray("data");
            if (sa != null) for (int i = 0; i < sa.length(); i++) {
                JSONObject o = sa.getJSONObject(i);
                if ("1".equals(o.optString("state"))) {
                    duuid = o.optString("uuid", duuid);
                    dmodel = o.optString("deviceModel", dmodel);
                    dsys = o.optString("systemVersion", dsys);
                    dapp = o.optString("appVersion", dapp);
                    break;
                }
            }
        } catch (Exception ignored) { }

        JSONObject dev = new JSONObject();
        dev.put("uuid", duuid);
        dev.put("appVersion", dapp);
        dev.put("deviceModel", dmodel);
        dev.put("systemVersion", dsys);
        Resp dr = req("POST", BASE + "/Handler/device.ashx?flag=checkBindDevice",
                form(mapOf("deviceData", dev.toString(), "autoBind", "false")), gh);
        JSONObject dj = new JSONObject(dr.body);
        if (dj.optInt("code") == 1) {
            dev.put("uuid", fallbackUuid);
            dev.put("deviceModel", fallbackModel);
            dev.put("systemVersion", android.os.Build.VERSION.RELEASE);
            Resp dr2 = req("POST", BASE + "/Handler/device.ashx?flag=checkBindDevice",
                    form(mapOf("deviceData", dev.toString(), "autoBind", "true")), gh);
            JSONObject dj2 = new JSONObject(dr2.body);
            if (dj2.optInt("code") != 0) throw new Exception("设备绑定失败：" + MainActivity.clean(dj2.optString("msg")));
        } else if (dj.optInt("code") != 0) {
            throw new Exception("设备校验失败：" + MainActivity.clean(dj.optString("msg")));
        }
        return sid;
    }
}
