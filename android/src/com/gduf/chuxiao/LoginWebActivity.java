package com.gduf.chuxiao;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.UUID;

/**
 * 易班扫码登录（免抓包）。
 *
 * 流程：
 * 1. WebView 打开 f.yiban.cn 的易班登录页，用户用易班 App 扫码登录；
 * 2. 从 Cookie 里挑出登录凭证；
 * 3. 先用「原生接口完整验证」直接判定这个凭证能不能登录易广金（结果直接写在状态条上）；
 * 4. 若网页被「仅限在易班app内使用」拦住，可点「以 App 身份重试」换 UA 再拉一次，尝试拿 verify_request。
 */
public class LoginWebActivity extends Activity {

    static final String ENTRY = "https://f.yiban.cn/iapp378946";
    /** 易班 App 的 UA，用来骗过 iapp 页面的「仅限在易班app内使用」校验。 */
    static final String APP_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 yiban_iOS/5.2.5(5104)";

    final Handler ui = new Handler(Looper.getMainLooper());
    WebView web;
    TextView status, log;
    Button doneBtn, reloadBtn, appRetryBtn;

    String tokenFound = "";
    String verifyFound = "";
    String lastUrl = "";
    boolean finished = false;
    boolean probing = false;
    boolean probedThisToken = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF78B842);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF6CA53A);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView back = tv("‹ 返回", 16, Color.WHITE, false);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = tv("易班扫码登录", 17, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        reloadBtn = new Button(this);
        reloadBtn.setText("重载");
        reloadBtn.setTextSize(13);
        reloadBtn.setAllCaps(false);
        reloadBtn.setBackgroundColor(Color.TRANSPARENT);
        reloadBtn.setTextColor(Color.WHITE);
        reloadBtn.setOnClickListener(v -> web.reload());
        bar.addView(reloadBtn);

        status = tv("正在打开易班登录页…", 12, 0xF2FFFFFF, false);
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        status.setBackgroundColor(0x22000000);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        log = tv("", 10, 0xCCFFFFFF, false);
        log.setPadding(dp(12), dp(4), dp(12), dp(4));
        log.setBackgroundColor(0x15000000);
        root.addView(log, new LinearLayout.LayoutParams(-1, -2));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                lastUrl = u;
                if (u.contains("verify_request=")) verifyFound = after(u, "verify_request=");
                ui.post(LoginWebActivity.this::probe);
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                lastUrl = url;
                if (url != null && url.contains("verify_request=")) verifyFound = after(url, "verify_request=");
                appendLog("页面：" + shortUrl(url));
                ui.postDelayed(LoginWebActivity.this::probe, 400);
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        appRetryBtn = new Button(this);
        appRetryBtn.setText("以 App 身份重试（取 verify_request）");
        appRetryBtn.setTextSize(14);
        appRetryBtn.setAllCaps(false);
        appRetryBtn.setTextColor(Color.WHITE);
        appRetryBtn.setBackgroundColor(0xFF4E6E2F);
        root.addView(appRetryBtn, new LinearLayout.LayoutParams(-1, dp(44)));
        appRetryBtn.setOnClickListener(v -> retryAsApp());

        doneBtn = new Button(this);
        doneBtn.setText("我已登录，返回静签");
        doneBtn.setTextSize(15);
        doneBtn.setAllCaps(false);
        doneBtn.setTextColor(Color.WHITE);
        doneBtn.setBackgroundColor(0xFF1B8C33);
        doneBtn.setEnabled(false);
        root.addView(doneBtn, new LinearLayout.LayoutParams(-1, dp(50)));
        doneBtn.setOnClickListener(v -> finishWithToken());

        setContentView(root);
        web.loadUrl(ENTRY);
    }

    // ---------------- 轮询与判定 ----------------

    void probe() {
        if (finished) return;
        String token = pickToken(CookieManager.getInstance().getCookie(ENTRY));
        if (token.isEmpty()) token = pickToken(CookieManager.getInstance().getCookie("https://www.yiban.cn"));

        if (!token.isEmpty()) {
            tokenFound = token;
            doneBtn.setEnabled(true);
            if (!probedThisToken && !probing) {
                probedThisToken = true;
                probeNative(token);
                return;
            }
            status.setText("已读到登录凭证 " + mask(token)
                    + (verifyFound.isEmpty() ? "（verify_request 还没拿到）" : "，已拿到 verify_request"));
            return;
        }

        if (lastUrl.contains("ygj.gduf.edu.cn")) {
            status.setText("已跳到易广金，但没读到登录凭证。可以点「重载」再来一次，或返回改用粘贴 token。");
            doneBtn.setEnabled(true);
        } else {
            status.setText("请用易班 App 扫二维码，或用账号密码登录；成功后这里会显示“验证结果”。");
        }
    }

    /** 用原生接口（和真正出码/签到同一套代码）验证这个凭证，能过就直接可用。 */
    void probeNative(String tok) {
        probing = true;
        status.setText("正在用原生接口验证这个登录态…（1/2 登录 + 设备对齐）");
        appendLog("开始原生验证，凭证 " + mask(tok) + "，长度 " + tok.length());
        final String tokF = tok;
        new Thread(() -> {
            String msg;
            boolean ok = false;
            try {
                YgjClient c = new YgjClient();
                android.content.SharedPreferences sp = getSharedPreferences("cfg", 0);
                String u = sp.getString("deviceUuid", "");
                if (u.isEmpty()) {
                    u = UUID.randomUUID().toString().toUpperCase(java.util.Locale.US);
                    sp.edit().putString("deviceUuid", u).apply();
                }
                String sid = c.prepare(tokF, u,
                        android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                ok = true;
                msg = "验证通过（学号 " + sid + "），可以直接返回静签了";
            } catch (Exception e) {
                String m = e.getMessage() == null ? "未知错误" : e.getMessage();
                msg = "验证失败：" + m;
            }
            final boolean fok = ok;
            final String fm = msg;
            ui.post(() -> {
                probing = false;
                status.setText(fm);
                appendLog(fm);
                doneBtn.setEnabled(true);
                if (fok) probedThisToken = true;
            });
        }).start();
    }

    /** 换成易班 App 的 UA + 补 app 级 cookie，再拉一次 iapp，试图越过「仅限在易班app内使用」。 */
    void retryAsApp() {
        if (tokenFound.isEmpty()) {
            status.setText("还没有登录凭证，先扫码登录再重试。");
            return;
        }
        status.setText("正在以 App 身份重新请求…");
        appendLog("切换 UA 为易班 iOS 客户端，注入 client=iOS");
        web.getSettings().setUserAgentString(APP_UA);
        CookieManager cm = CookieManager.getInstance();
        String host = "https://f.yiban.cn";
        cm.setCookie(host, "client=iOS");
        cm.setCookie(host, "loginToken=" + tokenFound);
        cm.setCookie(host, "yiban_user_token=" + tokenFound);
        cm.flush();
        web.loadUrl(ENTRY);
    }

    void finishWithToken() {
        finished = true;
        Intent data = new Intent();
        data.putExtra("token", tokenFound);
        data.putExtra("verify", verifyFound);
        setResult(RESULT_OK, data);
        finish();
    }

    // ---------------- 工具 ----------------

    void appendLog(String line) {
        ui.post(() -> {
            String old = log.getText().toString();
            if (old.length() > 400) old = old.substring(old.length() - 400);
            log.setText(old.isEmpty() ? line : old + "\n" + line);
        });
    }

    static String shortUrl(String u) {
        if (u == null) return "";
        return u.length() > 90 ? u.substring(0, 90) + "…" : u;
    }

    static String pickToken(String cookie) {
        if (cookie == null || cookie.isEmpty()) return "";
        String[] parts = cookie.split(";");
        String fallback = "";
        for (String p : parts) {
            String kv = p.trim();
            int i = kv.indexOf('=');
            if (i <= 0) continue;
            String k = kv.substring(0, i).trim();
            String v = kv.substring(i + 1).trim();
            if (v.isEmpty()) continue;
            if (k.equals("loginToken")) return v;
            if (k.equals("yiban_user_token")) return v;
            if ((k.equals("yibanToken") || k.equals("token")) && fallback.isEmpty()) fallback = v;
        }
        return fallback;
    }

    static String after(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return "";
        String rest = s.substring(i + key.length());
        int j = rest.length();
        for (char c : new char[]{'&', '#', '?', ' '}) {
            int k = rest.indexOf(c);
            if (k >= 0 && k < j) j = k;
        }
        return rest.substring(0, j);
    }

    static String mask(String t) {
        if (t == null) return "";
        if (t.length() <= 8) return t;
        return t.substring(0, 4) + "…" + t.substring(t.length() - 4);
    }

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    TextView tv(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.stopLoading();
                web.loadUrl("about:blank");
                web.destroy();
            }
        } catch (Exception ignored) { }
        super.onDestroy();
    }
}
