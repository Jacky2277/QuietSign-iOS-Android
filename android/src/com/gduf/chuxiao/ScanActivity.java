package com.gduf.chuxiao;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 极简二维码扫描：Camera1 预览 + ZXing 解码。 */
public class ScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    static final int REQ_CAM = 7;

    SurfaceView surface;
    Camera camera;
    final MultiFormatReader reader = new MultiFormatReader();
    final ExecutorService pool = Executors.newSingleThreadExecutor();
    final Handler ui = new Handler(Looper.getMainLooper());
    Camera.Size PreviewSize;
    volatile boolean busy = false;
    volatile boolean found = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        TextView hint = new TextView(this);
        hint.setText("把二维码放进取景框内\n扫到后会自动返回");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setShadowLayer(4, 0, 0, Color.BLACK);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2);
        hp.gravity = Gravity.TOP;
        hp.topMargin = dp(70);
        root.addView(hint, hp);

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextColor(Color.WHITE);
        cancel.setBackgroundColor(0x33000000);
        cancel.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(120), dp(46));
        cp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cp.bottomMargin = dp(48);
        root.addView(cancel, cp);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            surface.getHolder().addCallback(this);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == REQ_CAM) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
                surface.getHolder().addCallback(this);
            } else {
                Toast.makeText(this, "没有相机权限，无法扫码", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        openCamera(holder);
    }

    void openCamera(SurfaceHolder holder) {
        if (camera != null || found) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "没有相机权限，无法扫码", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            camera = Camera.open();
            Camera.Parameters p = camera.getParameters();
            p.setPreviewFormat(android.graphics.ImageFormat.NV21);
            Camera.Size best = null;
            List<Camera.Size> sizes = p.getSupportedPreviewSizes();
            for (Camera.Size s : sizes) {
                if (s.width <= 1280 && s.height <= 960) {
                    if (best == null || (long) s.width * s.height > (long) best.width * best.height) best = s;
                }
            }
            if (best != null) p.setPreviewSize(best.width, best.height);
            PreviewSize = p.getPreviewSize();
            List<String> modes = p.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);
            camera.setPreviewDisplay(holder);
            camera.setDisplayOrientation(90);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "相机打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onPreviewFrame(final byte[] data, Camera cam) {
        if (busy || found || data == null) return;
        busy = true;
        final Camera.Size sz = PreviewSize;
        if (sz == null) { busy = false; return; }
        pool.execute(() -> {
            String text = decode(data, sz.width, sz.height);
            if (text == null) text = decode(rotate90(data, sz.width, sz.height), sz.height, sz.width);
            busy = false;
            if (text != null) {
                final String out = text;
                ui.post(() -> finishWith(out));
            }
        });
    }

    String decode(byte[] data, int w, int h) {
        try {
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(src));
            Result r = reader.decodeWithState(bitmap);
            return r == null ? null : r.getText();
        } catch (Exception ignored) {
            reader.reset();
            return null;
        }
    }

    static byte[] rotate90(byte[] d, int w, int h) {
        byte[] o = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                o[x * h + (h - 1 - y)] = d[row + x];
            }
        }
        return o;
    }

    void finishWith(String text) {
        if (found) return;
        found = true;
        release();
        Intent i = new Intent();
        i.putExtra("qr", text);
        setResult(RESULT_OK, i);
        finish();
    }

    void release() {
        try {
            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            }
        } catch (Exception ignored) { }
        camera = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
        pool.shutdownNow();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        release();
    }

    int dp(float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
