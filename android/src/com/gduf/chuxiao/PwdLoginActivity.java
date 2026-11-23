package com.gduf.chuxiao;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
 *
 * v6.5：界面按 V6 蓝色体系重做（含深色模式），并补上「未授权易广金」的去授权入口。
 */
public class PwdLoginActivity extends Activity {

    /** 易班 App 内置的密码加密公钥（4096 位，PKCS#1 v1.5）。 */
    static final String PUB_KEY =
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAzq0rgsM++ZxLRGHpdfreHu6UXhdlUS5P2WOxRG14qU8/iWSb/CkOqgOl8AGcOhlthkvolCdpUvVcVsVUxBv0YRN0Jb64zPrn5aLVwQT4RJn5tXvoqLdHIXis7pljXAMDPVZOVlWJkDMk8YU6HDaAMqsD6l5p9lg2LMP4OhMgaPX+CkO370LB5vRjJTHp03n+IqfxXoC7DEd+kxRIEM2CEDgUSYDJBDgwBvGALZmvB/a1b0im9t1P/EmnuE7uN9NRFoWyVpOiEwo/Ti7rmJGfqNT3vvtfWo4nXsm1rYQXsPayoKDSRaba3gFY/1SYWLAuSO2q2da5ZCcsAk5RKy0Vc1hUg8n6y0YLAvuzoXY5VyNMXkhH5Zc5Kg64b5RxILeZpZG0MV7GFY3sw//k7SNgdarKT8A0Iv3l3lfguX3HNi6dkf97kS/EiA0tbkIB/JNjv13mq8HL7LijRt2hkKqPPhQW88xC/exZilU5pAavoZOPuZIOTUHqtpRq4ZeKl+wDf+e5lPYFDpihWGjplGpa4BOSmGeo/SyVFPji9QF4Pk0DRJF/NjwJoAC60xHAVt5Z4gQSOOOjNZDCswA0ry2Le8m5cv5vPGY75uVrGqALQ6Xm961PPc5cJ1q7tmEZMj+z5HE7tgAdhiPI6acKgrAv+1k4N0OVqKamMS+PVpD05hUCAwEAAQ==";

    static final String SIG = "7170zo191911bb45";
    static final String APP_VER = "5.1.8";
    static final String LOGIN_URL = "https://m.yiban.cn/api/v4/passport/login";
    static final String IDENTIFY = "114514191981011";

    // ---- V6 语义色（跟随系统深浅） ----
    int cBg, cCard, cInk, cSub, cPrimary, cCont, cLine, cMuted;
    int cWarn;
    boolean night;

    final Handler ui = new Handler(Looper.getMainLooper());
    EditText phoneIn, pwdIn;
    TextView statusView;
    Button goBtn;
    volatile boolean busy = false;

