package com.example.screenreader;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingService";
    private WindowManager windowManager;
    private View floatingBall;
    private View contentPanel;
    private TextView tvPanelContent;
    private boolean isPanelVisible = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingBall();
        registerReceivers();
        Log.d(TAG, "悬浮球服务已创建");
    }

    /** 创建悬浮球 */
    private void createFloatingBall() {
        int size = dp2px(50);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xDD4CAF50);
        bg.setStroke(dp2px(2), Color.WHITE);

        floatingBall = new View(this);
        floatingBall.setBackground(bg);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = dp2px(300);

        windowManager.addView(floatingBall, params);
        setupTouch(floatingBall, params);
    }

    /** 设置拖拽、点击、长按 */
    private void setupTouch(final View view, final WindowManager.LayoutParams params) {
        final float[] initialTouch = new float[2];
        final float[] initialPos = new float[2];
        final long[] downTime = new long[1];
        final boolean[] moved = {false};
        final android.os.Handler handler = new android.os.Handler();
        final boolean[] longPressed = {false};
        final int clickThreshold = dp2px(10);

        final Runnable longPressRunnable = () -> {
            longPressed[0] = true;
            // 长按：切换面板显示
            if (isPanelVisible) {
                hidePanel();
            } else {
                showPanel();
            }
        };

        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouch[0] = event.getRawX();
                    initialTouch[1] = event.getRawY();
                    initialPos[0] = params.x;
                    initialPos[1] = params.y;
                    downTime[0] = System.currentTimeMillis();
                    moved[0] = false;
                    longPressed[0] = false;
                    handler.postDelayed(longPressRunnable, 600);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouch[0];
                    float dy = event.getRawY() - initialTouch[1];
                    if (Math.abs(dx) > clickThreshold || Math.abs(dy) > clickThreshold) {
                        moved[0] = true;
                        handler.removeCallbacks(longPressRunnable);
                    }
                    if (moved[0]) {
                        params.x = (int) (initialPos[0] + dx);
                        params.y = (int) (initialPos[1] + dy);
                        windowManager.updateViewLayout(view, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    if (!moved[0] && !longPressed[0]) {
                        // 短点击：切换面板
                        if (isPanelVisible) {
                            hidePanel();
                        } else {
                            showPanel();
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    /** 显示内容面板 */
    private void showPanel() {
        if (contentPanel != null) {
            contentPanel.setVisibility(View.VISIBLE);
            isPanelVisible = true;
            return;
        }

        int panelWidth = dp2px(320);
        int panelHeight = dp2px(480);

        // 主容器
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setElevation(dp2px(8));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(dp2px(12));
        panelBg.setColor(0xF5FFFFFF);
        root.setBackground(panelBg);
        root.setClipToOutline(true);

        // 标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0xFF4CAF50);
        header.setPadding(dp2px(12), dp2px(10), dp2px(8), dp2px(10));
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("屏幕内容");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        TextView btnClose = new TextView(this);
        btnClose.setText("收起 ✕");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnClose.setPadding(dp2px(8), dp2px(4), dp2px(8), dp2px(4));
        btnClose.setOnClickListener(v -> hidePanel());
        header.addView(btnClose);

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 内容区域
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(dp2px(10), dp2px(8), dp2px(10), dp2px(8));

        tvPanelContent = new TextView(this);
        tvPanelContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvPanelContent.setTextColor(0xFF333333);
        tvPanelContent.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvPanelContent.setText(ScreenReaderService.lastContent != null
                ? ScreenReaderService.lastContent : "等待获取屏幕内容...");

        scrollView.addView(tvPanelContent);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // 添加到窗口
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                panelWidth, panelHeight,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        contentPanel = root;
        windowManager.addView(contentPanel, params);
        isPanelVisible = true;
    }

    /** 隐藏内容面板 */
    private void hidePanel() {
        if (contentPanel != null) {
            contentPanel.setVisibility(View.GONE);
        }
        isPanelVisible = false;
    }

    /** 获取合适的窗口类型 */
    private int getWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    /** 注册广播接收器 */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter(ScreenReaderService.ACTION_CONTENT_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(contentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(contentReceiver, filter);
        }
    }

    private final BroadcastReceiver contentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String content = intent.getStringExtra(ScreenReaderService.EXTRA_CONTENT);
            if (content != null && tvPanelContent != null && isPanelVisible) {
                tvPanelContent.setText(content);
            }
        }
    };

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(contentReceiver);
        } catch (Exception ignored) {}
        if (floatingBall != null) windowManager.removeView(floatingBall);
        if (contentPanel != null) windowManager.removeView(contentPanel);
        Log.d(TAG, "悬浮球服务已销毁");
    }
}
