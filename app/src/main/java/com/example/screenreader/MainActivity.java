package com.example.screenreader;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button btnToggleService;
    private Button btnCapture;
    private TextView tvStatus;
    private TextView tvContent;
    private ScrollView scrollView;

    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        btnToggleService = findViewById(R.id.btn_toggle_service);
        btnCapture = findViewById(R.id.btn_capture);
        tvStatus = findViewById(R.id.tv_status);
        tvContent = findViewById(R.id.tv_content);
        scrollView = findViewById(R.id.scroll_view);
    }

    /**
     * 设置按钮点击事件
     */
    private void setupListeners() {
        // 切换无障碍服务开关（跳转到系统设置页面）
        btnToggleService.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // 手动触发一次屏幕内容抓取
        btnCapture.setOnClickListener(v -> {
            Intent intent = new Intent(ScreenReaderService.ACTION_REQUEST_CAPTURE);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        });
    }

    /**
     * 检查无障碍服务是否已启用
     */
    private void checkServiceStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        updateServiceUI(enabled);
    }

    /**
     * 判断无障碍服务是否正在运行
     */
    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        if (enabledServices == null) return false;

        String myServiceName = getPackageName() + "/" + ScreenReaderService.class.getName();
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据服务状态更新界面
     */
    private void updateServiceUI(boolean running) {
        isServiceRunning = running;
        if (running) {
            tvStatus.setText("状态：服务运行中");
            btnToggleService.setText("前往设置（服务已开启）");
            btnCapture.setEnabled(true);
        } else {
            tvStatus.setText("状态：服务未开启");
            btnToggleService.setText("开启无障碍服务");
            btnCapture.setEnabled(false);
            tvContent.setText("请先在系统设置中开启无障碍服务");
        }
    }

    /**
     * 注册广播接收器，监听来自 AccessibilityService 的数据
     */
    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter contentFilter = new IntentFilter(ScreenReaderService.ACTION_CONTENT_UPDATE);
        IntentFilter statusFilter = new IntentFilter(ScreenReaderService.ACTION_STATUS_UPDATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(contentReceiver, contentFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(contentReceiver, contentFilter);
            registerReceiver(statusReceiver, statusFilter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(contentReceiver);
            unregisterReceiver(statusReceiver);
        } catch (Exception e) {
            // 忽略未注册的异常
        }
    }

    /**
     * 接收屏幕内容的广播
     */
    private final BroadcastReceiver contentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String content = intent.getStringExtra(ScreenReaderService.EXTRA_CONTENT);
            if (content != null) {
                tvContent.setText(content);
                // 自动滚动到底部
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
    };

    /**
     * 接收服务状态的广播
     */
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean running = intent.getBooleanExtra(ScreenReaderService.EXTRA_IS_RUNNING, false);
            updateServiceUI(running);
        }
    };
}