    void applyTheme() {
        night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            cBg = 0xFF101318; cCard = 0xFF1C2129; cInk = 0xFFE8ECF2; cSub = 0xFF8E99A8;
            cPrimary = 0xFF4D86FF; cCont = 0xFF1E2A44; cLine = 0xFF2A3038; cMuted = 0xFF6E7886;
            cWarn = 0xFFE0B457;
        } else {
            cBg = 0xFFF4F7FC; cCard = 0xFFFFFFFF; cInk = 0xFF1A1D23; cSub = 0xFF7A8494;
            cPrimary = 0xFF2F6BFF; cCont = 0xFFE8EFFF; cLine = 0xFFEBF0F7; cMuted = 0xFFA8B2C0;
            cWarn = 0xFF9A6A00;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        recreate();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyTheme();
        try {
            getWindow().setStatusBarColor(cBg);
            getWindow().getDecorView().setSystemUiVisibility(night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Exception ignored) {
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scroll);

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(cCard);
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView back = tv("‹ 返回", 15, cSub, false);
        back.setPadding(dp(2), dp(6), dp(14), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = tv("易班账号密码登录", 18, cInk, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout spacer = new LinearLayout(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(dp(40), 1));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(16), dp(18), dp(16), dp(28));
        root.addView(pad, new LinearLayout.LayoutParams(-1, -2));

        // 卡片
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(cCard, 24, true));
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        pad.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView intro = tv("用易班手机号和密码登录，App 直接为你换取 token，不用抓包、也不用 root。\n\n"
                + "密码只在本机用于一次登录请求，不会保存，也不会上传到易班官方接口以外的任何地方。",
                13, cSub, false);
        intro.setLineSpacing(dp(7), 1f);
        card.addView(intro);

        TextView l1 = tv("易班手机号", 12, cSub, false);
        l1.setPadding(0, dp(18), 0, dp(6));
        card.addView(l1);

        phoneIn = input("11 位手机号");
        phoneIn.setInputType(InputType.TYPE_CLASS_PHONE);
        card.addView(phoneIn, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView l2 = tv("易班密码", 12, cSub, false);
        l2.setPadding(0, dp(14), 0, dp(6));
        card.addView(l2);

        pwdIn = input("易班密码（不会保存）");
        pwdIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(pwdIn, new LinearLayout.LayoutParams(-1, dp(50)));

        goBtn = pill("登录并获取 token", cPrimary, 25, 16);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(50));
        blp.topMargin = dp(18);
        card.addView(goBtn, blp);
        goBtn.setOnClickListener(v -> doLogin());

        statusView = tv("", 12, cSub, false);
        statusView.setLineSpacing(dp(5), 1f);
        statusView.setPadding(0, dp(12), 0, 0);
        card.addView(statusView);

        TextView warn = tv("注意：这里登录会把该账号在易班 App 里的会话顶下线；换到的 token 只要不再重新登录就一直有效。",
                11, cWarn, false);
        warn.setLineSpacing(dp(4), 1f);
        warn.setPadding(dp(4), dp(14), dp(4), 0);
        pad.addView(warn);

        TextView how = tv("忘记密码？在易班 App 里用短信找回后，再回来登录。", 11, cMuted, false);
        how.setPadding(dp(4), dp(10), dp(4), 0);
        pad.addView(how);

        TextView note = tv("静签 v6.5 · 仅供技术交流", 11, cMuted, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(22), 0, 0);
        pad.addView(note);
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
        goBtn.setText("正在登录…");
        statusView.setText("正在向易班验证账号…");

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
                goBtn.setText("登录并获取 token");
                if (t != null && em == null) {
                    statusView.setText("登录成功，学号 " + s + "，正在返回…");
                    finishWithToken(t);
                    ui.postDelayed(this::finish, 400);
                    return;
                }
                if (t != null && em != null && em.startsWith("NEED_YIBAN_AUTH|")) {
                    // 拿到 token 了，但这个易班账号还没授权过「易广金」
                    finishWithToken(t);
                    statusView.setText("你的易班账号还没有授权「易广金」");
                    showYibanAuth(em.substring("NEED_YIBAN_AUTH|".length()));
                    return;
                }
                if (t != null) {
                    // 登录成功但易广金校验没过：token 仍可能有效，交给上层判定
                    statusView.setText("已拿到 token，但易广金校验未通过：" + em);
                    finishWithToken(t);
                    return;
                }
                if (em != null && em.startsWith("NEED_YIBAN_AUTH|")) {
                    statusView.setText("你的易班账号还没有授权「易广金」");
                    showYibanAuth(em.substring("NEED_YIBAN_AUTH|".length()));
                    return;
                }
                statusView.setText("登录失败：" + em);
            });
        }).start();
    }

    void finishWithToken(String t) {
        Intent data = new Intent();
        data.putExtra("token", t);
        setResult(RESULT_OK, data);
    }

    /** 未授权易广金时，给出「去授权」入口，而不是把 NEED_YIBAN_AUTH| 原样丢给用户。 */
    void showYibanAuth(final String url) {
        m3Dialog("需要先在易班授权一次",
                "这个易班账号还没有授权过「易广金」。\n\n点「去授权」会用浏览器打开易班授权页，用同一个易班账号登录后点「同意授权」，完成后回到静签再点生成凭证。",
                "去授权", () -> {
                    finish();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ex) {
                        Toast.makeText(PwdLoginActivity.this, "没有可用的浏览器，请手动打开授权页", Toast.LENGTH_LONG).show();
                    }
                },
                "稍后再说", this::finish);
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

    GradientDrawable round(int color, float radiusDp, boolean stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke) g.setStroke(dp(1), cLine);
        return g;
    }

    Button pill(String text, int color, float radiusDp, float sizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(sizeSp);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setBackground(round(color, radiusDp, false));
        return b;
    }

    EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(cMuted);
        e.setTextColor(cInk);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(cCont, 14, false));
        return e;
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

    /** M3 风格对话框：圆角卡片 + 双 pill 按钮，跟随深浅色（与主界面一致） */
    void m3Dialog(String title, String msg, String posText, Runnable posAct, String negText, Runnable negAct) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(cCard, 24, false));
        box.setPadding(dp(22), dp(22), dp(22), dp(16));
        box.setElevation(dp(10));

        box.addView(tv(title, 19, cInk, true));
        if (msg != null && !msg.isEmpty()) {
            TextView m = tv(msg, 13, cSub, false);
            m.setLineSpacing(dp(7), 1f);
            m.setPadding(0, dp(10), 0, 0);
            box.addView(m);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(18);
        box.addView(row, rlp);

        if (negText != null) {
            TextView neg = tv(negText, 14, cSub, false);
            neg.setGravity(Gravity.CENTER);
            neg.setPadding(dp(16), 0, dp(16), 0);
            neg.setBackground(round(cCont, 22, false));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, dp(44));
            nlp.rightMargin = dp(8);
            row.addView(neg, nlp);
            neg.setOnClickListener(v -> {
                d.dismiss();
                if (negAct != null) negAct.run();
            });
        }

        Button pos = pill(posText, cPrimary, 22, 14);
        pos.setPadding(dp(20), 0, dp(20), 0);
        row.addView(pos, new LinearLayout.LayoutParams(-2, dp(44)));
        pos.setOnClickListener(v -> {
            d.dismiss();
            if (posAct != null) posAct.run();
        });

        wrap.addView(box, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(wrap);

        int wpx = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
        wrap.measure(View.MeasureSpec.makeMeasureSpec(wpx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setWindowAnimations(0);
            w.setLayout(wpx, Math.max(wrap.getMeasuredHeight(), dp(80)));
        }
        d.show();
    }
}
