package com.gduf.chuxiao;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;

/**
 * 易班账号密码登录（免抓包、免 root）。
 *
 * 直接调易班 passport 登录接口，把手机号 + 密码用 RSA 加密后提交，
 * 服务端回传 access_token —— 这个 token 即为 loginToken，可直接用于易广金凭证。
 *
 * 密码只在本机内存中使用，不写入任何存储、不上传到除易班官方接口以外的地址。
 */
public class PwdLoginActivity extends Activity {

    static final int GREEN = 0xFF78B842;
    static final int BAR = 0xFF6CA53A;
    static final int BTN = 0xFF1B8C33;

    /** 易班 App 内置的密码加密公钥（4096 位，PKCS#1 v1.5）。 */
    static final String PUB_KEY =
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAzq0rgsM++ZxLRGHpdfreHu6UXhdlUS5P2WOxRG14qU8/iWSb/CkOqgOl8AGcOhlthkvolCdpUvVcVsVUxBv0YRN0Jb64zPrn5aLVwQT4RJn5tXvoqLdHIXis7pljXAMDPVZOVlWJkDMk8YU6HDaAMqsD6l5p9lg2LMP4OhMgaPX+CkO370LB5vRjJTHp03n+IqfxXoC7DEd+kxRIEM2CEDgUSYDJBDgwBvGALZmvB/a1b0im9t1P/EmnuE7uN9NRFoWyVpOiEwo/Ti7rmJGfqNT3vvtfWo4nXsm1rYQXsPayoKDSRaba3gFY/1SYWLAuSO2q2da5ZCcsAk5RKy0Vc1hUg8n6y0YLAvuzoXY5VyNMXkhH5Zc5Kg64b5RxILeZpZG0MV7GFY3sw//k7SNgdarKT8A0Iv3l3lfguX3HNi6dkf97kS/EiA0tbkIB/JNjv13mq8HL7LijRt2hkKqPPhQW88xC/exZilU5pAavoZOPuZIOTUHqtpRq4ZeKl+wDf+e5lPYFDpihWGjplGpa4BOSmGeo/SyVFPji9QF4Pk0DRJF/NjwJoAC60xHAVt5Z4gQSOOOjNZDCswA0ry2Le8m5cv5vPGY75uVrGqALQ6Xm961PPc5cJ1q7tmEZMj+z5HE7tgAdhiPI6acKgrAv+1k4N0OVqKamMS+PVpD05hUCAwEAAQ==";

    static final String SIG = "7170zo191911bb45";
    static final String APP_VER = "5.1.8";
    static final String LOGIN_URL = "https://m.yiban.cn/api/v4/passport/login";
    static final String IDENTIFY = "114514191981011";

