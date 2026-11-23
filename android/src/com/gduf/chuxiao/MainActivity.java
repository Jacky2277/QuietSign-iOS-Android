package com.gduf.chuxiao;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {

    static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 yiban_iOS/5.2.5(5104)";
    static final String BASE = "https://ygj.gduf.edu.cn";
    static final int GREEN = 0xFF78B842;
    static final int REQ_LOGIN = 21;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_LOGIN) return;
        if (res != RESULT_OK || data == null) {
            if (setupHint != null) setupHint.setText("已取消易班登录");
            return;
        }
        String t = data.getStringExtra("token");
        String v = data.getStringExtra("verify");
        if (t == null || t.trim().isEmpty()) {
            if (setupHint != null) {
                setupHint.setText("没有拿到登录 token。可以重试一次，或用下方的粘贴 token 方式。");
            }
            return;
        }
        sp.edit().putString("userToken", t.trim()).apply();
        if (v != null && !v.isEmpty()) sp.edit().putString("verifyOnce", v).apply();
        showQR(false);
    }

    SharedPreferences sp;
    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout root;
    ScrollView scroll;

    // QR screen
    TextView titleView, nameView, countdownView, statusView;
    ImageView qrView;
    CornerFrame corners;
    Button refreshBtn, overdueBtn, changeBtn, signBtn;
    TextView placeholder;
    volatile long endAt = 0;
    volatile boolean lastInbound = false;

    // setup screen
    EditText tokenInput;
    TextView setupHint;

    final Map<String, String> cookies = new HashMap<>();
    String studentID = "";
    String outSchoolID = "";
    String recType = "出";
    String lastName = "", lastStart = "", lastEnd = "";
    volatile boolean busy = false;
    volatile int gen = 0;
    volatile long nextRefreshAt = 0;

    static final String VERSION = "6.0";
    boolean night = false;
    int cBg, cCard, cInk, cSub, cSec, cPrimary, cCont, cLine, cMuted;
    TextView stateChip;
    ProgressBar progBar;

    void applyTheme() {
        int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        night = mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            cBg = 0xFF101318; cCard = 0xFF1C2129; cInk = 0xFFE8ECF2; cSub = 0xFF8E99A8;
            cSec = 0xFF96A2B2; cPrimary = 0xFF4D86FF; cCont = 0xFF1E2A44; cLine = 0xFF2A3038;
            cMuted = 0xFF6E7886;
        } else {
            cBg = 0xFFF4F7FC; cCard = 0xFFFFFFFF; cInk = 0xFF1A1D23; cSub = 0xFF7A8494;
            cSec = 0xFF5B6B80; cPrimary = 0xFF2F6BFF; cCont = 0xFFE8EFFF; cLine = 0xFFEBF0F7;
            cMuted = 0xFFA8B2C0;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        applyTheme();
        if (root != null) root.setBackgroundColor(cBg);
        if (token().isEmpty()) showSetup(false);
        else showQR(false);
    }

    static class Resp {
        int code;
        String body = "";
        String location = null;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", 0);
        applyTheme();
        try {
            getWindow().setStatusBarColor(cBg);
            getWindow().getDecorView().setSystemUiVisibility(night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Exception ignored) {
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));
        scroll.setBackgroundColor(cBg);
        setContentView(scroll);
        if (token().isEmpty()) showSetup(false);
        else showQR(false);
    }

    String token() { return sp.getString("userToken", ""); }

    String uuid() {
        String u = sp.getString("deviceUuid", "");
        if (u.isEmpty()) {
            u = UUID.randomUUID().toString().toUpperCase(Locale.US);
            sp.edit().putString("deviceUuid", u).apply();
        }
        return u;
    }

    static String clean(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    Button btn(String s, int bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        return b;
    }

    GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    Button pill(String s, int color, float radiusDp, float size) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(size);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(bg(color, radiusDp));
        try {
            b.setStateListAnimator(null);
            b.setElevation(0);
        } catch (Throwable ignored) {
        }
        return b;
    }

    void clearRoot() { root.removeAllViews(); }

    /** 二维码四角 L 框，间距固定（浅色/深色一致） */
    static class CornerFrame extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float inset, len;

        CornerFrame(Context c, int color) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(color);
            float d = c.getResources().getDisplayMetrics().density;
            p.setStrokeWidth(2.5f * d);
            inset = 10f * d;
            len = 22f * d;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float i = inset, L = len, s = p.getStrokeWidth() / 2f;
            float w = getWidth(), h = getHeight();
            cv.drawLine(i, i + s, i + L, i + s, p);
            cv.drawLine(i + s, i, i + s, i + L, p);
            cv.drawLine(w - i - L, i + s, w - i, i + s, p);
            cv.drawLine(w - i - s, i, w - i - s, i + L, p);
            cv.drawLine(i, h - i - s, i + L, h - i - s, p);
            cv.drawLine(i + s, h - i - L, i + s, h - i, p);
            cv.drawLine(w - i - L, h - i - s, w - i, h - i - s, p);
            cv.drawLine(w - i - s, h - i - L, w - i - s, h - i, p);
        }
    }

    // ---------------- m3 helpers ----------------

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = bg(cCard, 24);
        g.setStroke(dp(1), cLine);
        c.setBackground(g);
        return c;
    }

    LinearLayout appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv("静签", 21, cInk, true));
        String sub = studentID.isEmpty() ? "广东金融学院" : ("广东金融学院 · " + studentID);
        TextView s = tv(sub, 11, cSub, false);
        s.setPadding(0, dp(2), 0, 0);
        col.addView(s);
        bar.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = tv("···", 20, cSub, false);
        more.setPadding(dp(16), dp(6), dp(6), dp(6));
        more.setOnClickListener(v -> showAbout());
        bar.addView(more);
        return bar;
    }

    TextView sectionHead(String s) {
        TextView t = tv(s, 13, cSec, false);
        t.setPadding(0, dp(20), 0, dp(8));
        return t;
    }

    Button chipBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(cPrimary);
        b.setBackground(bg(cCont, 24));
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        try {
            b.setStateListAnimator(null);
            b.setElevation(0);
        } catch (Throwable ignored) {
        }
        return b;
    }

    LinearLayout listRow(String icon, boolean filled, String title, String sub, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        TextView ic = tv(icon, 15, filled ? 0xFFFFFFFF : cPrimary, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(bg(filled ? cPrimary : cCont, 12));
        row.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        col.addView(tv(title, 15, cInk, true));
        TextView s = tv(sub, 11, cSub, false);
        s.setPadding(0, dp(2), 0, 0);
        col.addView(s);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arr = tv("›", 18, cMuted, false);
        row.addView(arr);
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    void aboutCard(LinearLayout pad) {
        TextView sh = tv("关于作者", 13, cSec, false);
        sh.setPadding(0, dp(26), 0, dp(8));
        pad.addView(sh);
        LinearLayout c = card();
        c.setPadding(dp(18), dp(4), dp(18), dp(4));
        c.addView(listRow("J", true, "Jacky2277", "静签作者 · 广东金融学院", this::showAbout));
        View div1 = new View(this);
        div1.setBackgroundColor(cLine);
        c.addView(div1, new LinearLayout.LayoutParams(-1, dp(1)));
        c.addView(listRow("↗", false, "github.com/Jacky2277", "GitHub 主页 · 源码与更新", this::openGithub));
        View div2 = new View(this);
        div2.setBackgroundColor(cLine);
        c.addView(div2, new LinearLayout.LayoutParams(-1, dp(1)));
        c.addView(listRow("≡", false, "设置", "账号、设备与版本", this::showAbout));
        pad.addView(c, new LinearLayout.LayoutParams(-1, -2));
    }

    void bottomNote(LinearLayout pad) {
        TextView t = tv("静签 v" + VERSION + " · 仅供技术交流", 11, cMuted, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(26), 0, dp(2));
        pad.addView(t);
        TextView g = tv("github.com/Jacky2277", 11, cMuted, false);
        g.setGravity(Gravity.CENTER);
        g.setPadding(0, dp(2), 0, dp(10));
        pad.addView(g);
    }

    void showAbout() {
        m3Dialog("关于静签",
                "静签 v" + VERSION + "\n\n作者：Jacky2277\nGitHub：github.com/Jacky2277\n\n" +
                        "仅供技术交流，请勿用于违规用途。\n所有 token 只保存在本机，不会上传。",
                "打开 GitHub", this::openGithub, "关闭", null);
    }

    void openGithub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jacky2277")));
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    /** M3 风格对话框：圆角卡片 + pill 按钮，跟随深浅色 */
    void m3Dialog(String title, String msg, String posText, Runnable posAct, String negText, Runnable negAct) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(bg(cCard, 24));
        box.setPadding(dp(22), dp(22), dp(22), dp(16));
        box.setElevation(dp(10));

        box.addView(tv(title, 20, cInk, true));

        if (msg != null && !msg.isEmpty()) {
            TextView m = tv(msg, 13, cSub, false);
            m.setLineSpacing(dp(7), 1f);
            m.setPadding(0, dp(10), 0, 0);
            box.addView(m);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(18), 0, 0);

        if (negText != null) {
            Button neg = chipBtn(negText);
            neg.setTextSize(14);
            neg.setPadding(dp(16), 0, dp(16), 0);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, dp(44));
            nlp.rightMargin = dp(10);
            row.addView(neg, nlp);
            neg.setOnClickListener(v -> {
                d.dismiss();
                if (negAct != null) negAct.run();
            });
        }
        if (posText != null) {
            Button pos = pill(posText, cPrimary, 24, 14);
            pos.setPadding(dp(18), 0, dp(18), 0);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, dp(48));
            row.addView(pos, plp);
            pos.setOnClickListener(v -> {
                d.dismiss();
                if (posAct != null) posAct.run();
            });
        }

        box.addView(row);
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

    // ---------------- setup ----------------

    void showSetup(boolean expired) {
        gen++;
        clearRoot();
        root.setBackgroundColor(cBg);
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(20), dp(20), dp(20), dp(24));
        root.addView(pad, new LinearLayout.LayoutParams(-1, -2));

        pad.addView(appBar());
        pad.addView(sectionHead("开始使用"));

        LinearLayout card = card();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        pad.addView(card, new LinearLayout.LayoutParams(-1, -2));

        card.addView(tv("首次使用，填一次 token", 17, cInk, true));
        TextView sub = tv("token 只保存在本机，不会上传", 12, cSub, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        card.addView(sub);

        TextView step = tv(
                "1. 安装抓包工具（ProxyPin / Stream）\n" +
                "2. 易班 App → 易广金 → 出入申报 → 常规出入\n" +
                "3. 在 f.yiban.cn 的请求头里找到 loginToken（32 位）\n" +
                "4. 粘贴到下面，点「开始使用」", 12, cSub, false);
        step.setLineSpacing(dp(6), 1f);
        card.addView(step);

        tokenInput = new EditText(this);
        tokenInput.setHint("粘贴 32 位 loginToken");
        tokenInput.setTextSize(13);
        tokenInput.setSingleLine(true);
        tokenInput.setTextColor(cInk);
        tokenInput.setHintTextColor(cSub);
        tokenInput.setBackground(bg(cCont, 14));
        tokenInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        tokenInput.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(12);
        card.addView(tokenInput, lp);

        Button go = pill("开始使用", cPrimary, 26, 16);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(50));
        glp.topMargin = dp(12);
        card.addView(go, glp);
        go.setOnClickListener(v -> saveToken());

        Button pwdLogin = chipBtn("易班账号密码登录（不用抓包）");
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, dp(46));
        wlp.topMargin = dp(8);
        card.addView(pwdLogin, wlp);
        pwdLogin.setOnClickListener(v -> startActivityForResult(new Intent(this, PwdLoginActivity.class), REQ_LOGIN));

        Button rootBtn = chipBtn("一键读取本机 token（需 root）");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(46));
        rlp.topMargin = dp(8);
        card.addView(rootBtn, rlp);
        rootBtn.setOnClickListener(v -> readRootToken());

        Button det = chipBtn("详细教程");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(44));
        dlp.topMargin = dp(8);
        card.addView(det, dlp);
        det.setOnClickListener(v -> showDetail());

        setupHint = tv(expired ? "token 已失效，请重新抓一次并粘贴" : "token 只保存在本机，不会上传", 12,
                expired ? 0xFFE5484D : cSub, false);
        setupHint.setPadding(dp(2), dp(10), dp(2), 0);
        card.addView(setupHint);

        if (!token().isEmpty()) {
            Button back = chipBtn("返回凭证页");
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
            blp.topMargin = dp(12);
            pad.addView(back, blp);
            back.setOnClickListener(v -> showQR(false));
        }

        bottomNote(pad);
    }

    void showDetail() {
        String msg = "1. 安装 ProxyPin（应用商店搜索，或官网下载 apk）\n" +
                "2. 打开 ProxyPin，点「开始抓包」；首次使用按提示安装 CA 证书（设置 → 安全 → 加密与凭据 → 安装证书 → CA 证书）\n" +
                "3. 保持抓包，打开易班 App → 易广金 → 出入申报 → 常规出入，停在二维码页\n" +
                "4. 回 ProxyPin 停止抓包，找到 f.yiban.cn 的请求（地址里带 iapp378946 或 iapp/index）\n" +
                "5. 点开请求 → 请求头 → 找到 Cookie，复制 loginToken= 后面那 32 位字符（到分号为止，不要带空格）\n" +
                "6. 粘贴到输入框，点「开始使用」\n\n" +
                "token 只保存在本机，不会上传；失效时 App 会提示重新抓取。\n" +
                "抓不到 f.yiban.cn：确认停在二维码页，清空记录后重抓一次。";
        m3Dialog("详细教程", msg, "知道了", null, null, null);
    }

    void readRootToken() {
        setupHint.setText("正在扫描易班内存，请让易班保持已登录…");
        new Thread(() -> {
            String tok = null, err = null;
            try {
                tok = RootToken.fetch();
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String t = tok, em = err;
            runOnUiThread(() -> {
                if (t != null) {
                    tokenInput.setText(t);
                    setupHint.setText("已读取到 token：" + t);
                    m3Dialog("已读取到 loginToken",
                            t + "\n\n复制下来可以保存/发给别人；点「继续」会自动填入并进入主界面。",
                            "复制", () -> {
                                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(ClipData.newPlainText("loginToken", t));
                                    Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                                }
                                saveToken();
                            },
                            "继续", this::saveToken);
                } else {
                    setupHint.setText("读取失败：" + em + "（Magisk 授权弹窗要点允许）");
                }
            });
        }).start();
    }

    void saveToken() {
        String t = tokenInput.getText().toString().trim();
        if (t.length() < 32) {
            setupHint.setText("token 应该是 32 位十六进制字符，检查一下是否复制完整");
            return;
        }
        sp.edit().putString("userToken", t).apply();
        showQR(false);
    }

    // ---------------- QR screen ----------------

    void showQR(boolean auto) {
        gen++;
        clearRoot();
        root.setBackgroundColor(cBg);
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(20), dp(20), dp(20), dp(24));
        root.addView(pad, new LinearLayout.LayoutParams(-1, -2));

        pad.addView(appBar());
        pad.addView(sectionHead("我的凭证"));

        LinearLayout card = card();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        pad.addView(card, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        titleView = tv("出校凭证", 17, cInk, true);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        stateChip = tv(auto ? "已生成" : "待生成", 11, cPrimary, true);
        stateChip.setBackground(bg(cCont, 13));
        stateChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        head.addView(stateChip);
        card.addView(head);

        int side = Math.min(getResources().getDisplayMetrics().widthPixels - dp(120), dp(260));
        LinearLayout qrBox = new LinearLayout(this);
        qrBox.setOrientation(LinearLayout.VERTICAL);
        qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
        qrBox.setPadding(0, dp(14), 0, dp(4));
        card.addView(qrBox, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout qrWrap = new FrameLayout(this);
        qrView = new ImageView(this);
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrView.setBackgroundColor(0xFFFFFFFF);
        FrameLayout.LayoutParams qf = new FrameLayout.LayoutParams(side - dp(44), side - dp(44));
        qf.gravity = Gravity.CENTER;
        qrWrap.addView(qrView, qf);

        corners = new CornerFrame(this, cPrimary);
        qrWrap.addView(corners, new FrameLayout.LayoutParams(side, side));

        placeholder = tv("点击下方按钮\n生成凭证", 15, cSub, false);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setBackground(bg(cCont, 16));
        FrameLayout.LayoutParams plf = new FrameLayout.LayoutParams(side - dp(44), side - dp(44));
        plf.gravity = Gravity.CENTER;
        qrWrap.addView(placeholder, plf);

        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(side, side);
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        qrBox.addView(qrWrap, qlp);

        nameView = tv(auto ? "正在加载…" : "还没有生成凭证", 13, cSub, false);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(dp(4), dp(10), dp(4), dp(2));
        card.addView(nameView);

        progBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progBar.setIndeterminate(false);
        progBar.setMax(1000);
        progBar.setProgress(auto ? 1000 : 0);
        try {
            progBar.setProgressTintList(ColorStateList.valueOf(cPrimary));
            progBar.setProgressBackgroundTintList(ColorStateList.valueOf(cLine));
        } catch (Exception ignored) {
        }
        LinearLayout.LayoutParams pgp = new LinearLayout.LayoutParams(-1, dp(6));
        pgp.topMargin = dp(10);
        card.addView(progBar, pgp);

        countdownView = tv("", 11, cSub, false);
        countdownView.setGravity(Gravity.CENTER);
        countdownView.setPadding(0, dp(8), 0, dp(2));
        card.addView(countdownView);

        statusView = tv(auto ? "正在生成…" : "点下方「生成凭证」生成二维码", 11, cSub, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(6), dp(12), dp(6), dp(2));
        pad.addView(statusView);

        // 两个同级主按钮
        LinearLayout act = new LinearLayout(this);
        act.setOrientation(LinearLayout.HORIZONTAL);
        act.setClipChildren(false);
        act.setClipToPadding(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(48));
        alp.topMargin = dp(14);
        pad.addView(act, alp);

        refreshBtn = pill(auto ? "刷新凭证" : "生成凭证", cPrimary, 24, 15);
        act.addView(refreshBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        refreshBtn.setOnClickListener(v -> refresh());

        View agap = new View(this);
        act.addView(agap, new LinearLayout.LayoutParams(dp(10), 1));

        signBtn = pill("签到考勤", cPrimary, 24, 15);
        act.addView(signBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        signBtn.setOnClickListener(v -> {
            Intent it = new Intent(this, SignActivity.class);
            // Android 14（API 34）+ targetSdk 34 起 overridePendingTransition 被系统忽略，
            // 必须改用 overrideActivityTransition，且要在 startActivity 之前调用。
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_up_in, R.anim.stay);
            }
            startActivity(it);
            if (Build.VERSION.SDK_INT < 34) {
                overridePendingTransition(R.anim.slide_up_in, R.anim.stay);
            }
        });

        // 次级 chip
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setClipChildren(false);
        chips.setClipToPadding(false);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, dp(48));
        chp.topMargin = dp(8);
        pad.addView(chips, chp);

        overdueBtn = chipBtn("跨天返校");
        chips.addView(overdueBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        overdueBtn.setOnClickListener(v -> doOverdue());

        View cgap = new View(this);
        chips.addView(cgap, new LinearLayout.LayoutParams(dp(10), 1));

        changeBtn = chipBtn("更换 token");
        chips.addView(changeBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        changeBtn.setOnClickListener(v -> confirmChangeToken());

        aboutCard(pad);
        bottomNote(pad);

        if (auto) {
            qrView.setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
            refresh();
        } else {
            qrView.setVisibility(View.GONE);
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    void confirmChangeToken() {
        m3Dialog("更换 token", "更换 token 会回到填写页，已保存的 token 不会丢。",
                "更换", () -> showSetup(false), "取消", null);
    }

    // ---------------- network ----------------

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
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    static String param(String url, String key) {
        try {
            int q = url.indexOf('?');
            if (q < 0) return null;
            for (String kv : url.substring(q + 1).split("&")) {
                int i = kv.indexOf('=');
                if (i > 0 && URLEncoder.encode(key, "UTF-8").equals(kv.substring(0, i)))
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

    static String multipartLogin(String verify) {
        String b = "----GDUFBoundary" + System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(b).append("\r\nContent-Disposition: form-data; name=\"loginToken\"\r\n\r\n\r\n");
        sb.append("--").append(b).append("\r\nContent-Disposition: form-data; name=\"verify_request\"\r\n\r\n").append(verify).append("\r\n");
        sb.append("--").append(b).append("--\r\n");
        return sb.toString();
    }

    Map<String, String> gh;

    static class QRResult {
        String qr = "", name = "", start = "", end = "", type = "出", oid = "";
    }

    String prepareSession() throws Exception {
        cookies.clear();
        final String tok = token();
        if (tok.isEmpty()) throw new Exception("TOKEN_EMPTY");

        String verify = fetchVerify(tok);
        if (verify == null || verify.isEmpty()) throw new Exception("TOKEN_EXPIRED");

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
        if (lj.optInt("code") != 0) {
            String yibanUrl = lj.optString("yibanUrl", "");
            if (lj.optInt("code") == 3 && !yibanUrl.isEmpty())
                throw new Exception("NEED_YIBAN_AUTH|" + yibanUrl);
            throw new Exception("ygj 登录失败：" + clean(lj.optString("msg")));
        }
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

        String duuid = uuid();
        String dmodel = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
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
        } catch (Exception ignored) {
        }

        JSONObject dev = new JSONObject();
        dev.put("uuid", duuid);
        dev.put("appVersion", dapp);
        dev.put("deviceModel", dmodel);
        dev.put("systemVersion", dsys);
        Resp dr = req("POST", BASE + "/Handler/device.ashx?flag=checkBindDevice",
                form(mapOf("deviceData", dev.toString(), "autoBind", "false")), gh);
        JSONObject dj = new JSONObject(dr.body);
        if (dj.optInt("code") == 1) {
            dev.put("uuid", uuid());
            dev.put("deviceModel", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            dev.put("systemVersion", android.os.Build.VERSION.RELEASE);
            Resp dr2 = req("POST", BASE + "/Handler/device.ashx?flag=checkBindDevice",
                    form(mapOf("deviceData", dev.toString(), "autoBind", "true")), gh);
            JSONObject dj2 = new JSONObject(dr2.body);
            if (dj2.optInt("code") != 0) throw new Exception("设备绑定失败：" + clean(dj2.optString("msg")));
        } else if (dj.optInt("code") != 0) {
            throw new Exception("设备校验失败：" + clean(dj.optString("msg")));
        }
        return sid;
    }

    String findLatestRecord(String sid, String type) {
        try {
            Resp mr = req("POST", BASE + "/Handler/outschool.ashx?flag=getMyOutSchool",
                    form(mapOf("studentID", sid)), gh);
            JSONArray arr = new JSONObject(mr.body).optJSONArray("data");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (type.equals(o.optString("type", ""))) return o.optString("outSchoolID", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    QRResult loadQRForRecord(String sid, String oid, String type) throws Exception {
        QRResult q = new QRResult();
        q.oid = oid;
        q.type = type;
        String page = BASE + "/ygj2024/outschool/student-outschool.aspx?type="
                + URLEncoder.encode(type, "UTF-8") + "&outSchoolID=" + oid;
        Map<String, String> h = new HashMap<>(gh);
        h.put("Referer", page);
        req("GET", page, null, h);

        Resp pr = req("POST", BASE + "/Handler/outschool.ashx?flag=getPassRecord",
                form(mapOf("studentID", sid, "outSchoolID", oid, "type", type)), h);
        JSONObject pj = new JSONObject(pr.body);
        if (pj.optInt("code") != 0) throw new Exception("读取凭证失败：" + clean(pj.optString("msg")));
        JSONArray arr = pj.optJSONArray("data");
        if (arr == null || arr.length() == 0) throw new Exception("凭证不存在或已过期");
        JSONObject o = arr.getJSONObject(0);
        q.name = o.optString("studentName", o.optString("name", ""));
        q.start = o.optString("startTime", o.optString("startDate", ""));
        q.end = o.optString("endTime", o.optString("endDate", ""));

        Resp qr = req("POST", BASE + "/Handler/outschool.ashx?flag=getQrcode",
                form(mapOf("outSchoolID", oid)), h);
        JSONObject qj = new JSONObject(qr.body);
        if (qj.optInt("code") != 0) throw new Exception("取二维码失败：" + clean(qj.optString("msg")));
        q.qr = qj.optString("qrcode", "");
        if (q.qr.isEmpty()) throw new Exception("二维码为空");
        return q;
    }

    void finishQR(QRResult q, int my) throws Exception {
        final Bitmap bmp = makeQR(q.qr + (System.currentTimeMillis() / 1000L), 720);
        if (!q.name.isEmpty()) lastName = q.name;
        if (!q.start.isEmpty()) lastStart = q.start;
        if (!q.end.isEmpty()) lastEnd = q.end;
        final boolean inbound = "入".equals(q.type);
        final String nm = lastName;
        final String st = lastStart;
        final String tt = timeNow();
        final long endMs = parseTime(lastEnd);
        ui.post(() -> {
            if (my != gen || qrView == null) return;
            titleView.setText(inbound ? "入校凭证" : "出校凭证");
            String who = nm.isEmpty() ? "" : (nm + (studentID.isEmpty() ? "" : "（" + studentID + "）"));
            nameView.setText(who.isEmpty() ? "" : (who + " · " + dayOf(st) + (inbound ? " 入校" : " 出校")));
            qrView.setVisibility(View.VISIBLE);
            if (placeholder != null) placeholder.setVisibility(View.GONE);
            if (refreshBtn != null) refreshBtn.setText("刷新凭证");
            if (stateChip != null) stateChip.setText("有效中");
            if (progBar != null) progBar.setProgress(1000);
            qrView.setImageBitmap(bmp);
            endAt = endMs;
            lastInbound = inbound;
            statusView.setText("凭证 " + tt + " 生成，每 60 秒自动刷新");
            nextRefreshAt = System.currentTimeMillis() + 60000;
            scheduleTick(my);
        });
    }

    void doRefresh(final int my) throws Exception {
        String sid = prepareSession();
        Resp ar = req("POST", BASE + "/Handler/outschool.ashx?flag=autoCreateOutSchoolRecord",
                form(mapOf("studentID", sid)), gh);
        JSONObject aj = new JSONObject(ar.body);
        if (aj.optInt("code") != 0) throw new Exception("生成凭证失败：" + clean(aj.optString("msg")));
        String oid = aj.optString("outSchoolID", "");
        String type = aj.optString("type", "出");
        if (type.isEmpty()) type = "出";
        if (oid.isEmpty()) oid = findLatestRecord(sid, type);
        if (oid.isEmpty()) throw new Exception("没有拿到凭证号，请稍后重试");
        outSchoolID = oid;
        recType = type;
        finishQR(loadQRForRecord(sid, oid, type), my);
    }

    void doOverdue() {
        m3Dialog("跨天返校",
                "将向学校提交一条「出校超时（申请进校）」：\n去向：广东省广州市天河区\n日期：今天\n次数：共一次出入\n\n提交后会生成入校凭证。",
                "提交并生成入校码", this::runOverdue, "取消", null);
    }

    void runOverdue() {
        if (busy) return;
        busy = true;
        final int my = ++gen;
        if (statusView != null) statusView.setText("正在提交出校超时（申请进校）…");
        new Thread(() -> {
            try {
                finishQR(applyOverdueReturn(prepareSession()), my);
            } catch (final Exception e) {
                ui.post(() -> {
                    if (statusView != null && my == gen) statusView.setText("出错：" + e.getMessage());
                });
            } finally {
                busy = false;
            }
        }).start();
    }

    QRResult applyOverdueReturn(String sid) throws Exception {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        String addPage = BASE + "/ygj2024/outschool/student-add.aspx";
        Map<String, String> h = new HashMap<>(gh);
        h.put("Referer", addPage);
        req("GET", addPage, null, h);
        Map<String, String> f = mapOf(
                "outSchoolID", "0", "studentID", sid, "days", "1", "passNum", "共一次出入",
                "outType", "CXCS", "outTypeName", "出校超时（申请进校）",
                "outAddress", "广东省广州市天河区", "startDate", today, "endDate", today,
                "linkName", "", "linkPhone", "", "phone", "", "images", "", "picture1", "", "picture2", "");
        Resp sr = req("POST", BASE + "/Handler/outschool.ashx?flag=save", form(f), h);
        JSONObject sj = new JSONObject(sr.body);
        if (sj.optInt("code") != 0) throw new Exception("提交失败：" + clean(sj.optString("msg")));
        String oid = sj.optString("outSchoolID", "");
        if (oid.isEmpty() || "0".equals(oid)) {
            Resp mr = req("POST", BASE + "/Handler/outschool.ashx?flag=getMyOutSchool",
                    form(mapOf("studentID", sid)), h);
            JSONArray arr = new JSONObject(mr.body).optJSONArray("data");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optString("outTypeName", "").contains("超时") || "入".equals(o.optString("type"))) {
                    oid = o.optString("outSchoolID", "");
                    break;
                }
            }
        }
        if (oid.isEmpty()) throw new Exception("提交成功，但没找到入校凭证，请点刷新重试");
        outSchoolID = oid;
        recType = "入";
        return loadQRForRecord(sid, oid, "入");
    }

    static long parseTime(String s) {
        if (s == null) return 0;
        try {
            String t = s.trim().replace('/', '-');
            if (t.length() >= 19) t = t.substring(0, 19);
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(t).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    static String dayOf(String s) {
        if (s == null) return "";
        String t = s.trim().replace('/', '-');
        return t.length() >= 10 ? t.substring(0, 10) : t;
    }

    static String timeNow() {
        return new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date());
    }

    void scheduleTick(final int my) {
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (countdownView == null || my != gen) return;
                long now = System.currentTimeMillis();
                if (now >= nextRefreshAt) { refresh(); return; }
                if (endAt > 0) {
                    long left = (endAt - now) / 1000;
                    if (left <= 0) {
                        countdownView.setText("该凭证已失效，正在重新生成…");
                        refresh();
                        return;
                    }
                    countdownView.setText(String.format(Locale.US, "该凭证 %02d:%02d 后将失效，请尽快%s校",
                            left / 60, left % 60, lastInbound ? "入" : "出"));
                }
                ui.postDelayed(this, 1000);
            }
        }, 1000);
    }

    void refresh() {
        if (busy) return;
        busy = true;
        final int my = ++gen;
        if (statusView != null) statusView.setText("正在生成…");
        new Thread(() -> {
            try {
                doRefresh(my);
            } catch (final Exception e) {
                final String msg = e.getMessage() == null ? "" : e.getMessage();
                ui.post(() -> {
                    if (my != gen) return;
                    if ("TOKEN_EMPTY".equals(msg)) { showSetup(false); return; }
                    if ("TOKEN_EXPIRED".equals(msg)) { showSetup(true); return; }
                    if (msg.startsWith("NEED_YIBAN_AUTH|")) {
                        final String url = msg.substring("NEED_YIBAN_AUTH|".length());
                        if (statusView != null) statusView.setText("你的易班账号还没有授权「易广金」");
                        m3Dialog("需要先在易班授权一次",
                                "这个易班账号还没有授权过「易广金」。\n\n点「去授权」会用浏览器打开易班授权页，登录易班账号后点「同意授权」，完成后回到静签再点生成凭证即可。",
                                "去授权", () -> {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                    } catch (Exception ex) {
                                        Toast.makeText(MainActivity.this, "没有可用的浏览器，请手动打开授权页", Toast.LENGTH_LONG).show();
                                    }
                                },
                                "取消", null);
                        return;
                    }
                    if (statusView != null) statusView.setText("出错：" + msg);
                });
            } finally {
                busy = false;
            }
        }).start();
    }

    static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    Bitmap makeQR(String text, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
        return bmp;
    }
}
