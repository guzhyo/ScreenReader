package com.example.screenreader;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button btnAccessibility;
    private Button btnOverlay;
    private Button btnMinimize;
    private TextView tvStatus;
    private TextView tvDetail;

    private static final int REQUEST_OVERLAY = 1001;

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
        refreshStatus();
    }

    private void initViews() {
        btnAccessibility = findViewById(R.id.btn_accessibility);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnMinimize = findViewById(R.id.btn_minimize);
        tvStatus = findViewById(R.id.tv_status);
        tvDetail = findViewById(R.id.tv_detail);
    }

    private void setupListeners() {
        // 按钮1：跳转无障碍设置
        btnAccessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // 按钮2：跳转悬浮窗权限设置
        btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
        });

        // 按钮3：最小化到后台（悬浮球继续工作）
        btnMinimize.setOnClickListener(v -> moveTaskToBack(true));
    }

    /** 刷新所有权限状态和 UI */
    private void refreshStatus() {
        boolean a11y = isAccessibilityEnabled();
        boolean overlay = canDrawOverlay();

        // 使用静态标志做辅助判断（解决部分机型检测不准的问题）
        if (ScreenReaderService.isRunning) a11y = true;

        // 更新无障碍按钮
        if (a11y) {
            btnAccessibility.setText("✓ 无障碍服务已开启");
            btnAccessibility.setBackgroundColor(0xFF4CAF50);
        } else {
            btnAccessibility.setText("开启无障碍服务");
            btnAccessibility.setBackgroundColor(0xFF2196F3);
        }

        // 更新悬浮窗按钮
        if (overlay) {
            btnOverlay.setText("✓ 悬浮窗权限已开启");
            btnOverlay.setBackgroundColor(0xFF4CAF50);
        } else {
            btnOverlay.setText("开启悬浮窗权限");
            btnOverlay.setBackgroundColor(0xFFFF9800);
        }

        // 更新最小化按钮
        btnMinimize.setEnabled(a11y && overlay);
        if (a11y && overlay) {
            btnMinimize.setText("最小化（悬浮球工作中）");
            btnMinimize.setBackgroundColor(0xFF4CAF50);
        } else {
            btnMinimize.setText("请先开启以上权限");
            btnMinimize.setBackgroundColor(0xFF9E9E9E);
        }

        // 更新状态文字
        if (a11y && overlay) {
            tvStatus.setText("全部就绪！点击「最小化」后使用悬浮球查看屏幕内容");
            tvDetail.setText("• 悬浮球：点击展开内容面板，拖拽移动位置\n"
                    + "• 内容面板：实时显示当前屏幕的UI元素\n"
                    + "• 收起面板：点击面板右上角「收起」或再次点击悬浮球");
        } else if (a11y) {
            tvStatus.setText("还需开启悬浮窗权限");
            tvDetail.setText("悬浮窗权限用于显示悬浮球，请点击上方按钮授权");
        } else {
            tvStatus.setText("请先开启无障碍服务");
            tvDetail.setText("在系统设置中找到「屏幕读取器」并启用");
        }
    }

    /** 检测无障碍服务是否启用 */
    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> services =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (services == null) return false;
        for (AccessibilityServiceInfo s : services) {
            if (s.getId() != null && s.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    /** 检测悬浮窗权限 */
    private boolean canDrawOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            refreshStatus();
        }
    }

    // 广播接收器（保留兼容）
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter statusFilter = new IntentFilter(ScreenReaderService.ACTION_STATUS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, statusFilter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshStatus();
        }
    };
}