    final Handler ui = new Handler(Looper.getMainLooper());
    EditText phoneIn, pwdIn;
    TextView statusView;
    Button goBtn;
    volatile boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(GREEN);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView back = tv("‹ 返回", 16, Color.WHITE, false);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = tv("易班账号密码登录", 17, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.addView(pad, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        pad.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView intro = tv("用你的易班手机号和密码登录，App 会直接为你换取 token，"
                + "不需要抓包、也不需要 root。\n\n密码只在本机用于一次登录请求，不会保存，也不会上传到除易班官方接口以外的任何地方。",
                13, 0xFF444444, false);
        card.addView(intro);

        phoneIn = new EditText(this);
        phoneIn.setHint("易班手机号");
        phoneIn.setTextSize(15);
        phoneIn.setSingleLine(true);
        phoneIn.setInputType(InputType.TYPE_CLASS_PHONE);
        card.addView(phoneIn, topLp(dp(12), dp(48)));

        pwdIn = new EditText(this);
        pwdIn.setHint("易班密码");
        pwdIn.setTextSize(15);
        pwdIn.setSingleLine(true);
        pwdIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(pwdIn, topLp(dp(8), dp(48)));

        goBtn = new Button(this);
        goBtn.setText("登录并获取 token");
        goBtn.setTextSize(16);
        goBtn.setAllCaps(false);
        goBtn.setTextColor(Color.WHITE);
        goBtn.setBackground(bg(BTN, 10));
        card.addView(goBtn, topLp(dp(14), dp(48)));
        goBtn.setOnClickListener(v -> doLogin());

        statusView = tv("", 12, 0xFF777777, false);
        statusView.setPadding(dp(2), dp(10), dp(2), 0);
        card.addView(statusView);

        TextView warn = tv("注意：这里登录会把该账号在易班 App 里的会话顶下线；"
                + "换到的 token 只要不再重新登录就一直有效。", 11, 0xFF9A6A00, false);
        warn.setPadding(0, dp(14), 0, 0);
        pad.addView(warn);

        TextView how = tv("忘记密码？在易班 App 里用短信找回后，再回来登录。", 11, 0xCCFFFFFF, false);
        how.setPadding(dp(2), dp(10), dp(2), 0);
        pad.addView(how);
    }

    // ---------------- 登录流程 ----------------

    void doLogin() {
        final String phone = phoneIn.getText().toString().trim();
        final String pwd = pwdIn.getText().toString();
        if (!phone.matches("1[3-9]\\d{9}")) {
            statusView.setText("请输入正确的 11 位手机号");
            return;
        }
        if (pwd.isEmpty()) {
            statusView.setText("请输入易班密码");
            return;
        }
        if (busy) return;
        busy = true;
        goBtn.setEnabled(false);
        statusView.setText("正在登录…");

        new Thread(() -> {
            String token = null, sid = null, err = null;
            try {
                token = passportLogin(phone, pwd);
                if (token == null || token.isEmpty()) throw new Exception("服务器没有返回 token");
                SharedPreferences sp = getSharedPreferences("cfg", 0);
                String u = sp.getString("deviceUuid", "");
                if (u.isEmpty()) {
                    u = UUID.randomUUID().toString().toUpperCase(Locale.US);
                    sp.edit().putString("deviceUuid", u).apply();
                }
                YgjClient c = new YgjClient();
                sid = c.prepare(token, u, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String t = token, s = sid, em = err;
            ui.post(() -> {
                busy = false;
                goBtn.setEnabled(true);
                if (t != null && em == null) {
                    statusView.setText("登录成功，学号 " + s + "，正在返回…");
                    Intent data = new Intent();
                    data.putExtra("token", t);
                    setResult(RESULT_OK, data);
                    ui.postDelayed(this::finish, 400);
                } else if (t != null) {
                    // 登录成功但易广金校验没过：token 仍可能有效，交给上层判定
                    statusView.setText("已拿到 token，但易广金校验未通过：" + em);
                    Intent data = new Intent();
                    data.putExtra("token", t);
                    setResult(RESULT_OK, data);
                    ui.postDelayed(this::finish, 1200);
                } else {
                    statusView.setText("登录失败：" + em);
                }
            });
        }).start();
    }

    /** 调易班 passport 登录接口，返回 access_token（即 loginToken）。 */
    String passportLogin(String mobile, String password) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("device", android.os.Build.MANUFACTURER + ":" + android.os.Build.MODEL);
        f.put("v", APP_VER);
        f.put("password", rsaEncrypt(password));
        f.put("token", "");
        f.put("mobile", mobile);
        f.put("ct", "2");
        f.put("identify", IDENTIFY);
        f.put("sversion", String.valueOf(android.os.Build.VERSION.SDK_INT));
        f.put("app", "1");
        f.put("apn", "wifi");
        f.put("authCode", "");
        f.put("sig", SIG);

        String body = form(f);
        HttpURLConnection conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Authorization", "Bearer");
        conn.setRequestProperty("loginToken", "");
        conn.setRequestProperty("AppVersion", APP_VER);
        conn.setRequestProperty("User-Agent", "YiBan/" + APP_VER
                + " Mozilla/5.0 (Linux; Android " + android.os.Build.VERSION.RELEASE
                + "; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/81.0.4044.114 Mobile Safari/537.36");
        byte[] bb = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bb.length);
        OutputStream os = conn.getOutputStream();
        os.write(bb);
        os.close();
        int code = conn.getResponseCode();
        String resp = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();

        JSONObject j = new JSONObject(resp);
        int r = j.optInt("response");
        String msg = MainActivity.clean(j.optString("message"));
        if (r == 100) {
            JSONObject d = j.optJSONObject("data");
            return d == null ? "" : d.optString("access_token", "");
        }
        if (r == 110) {
            throw new Exception("易班要求人机验证，请隔一会儿再试；实在不行就改用「粘贴 token」方式");
        }
        if (!msg.isEmpty()) throw new Exception(msg);
        throw new Exception("登录失败（代码 " + r + "）");
    }

    // ---------------- 工具 ----------------

    static String rsaEncrypt(String plain) throws Exception {
        byte[] der = Base64.decode(PUB_KEY, Base64.DEFAULT);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
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

    static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    LinearLayout.LayoutParams topLp(int topMargin, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.topMargin = topMargin;
        return lp;
    }

    GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    TextView tv(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLineSpacing(dp(4), 1f);
        return v;
    }
}
