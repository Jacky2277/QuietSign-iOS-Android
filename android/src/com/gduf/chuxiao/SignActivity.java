package com.gduf.chuxiao;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 签到考勤：课表 → 每节课的签到窗口 → 勾选后批量签到（自造码，不依赖别人扫码）。
 * 与 iOS 端 SignViewController 对齐。
 */
public class SignActivity extends Activity {

    static final int REQ_SCAN = 11;
    static final int REQ_TEST = 12;
    static final int GREEN = 0xFF78B842;
    static final int BAR = 0xFF6CA53A;
    static final String WIN_ONLY_KEY = "sign_window_only";

    final Handler ui = new Handler(Looper.getMainLooper());
    SharedPreferences sp;
    YgjClient client;
    String sid = "";

    JSONArray courses = new JSONArray();
    final Set<Integer> selected = new HashSet<>();
    final Set<Integer> signedSet = new HashSet<>();
    final Set<Integer> pending = new HashSet<>();

    int startSignMin = 10;
    int week = 1;
    int grade = 3;
    String xn = "", xnxq = "", school = "", stopSignMin = "15", lateSignMin = "2";

    boolean loading = false;
    boolean signing = false;
    boolean ticking = false;
    boolean windowOnly = false;

    boolean night = false;
    int cBg, cCard, cInk, cSub, cPrimary, cCont, cLine, cMuted;

    void applyTheme() {
        int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        night = mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            cBg = 0xFF101318; cCard = 0xFF1C2129; cInk = 0xFFE8ECF2; cSub = 0xFF8E99A8;
            cPrimary = 0xFF4D86FF; cCont = 0xFF1E2A44; cLine = 0xFF2A3038; cMuted = 0xFF6E7886;
        } else {
            cBg = 0xFFF4F7FC; cCard = 0xFFFFFFFF; cInk = 0xFF1A1D23; cSub = 0xFF7A8494;
            cPrimary = 0xFF2F6BFF; cCont = 0xFFE8EFFF; cLine = 0xFFEBF0F7; cMuted = 0xFFA8B2C0;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        recreate();
    }

