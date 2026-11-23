package com.gduf.chuxiao;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从本机易班进程内存里读 loginToken（需要 root）。
 * 方案：su 下用 dd 逐段读 /proc/<pid>/mem，流式搜 "loginToken" 后面的 32 位 hex，
 * 再用易班服务端做严格校验（必须能换到 ygj 的 verify_request）。
 */
public class RootToken {

    private static final Pattern HEX32 = Pattern.compile("([0-9a-f]{32})");
    private static final byte[] NEEDLE = "loginToken".getBytes();
    private static String SU = null;

    /** 找到能用的 su 可执行文件（很多机型的 su 不在 app 的 PATH 里） */
    private static String su() {
        if (SU != null) return SU;
        for (String c : new String[]{"/product/bin/su", "/debug_ramdisk/su",
                "/system/bin/su", "/system/xbin/su", "su"}) {
            try {
                Process p = new ProcessBuilder(c, "-c", "id").start();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                InputStream in = p.getInputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) bos.write(b, 0, n);
                in.close();
                p.waitFor();
                String out = bos.toString("UTF-8");
                if (out.contains("uid=0")) {
                    SU = c;
                    return SU;
                }
            } catch (Exception ignored) {
            }
        }
        throw new RuntimeException("root 不可用（Magisk 授权没给，或 su 被隐藏）");
    }

    /** 返回校验通过的 token；失败抛出带说明的异常 */
    public static String fetch() throws Exception {
        String pid = firstToken(sh("pidof com.yiban.app"));
        if (pid == null) throw new Exception("易班没在运行，请先打开易班并保持登录");

        String maps = sh("cat /proc/" + pid + "/maps");
        if (maps == null || maps.trim().isEmpty()) throw new Exception("读不到易班进程信息（root 是否授权？）");

        List<int[]> preferred = new ArrayList<>();
        List<int[]> others = new ArrayList<>();
        for (String line : maps.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 6) continue;
            if (!f[1].startsWith("rw")) continue;
            String[] se = f[0].split("-");
            long st, en;
            try {
                st = Long.parseLong(se[0], 16);
                en = Long.parseLong(se[1], 16);
            } catch (Exception e) {
                continue;
            }
            long size = en - st;
            if (size < 65536 || size > 64L * 1024 * 1024) continue;
            int[] r = {(int) (st / 4096), (int) (size / 4096)};
            if (f[5].contains("partition_alloc")) preferred.add(r);
            else others.add(r);
        }

        List<String> cands = new ArrayList<>();
        cands.addAll(scan(pid, preferred));
        if (cands.isEmpty()) cands.addAll(scan(pid, others));

        for (String c : cands) {
            if (verify(c)) return c;
        }
        throw new Exception(cands.isEmpty()
                ? "易班内存里没找到 loginToken（确认已登录且是易班主进程）"
                : "找到 " + cands.size() + " 个候选，但都被服务端拒绝了（token 可能已失效）");
    }

    private static List<String> scan(String pid, List<int[]> regions) throws IOException {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < regions.size()) {
            StringBuilder cmd = new StringBuilder();
            int batch = 0;
            while (i < regions.size() && batch < 12 && cmd.length() < 6000) {
                int[] r = regions.get(i++);
                if (cmd.length() > 0) cmd.append(";");
                cmd.append("dd if=/proc/").append(pid).append("/mem bs=4096 skip=")
                        .append(r[0]).append(" count=").append(r[1]).append(" 2>/dev/null");
                batch++;
            }
            Process p = new ProcessBuilder(su(), "-c", cmd.toString()).redirectErrorStream(false).start();
            InputStream in = p.getInputStream();
            byte[] buf = new byte[262144];
            byte[] prev = new byte[0];
            int n;
            while ((n = in.read(buf)) > 0) {
                byte[] data = new byte[prev.length + n];
                System.arraycopy(prev, 0, data, 0, prev.length);
                System.arraycopy(buf, 0, data, prev.length, n);
                collect(data, out);
                int keep = Math.min(256, data.length);
                prev = new byte[keep];
                System.arraycopy(data, data.length - keep, prev, 0, keep);
            }
            try { p.waitFor(); } catch (InterruptedException ignored) {}
            in.close();
            if (!out.isEmpty()) break;
        }
        return out;
    }

    private static void collect(byte[] data, List<String> out) {
        for (int i = 0; i + NEEDLE.length <= data.length; i++) {
            boolean hit = true;
            for (int j = 0; j < NEEDLE.length; j++) {
                if (data[i + j] != NEEDLE[j]) { hit = false; break; }
            }
            if (!hit) continue;
            int from = i + NEEDLE.length;
            int to = Math.min(data.length, from + 200);
            StringBuilder sb = new StringBuilder();
            for (int k = from; k < to; k++) {
                int b = data[k];
                if (b >= 32 && b < 127) sb.append((char) b);
                else sb.append('\n');
                if (sb.length() >= 220) break;
            }
            Matcher m = HEX32.matcher(sb.toString());
            while (m.find()) {
                String v = m.group(1);
                if (!out.contains(v)) out.add(v);
            }
        }
    }

    /** 严格校验：两跳后必须拿到 ygj 的 verify_request */
    private static boolean verify(String token) {
        try {
            String hop1 = getRedirect("https://f.yiban.cn/iapp378946", token);
            if (hop1 == null) return false;
            String hop2 = getRedirect(hop1, token);
            return hop2 != null && hop2.contains("verify_request=")
                    && hop2.contains("ygj.gduf.edu.cn");
        } catch (Exception e) {
            return false;
        }
    }

    private static String getRedirect(String url, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "Yiban/9.7.9 (iPhone; iOS 17.0; Scale/3.00)");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("loginToken", token);
        c.setRequestProperty("AppVersion", "5.2.5");
        c.setRequestProperty("Cookie",
                String.format(Locale.US, "client=iOS; loginToken=%s; yiban_user_token=%s", token, token));
        int code = c.getResponseCode();
        String loc = c.getHeaderField("Location");
        c.disconnect();
        if (code == 302 && loc != null) return loc;
        return null;
    }

    private static String sh(String cmd) throws IOException {
        Process p = new ProcessBuilder(su(), "-c", cmd).start();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        InputStream in = p.getInputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bos.write(b, 0, n);
        in.close();
        try { p.waitFor(); } catch (InterruptedException ignored) {}
        return bos.toString("UTF-8");
    }

    private static String firstToken(String s) {
        if (s == null) return null;
        for (String x : s.trim().split("\\s+")) {
            if (!x.isEmpty() && x.matches("\\d+")) return x;
        }
        return null;
    }
}