    @Override
    public void finish() {
        // CLOSE 转场要在 finish() 之前声明；API 34 起旧的 overridePendingTransition 已失效。
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.stay, R.anim.slide_down_out);
        }
        super.finish();
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(R.anim.stay, R.anim.slide_down_out);
        }
    }

    TextView status, hint, allBtn, scanBtn, reloadBtn, emptyLabel;
    Button batchBtn;
    Switch winSwitch;
    LinearLayout listBox;

    List<View> rows = new ArrayList<>();
    List<TextView> boxes = new ArrayList<>();
    List<TextView> stts = new ArrayList<>();

    Handler winHandler = new Handler(Looper.getMainLooper());
    Runnable winTick = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", 0);
        windowOnly = sp.getBoolean(WIN_ONLY_KEY, false);

        applyTheme();
        try {
            getWindow().setStatusBarColor(cBg);
            getWindow().getDecorView().setSystemUiVisibility(night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Exception ignored) {
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(cCard);
        bar.setElevation(dp(2));
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView back = tv("‹ 返回", 15, cSub, false);
        back.setPadding(dp(2), dp(6), dp(14), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = tv("签到考勤", 18, cInk, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        reloadBtn = tv("刷新", 14, cPrimary, false);
        reloadBtn.setPadding(dp(12), dp(6), dp(2), dp(6));
        reloadBtn.setOnClickListener(v -> load());
        bar.addView(reloadBtn);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        pad.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(pad, new LinearLayout.LayoutParams(-1, -2));

        status = tv("正在登录易广金…", 12, cSub, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(12));
        pad.addView(status);

        // 窗口开关
        LinearLayout winRow = new LinearLayout(this);
        winRow.setOrientation(LinearLayout.HORIZONTAL);
        winRow.setGravity(Gravity.CENTER_VERTICAL);
        winRow.setBackground(round(cCard, 20));
        winRow.setElevation(dp(2));
        winRow.setPadding(dp(14), dp(12), dp(12), dp(12));
        pad.addView(winRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout winText = new LinearLayout(this);
        winText.setOrientation(LinearLayout.VERTICAL);
        TextView wt = tv("仅在签到窗口内提交", 14, cInk, true);
        winText.addView(wt);
        TextView wd = tv("未到开窗时间的课先挂起，到点自动提交", 11, cSub, false);
        wd.setPadding(0, dp(2), 0, 0);
        winText.addView(wd);
        winRow.addView(winText, new LinearLayout.LayoutParams(0, -2, 1));

        winSwitch = new Switch(this);
        winSwitch.setChecked(windowOnly);
        winSwitch.setOnCheckedChangeListener((v, checked) -> {
            windowOnly = checked;
            sp.edit().putBoolean(WIN_ONLY_KEY, checked).apply();
            hint.setText(checked ? "窗口模式已开启：未到开窗时间的课会挂起等待"
                    : "当前不会自动提交，必须点“批量签到”或扫码");
        });
        winRow.addView(winSwitch);

        // 课程工具行
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(18), 0, dp(8));
        pad.addView(tools, new LinearLayout.LayoutParams(-1, -2));

        TextView section2 = tv("今日课程", 13, cSub, false);
        tools.addView(section2, new LinearLayout.LayoutParams(0, -2, 1));

        allBtn = tv("全选", 13, cPrimary, false);
        allBtn.setPadding(dp(10), dp(4), dp(2), dp(4));
        allBtn.setOnClickListener(v -> toggleAll());
        tools.addView(allBtn);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        pad.addView(listBox, new LinearLayout.LayoutParams(-1, -2));

        emptyLabel = tv("", 12, cSub, false);
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setPadding(0, dp(18), 0, dp(18));
        pad.addView(emptyLabel);

        batchBtn = pill("批量签到", cPrimary, 24, 16);
        batchBtn.setEnabled(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(50));
        blp.topMargin = dp(16);
        pad.addView(batchBtn, blp);
        batchBtn.setOnClickListener(v -> batchSign());

        scanBtn = tv("扫码签到", 14, cPrimary, false);
        scanBtn.setGravity(Gravity.CENTER);
        scanBtn.setBackground(round(cCont, 24));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(46));
        slp.topMargin = dp(10);
        pad.addView(scanBtn, slp);
        scanBtn.setOnClickListener(v -> startScan());

        hint = tv("", 11, cSub, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), dp(14), dp(10), 0);
        pad.addView(hint);

        TextView testCam = tv("测试相机（不会提交）", 11, cMuted, false);
        testCam.setGravity(Gravity.CENTER);
        testCam.setPadding(dp(10), dp(18), dp(10), 0);
        testCam.setOnClickListener(v -> startActivityForResult(new Intent(this, ScanActivity.class), REQ_TEST));
        pad.addView(testCam);

        load();
    }

    // ================= 加载课表 =================

    void load() {
        if (loading) return;
        loading = true;
        signedSet.clear();
        pending.clear();
        stopWindowTimer();
        batchBtn.setEnabled(false);
        batchBtn.setText("批量签到");
        status.setText("正在加载课程…");
        hint.setText("");
        emptyLabel.setText("");
        listBox.removeAllViews();
        rows.clear();
        boxes.clear();
        stts.clear();
        courses = new JSONArray();

        final String tok = sp.getString("userToken", "");
        if (tok.isEmpty()) {
            loading = false;
            status.setText("还没有填写 token，请返回主界面填写");
            return;
        }
        new Thread(() -> {
            try {
                client = new YgjClient();
                sid = client.prepare(tok, UUID.randomUUID().toString(),
                        android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);

                JSONObject sch = getJson(YgjClient.BASE
                        + "/Handler/sign.ashx?flag=getStuInfoSchedule&studentID=" + sid);
                if (sch.optInt("code") != 0) throw new Exception("获取课表失败：" + sch.optString("msg"));
                JSONObject d = sch.optJSONObject("data");
                if (d == null) throw new Exception("课表数据异常");

                String yearIn = d.optString("yearIn", "2025");
                school = d.optString("school", "清远校区");
                startSignMin = d.optInt("startSignMin", 10);
                if (startSignMin <= 0) startSignMin = 10;
                stopSignMin = d.optString("stopSignMin", "15");
                lateSignMin = d.optString("lateSignMin", "2");
                xn = d.optString("xn", "");
                xnxq = d.optString("xnxq", "");
                week = weekNum(d.optString("firstWeekDate", ""));
                grade = grade(yearIn);

                JSONArray all = d.optJSONArray("scheduleInfo");
                List<JSONObject> slots = new ArrayList<>();
                Set<String> seenJc = new HashSet<>();
                String g = String.valueOf(grade);
                if (all != null) for (int i = 0; i < all.length(); i++) {
                    JSONObject it = all.optJSONObject(i);
                    if (it == null) continue;
                    if (!g.equals(it.optString("grade"))) continue;
                    String ts, te;
                    if ("广州校本部".equals(school)) { ts = it.optString("timeS"); te = it.optString("timeE"); }
                    else if ("肇庆校区".equals(school)) { ts = it.optString("zqTimeS"); te = it.optString("zqTimeE"); }
                    else { ts = it.optString("qyTimeS"); te = it.optString("qyTimeE"); }
                    String jc = it.optString("lessonTime", "").replace("第", "").replace("小节", "");
                    if (ts.isEmpty() || te.isEmpty() || jc.isEmpty() || seenJc.contains(jc)) continue;
                    seenJc.add(jc);
                    JSONObject s = new JSONObject();
                    s.put("jc", jc);
                    s.put("ts", ts);
                    slots.add(s);
                }
                slots.sort((a, c) -> a.optString("ts").compareTo(c.optString("ts")));

                JSONArray list = new JSONArray();
                Set<String> seenKey = new HashSet<>();
                for (JSONObject slot : slots) {
                    Map<String, String> p = YgjClient.mapOf(
                            "studentID", sid, "grade", String.valueOf(grade),
                            "thisWeek", String.valueOf(week), "jc", slot.optString("jc"),
                            "xn", xn, "xnxq", xnxq,
                            "startSignMin", String.valueOf(startSignMin),
                            "stopSignMin", stopSignMin, "lateSignMin", lateSignMin,
                            "startTime", slot.optString("ts"), "school", school);
                    JSONObject cur = postJsonAuth(YgjClient.BASE
                            + "/Handler/sign.ashx?flag=getCurrentCourse", p);
                    if (cur.optInt("code") != 0) continue;
                    JSONArray arr = cur.optJSONArray("data");
                    if (arr == null) continue;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.optJSONObject(i);
                        if (c == null) continue;
                        String k2 = c.optString("KKDM") + "|" + c.optString("DDJ");
                        if (seenKey.contains(k2)) continue;
                        seenKey.add(k2);
                        list.put(c);
                    }
                }

                Set<Integer> signedNow = new HashSet<>();
                for (int i = 0; i < list.length(); i++) {
                    if (isSigned(list.getJSONObject(i))) signedNow.add(i);
                }

                final JSONArray flist = list;
                final Set<Integer> fsigned = signedNow;
                ui.post(() -> {
                    loading = false;
                    courses = flist;
                    signedSet.clear();
                    signedSet.addAll(fsigned);
                    selected.clear();
                    for (int i = 0; i < courses.length(); i++) selected.add(i);
                    rebuildRows();
                    scanBtn.setEnabled(courses.length() > 0);
                    if (courses.length() == 0) {
                        status.setText("今天没有需要签到的课程");
                        hint.setText("上课时再点右上角刷新");
                        emptyLabel.setText("今天没有需要签到的课程");
                        refreshSelection();
                        return;
                    }
                    emptyLabel.setText("");
                    status.setText("已加载 " + courses.length() + " 门课程，勾选后点“批量签到”");
                    hint.setText(windowOnly ? "窗口模式已开启：未到开窗时间的课会挂起等待"
                            : "当前不会自动提交，必须点“批量签到”或扫码");
                    refreshSelection();
                });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "加载失败" : e.getMessage();
                ui.post(() -> {
                    loading = false;
                    status.setText("TOKEN_EXPIRED".equals(msg) ? "token 已失效，请回主界面更换 token" : msg);
                    emptyLabel.setText("");
                    hint.setText("点右上角刷新重试");
                });
            }
        }).start();
    }

    /** 造码前先确认这一节是否已经签过。 */
    boolean isSigned(JSONObject c) {
        try {
            String q = YgjClient.BASE + "/Handler/sign.ashx?flag=checkStatus&"
                    + "studentID=" + enc(sid)
                    + "&kkdm=" + enc(c.optString("KKDM"))
                    + "&ddj=" + enc(c.optString("DDJ"))
                    + "&weekNum=" + enc(c.optString("weekNum", ""))
                    + "&skxq=" + enc(c.optString("SKXQ"))
                    + "&xn=" + enc(c.optString("XN"))
                    + "&xq=" + enc(c.optString("XQ"));
            JSONObject r = getJson(q);
            Object d = r.opt("data");
            if (d instanceof Boolean) return (Boolean) d;
            if (d instanceof Number) return ((Number) d).intValue() != 0;
            if (d instanceof String) return "true".equalsIgnoreCase((String) d) || "1".equals(d);
        } catch (Exception ignored) { }
        return false;
    }

    // ================= 列表 =================

    void rebuildRows() {
        listBox.removeAllViews();
        rows.clear();
        boxes.clear();
        stts.clear();

        for (int i = 0; i < courses.length(); i++) {
            final int idx = i;
            JSONObject c = courses.optJSONObject(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(round(cCard, 16));
            row.setElevation(dp(2));
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(10);
            listBox.addView(row, rlp);
            row.setOnClickListener(v -> toggleRow(idx));
            rows.add(row);

            TextView box = tv(selected.contains(idx) ? "☑" : "☐", 20, cSub, false);
            box.setPadding(0, 0, dp(10), 0);
            row.addView(box);
            boxes.add(box);

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

            String name = c == null ? "未知课程" : c.optString("KCMC", "未知课程");
            TextView nm = tv(name, 15, cInk, true);
            nm.setMaxLines(2);
            mid.addView(nm);

            String ddj = c == null ? "" : c.optString("DDJ");
            long open = windowOpen(c);
            long close = parseTime(c == null ? "" : c.optString("stopScanTime"));
            String win = (open > 0 ? hhmm(open) : "—") + "–" + (close > 0 ? hhmm(close) : "—");
            long st = parseTime(c == null ? "" : c.optString("startTime"));
            String teacher = c == null ? "" : c.optString("SKJSMC", "");
            String detail = "第" + ddj + "节 · 签到窗口 " + win
                    + "\n上课 " + (st > 0 ? hhmm(st) : "—")
                    + (teacher.isEmpty() ? "" : " · " + teacher);
            TextView dt = tv(detail, 11, cSub, false);
            dt.setPadding(0, dp(4), 0, 0);
            dt.setLineSpacing(dp(2), 1f);
            mid.addView(dt);

            TextView stt = tv(signedSet.contains(idx) ? "已签到" : "", 12, cSub, false);
            stt.setGravity(Gravity.END);
            stt.setMaxLines(2);
            row.addView(stt, new LinearLayout.LayoutParams(dp(66), -2));
            stts.add(stt);
        }
    }

    void toggleRow(int i) {
        if (signing) return;
        if (selected.contains(i)) selected.remove(i);
        else selected.add(i);
        if (i < boxes.size()) boxes.get(i).setText(selected.contains(i) ? "☑" : "☐");
        refreshSelection();
    }

    void toggleAll() {
        if (signing) return;
        if (selected.size() >= courses.length()) selected.clear();
        else for (int i = 0; i < courses.length(); i++) selected.add(i);
        for (int i = 0; i < boxes.size(); i++) boxes.get(i).setText(selected.contains(i) ? "☑" : "☐");
        refreshSelection();
    }

    void refreshSelection() {
        allBtn.setText(selected.size() >= courses.length() && courses.length() > 0 ? "全不选" : "全选");
        batchBtn.setEnabled(courses.length() > 0 && !selected.isEmpty() && !signing);
        batchBtn.setText(signing ? "处理中…"
                : (selected.isEmpty() ? "批量签到（请先勾选课程）"
                : "批量签到（已选 " + selected.size() + " 门）"));
    }

    void setStatus(int i, String text, final int color) {
        ui.post(() -> {
            if (i >= 0 && i < stts.size()) {
                stts.get(i).setText(text);
                stts.get(i).setTextColor(color);
            }
        });
    }

    // ================= 批量签到 =================

    void batchSign() {
        if (signing || courses.length() == 0) return;
        List<Integer> pick = new ArrayList<>(selected);
        pick.sort(Integer::compareTo);
        if (pick.isEmpty()) return;

        signing = true;
        refreshSelection();
        status.setText("批量签到中，请稍候…");

        new Thread(() -> {
            int ok = 0, skip = 0, wait = 0, fail = 0;
            long now = System.currentTimeMillis();
            for (int i : pick) {
                if (i >= courses.length()) continue;
                JSONObject c = courses.optJSONObject(i);
                if (signedSet.contains(i)) {
                    skip++;
                    setStatus(i, "已签到", 0xFF2E7D32);
                    continue;
                }
                long close = parseTime(c.optString("stopScanTime"));
                if (close > 0 && now >= close) {
                    fail++;
                    setStatus(i, "已过截止", 0xFFC62828);
                    continue;
                }
                long open = windowOpen(c);
                if (open > 0 && now < open) {
                    if (windowOnly) {
                        wait++;
                        pending.add(i);
                        setStatus(i, hhmm(open) + " 开窗", 0xFFEF6C00);
                        continue;
                    }
                }
                setStatus(i, "签到中…", 0xFF757575);
                String err = signOne(c);
                if (err == null) {
                    ok++;
                    signedSet.add(i);
                    setStatus(i, "✓ 成功", 0xFF2E7D32);
                    markSigned(i);
                } else {
                    fail++;
                    setStatus(i, err, 0xFFC62828);
                }
            }
            int fok = ok, fskip = skip, fwait = wait, ffail = fail;
            ui.post(() -> {
                signing = false;
                status.setText("批量签到：成功 " + fok + " · 已签 " + fskip + " · 等待 " + fwait + " · 失败 " + ffail);
                refreshSelection();
                if (!pending.isEmpty()) {
                    hint.setText("有课程在等开窗，请保持本页打开，到点后会自动提交");
                    startWindowTimer();
                } else {
                    hint.setText("");
                }
            });
        }).start();
    }

    void markSigned(int i) {
        ui.post(() -> {
            if (i < stts.size()) {
                stts.get(i).setTextColor(0xFF2E7D32);
            }
        });
    }

    /** 返回 null 表示成功，否则是错误文案。 */
    String signOne(JSONObject c) {
        String pid = processIDForCourse(c);
        if (pid == null || pid.isEmpty()) return "取进程号失败";
        String key = fetchPublicKey();
        if (key == null || key.length() != 32) return "取公钥失败";
        String qr = makeQR(pid, key);
        if (qr == null) return "造码失败";
        return submitGeneratedQR(qr);
    }

    String fetchPublicKey() {
        try {
            Map<String, String> h = authHeaders();
            h.put("Referer", YgjClient.BASE + "/ygj2024/sign/student-sign.aspx");
            YgjClient.Resp r = client.req("GET",
                    YgjClient.BASE + "/Handler/sign.ashx?flag=getPublicKey", null, h);
            JSONObject j = new JSONObject(r.body);
            JSONObject d = j.optJSONObject("data");
            if (d == null) return null;
            String k = d.optString("key", "");
            return k.length() == 32 ? k : null;
        } catch (Exception e) {
            return null;
        }
    }

    String processIDForCourse(JSONObject c) {
        try {
            Map<String, String> f = YgjClient.mapOf(
                    "kkdm", c.optString("KKDM", ""),
                    "lessonNum", c.optString("DDJ", ""),
                    "xn", c.optString("XN", ""),
                    "xq", c.optString("XQ", ""),
                    "weekNum", c.optString("weekNum", ""),
                    "skxq", c.optString("SKXQ", ""),
                    "kcmc", c.optString("KCMC", ""),
                    "sksj", c.optString("startTime", ""));
            Map<String, String> h = authHeaders();
            h.put("Referer", YgjClient.BASE + "/ygj2024/sign/student-sign.aspx");
            YgjClient.Resp r = client.req("POST",
                    YgjClient.BASE + "/Handler/sign.ashx?flag=startSignProcess",
                    YgjClient.form(f), h);
            JSONObject j = new JSONObject(r.body);
            if (j.optInt("code") != 0) return null;
            Object d = j.opt("data");
            if (d == null || d == JSONObject.NULL) return null;
            String s = String.valueOf(d);
            return "null".equals(s) ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    String submitGeneratedQR(String qr) {
        try {
            String code = qr;
            int i = code.indexOf("qrcode=");
            if (i >= 0) code = code.substring(i + 7);
            if (code.length() <= 32) return "二维码内容不正确";
            String key = code.substring(code.length() - 32);
            String content = code.substring(0, code.length() - 32);
            Map<String, String> p = YgjClient.mapOf(
                    "key", key, "content", content, "studentID", sid, "type", "1");
            Map<String, String> h = authHeaders();
            h.put("Origin", YgjClient.BASE);
            h.put("Referer", YgjClient.BASE + "/ygj2024/sign/student-sign.aspx");
            YgjClient.Resp r = client.req("POST", YgjClient.BASE
                    + "/Handler/sign.ashx?flag=saveSignNew2&studentID=" + sid, YgjClient.form(p), h);
            JSONObject j = new JSONObject(r.body);
            int c = j.optInt("code");
            if (c == 0 || c == 2) return null;
            return MainActivity.clean(j.optString("msg", "提交失败"));
        } catch (Exception e) {
            return e.getMessage() == null ? "提交失败" : e.getMessage();
        }
    }

    /** 网页端 getNowTime() 的格式：yyyy/M/d HH:mm:ss（月日不补零，东八区）。 */
    static String nowStamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return f.format(new Date());
    }

    /** pid + 时间 → AES-256-ECB/PKCS7 → base64，再拼上公钥，与网页端一致。 */
    static String makeQR(String pid, String key) {
        try {
            String plain = "\"" + pid + "," + nowStamp() + "\"";
            String b64 = QrGen.aesEcbB64(plain, key);
            if (b64 == null) return null;
            return YgjClient.BASE + "/index.aspx?qrcode=" + b64 + key;
        } catch (Exception e) {
            return null;
        }
    }

    // ================= 窗口等待 =================

    void startWindowTimer() {
        if (winTick != null) return;
        winTick = new Runnable() {
            @Override
            public void run() {
                windowTick();
                if (winTick != null) winHandler.postDelayed(this, 20000);
            }
        };
        winHandler.postDelayed(winTick, 20000);
    }

    void stopWindowTimer() {
        if (winTick != null) {
            winHandler.removeCallbacks(winTick);
            winTick = null;
        }
        ticking = false;
    }

    void windowTick() {
        if (ticking || signing) return;
        if (pending.isEmpty()) { stopWindowTimer(); return; }
        List<Integer> ready = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i : new ArrayList<>(pending)) {
            if (i >= courses.length()) { pending.remove(i); continue; }
            JSONObject c = courses.optJSONObject(i);
            long close = parseTime(c.optString("stopScanTime"));
            if (close > 0 && now >= close) {
                pending.remove(i);
                setStatus(i, "已过截止", 0xFFC62828);
                continue;
            }
            long open = windowOpen(c);
            if (open <= 0 || now >= open) ready.add(i);
        }
        if (ready.isEmpty()) {
            if (pending.isEmpty()) {
                stopWindowTimer();
                hint.setText("挂起的课程已全部处理");
            }
            return;
        }
        ticking = true;
        new Thread(() -> {
            int ok = 0, fail = 0;
            for (int i : ready) {
                pending.remove(i);
                if (i >= courses.length()) continue;
                if (signedSet.contains(i)) { setStatus(i, "已签到", 0xFF2E7D32); continue; }
                setStatus(i, "签到中…", 0xFF757575);
                String err = signOne(courses.optJSONObject(i));
                if (err == null) { ok++; signedSet.add(i); setStatus(i, "✓ 成功", 0xFF2E7D32); }
                else { fail++; setStatus(i, err, 0xFFC62828); }
            }
            final int fok = ok, ffail = fail;
            ui.post(() -> {
                ticking = false;
                status.setText("窗口到点自动签到：成功 " + fok + " · 失败 " + ffail);
                if (pending.isEmpty()) {
                    stopWindowTimer();
                    hint.setText("挂起的课程已全部处理");
                }
            });
        }).start();
    }

    // ================= 扫码 =================

    void startScan() {
        startActivityForResult(new Intent(this, ScanActivity.class), REQ_SCAN);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_TEST) {
            if (res == RESULT_OK && data != null && data.getStringExtra("qr") != null) {
                String qr = data.getStringExtra("qr");
                String show = qr.length() > 300 ? qr.substring(0, 300) + "…" : qr;
                m3Dialog("扫码测试成功", show, "好", null);
            } else {
                status.setText("已取消扫码");
            }
            return;
        }
        if (req != REQ_SCAN) return;
        if (res == RESULT_OK && data != null && data.getStringExtra("qr") != null) {
            submit(data.getStringExtra("qr"));
        } else {
            status.setText("已取消扫码");
        }
    }

    void submit(String raw) {
        status.setText("正在提交签到…");
        new Thread(() -> {
            final String err = submitGeneratedQR(raw == null ? "" : raw.trim());
            ui.post(() -> {
                if (err == null) {
                    status.setText("签到成功");
                    hint.setText("");
                } else {
                    status.setText("签到失败：" + err);
                }
            });
        }).start();
    }

    // ================= 工具 =================

    Map<String, String> authHeaders() {
        Map<String, String> h = new java.util.HashMap<>();
        h.put("X-Requested-With", "XMLHttpRequest");
        return h;
    }

    JSONObject getJson(String url) throws Exception {
        YgjClient.Resp r = client.req("GET", url, null, authHeaders());
        return new JSONObject(r.body);
    }

    JSONObject postJsonAuth(String url, Map<String, String> form) throws Exception {
        Map<String, String> h = authHeaders();
        h.put("Origin", YgjClient.BASE);
        h.put("Referer", YgjClient.BASE + "/ygj2024/sign/student-sign.aspx");
        YgjClient.Resp r = client.req("POST", url, YgjClient.form(form), h);
        return new JSONObject(r.body);
    }

    JSONObject postJson(String url, Map<String, String> form) throws Exception {
        YgjClient.Resp r = client.req("POST", url, YgjClient.form(form), authHeaders());
        return new JSONObject(r.body);
    }

    long windowOpen(JSONObject c) {
        if (c == null) return 0;
        long st = parseTime(c.optString("startTime"));
        if (st <= 0) return 0;
        return st - startSignMin * 60000L;
    }

    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static long parseTime(String s) {
        if (s == null) return 0;
        String t = s.trim().replace('/', '-');
        if (t.length() >= 19) t = t.substring(0, 19);
        else if (t.length() == 16) t = t + ":00";
        String[] fmts = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String f : fmts) {
            try {
                return new SimpleDateFormat(f, Locale.US).parse(t).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    static String hhmm(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    static int grade(String yearIn) {
        int y;
        try { y = Integer.parseInt(yearIn.trim()); } catch (Exception e) { y = 2025; }
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1;
        return month >= 9 ? year - y + 1 : year - y;
    }

    static int weekNum(String firstWeekDate) {
        try {
            String t = firstWeekDate.trim();
            SimpleDateFormat f = new SimpleDateFormat(t.contains("/") ? "yyyy/M/d H:mm:ss" : "yyyy-MM-dd HH:mm:ss", Locale.US);
            Date d = f.parse(t);
            Calendar start = Calendar.getInstance();
            start.setTime(d);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            int dow = start.get(Calendar.DAY_OF_WEEK);
            int shift = dow == Calendar.SUNDAY ? -6 : Calendar.MONDAY - dow;
            start.add(Calendar.DAY_OF_MONTH, shift);
            Calendar now = Calendar.getInstance();
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            long days = (now.getTimeInMillis() - start.getTimeInMillis()) / 86400000L;
            int w = (int) Math.ceil((days + 1) / 7.0);
            return Math.max(w, 1);
        } catch (Exception e) {
            return 1;
        }
    }

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    TextView tv(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    Button pill(String s, int color, float radiusDp, float size) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(color, radiusDp));
        b.setPadding(0, 0, 0, 0);
        try {
            b.setStateListAnimator(null);
            b.setElevation(0);
        } catch (Throwable ignored) {
        }
        return b;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** M3 风格对话框：圆角卡片 + pill 按钮，跟随深浅色 */
    void m3Dialog(String title, String msg, String posText, Runnable posAct) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(cCard, 24));
        box.setPadding(dp(22), dp(22), dp(22), dp(16));
        box.setElevation(dp(10));

        box.addView(tv(title, 19, cInk, true));
        if (msg != null && !msg.isEmpty()) {
            TextView m = tv(msg, 13, cSub, false);
            m.setLineSpacing(dp(7), 1f);
            m.setPadding(0, dp(10), 0, 0);
            box.addView(m);
        }

        Button pos = pill(posText, cPrimary, 24, 14);
        pos.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, dp(48));
        plp.topMargin = dp(18);
        plp.gravity = Gravity.END;
        box.addView(pos, plp);
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
