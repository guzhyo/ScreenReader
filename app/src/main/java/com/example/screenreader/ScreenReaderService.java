package com.example.screenreader;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class ScreenReaderService extends AccessibilityService {

    private static final String TAG = "ScreenReaderService";
    private static final String CHANNEL_ID = "screen_reader_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final long REFRESH_INTERVAL = 2000;
    private static final long FLOATING_RETRY_INTERVAL = 5000;

    public static final String ACTION_CONTENT_UPDATE = "com.example.screenreader.CONTENT_UPDATE";
    public static final String ACTION_STATUS_UPDATE = "com.example.screenreader.STATUS_UPDATE";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_IS_RUNNING = "is_running";

    /** 静态标志，供 Activity 直接查询服务状态 */
    public static volatile boolean isRunning = false;
    public static volatile String lastContent = "";

    private StringBuilder capturedContent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFloatingStarted = false;

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            captureScreenContent();
            handler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    /** 延迟重试启动悬浮球（等待用户授予悬浮窗权限） */
    private final Runnable retryFloating = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isFloatingStarted) {
                tryStartFloatingWindow();
                if (!isFloatingStarted) {
                    handler.postDelayed(this, FLOATING_RETRY_INTERVAL);
                }
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");

        try {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.DEFAULT;
            info.notificationTimeout = 200;
            setServiceInfo(info);
        } catch (Exception e) {
            Log.e(TAG, "设置服务参数失败", e);
        }

        capturedContent = new StringBuilder();
        isRunning = true;

        try {
            createNotificationChannel();
            startForegroundNotification();
        } catch (Exception e) {
            Log.e(TAG, "启动前台通知失败", e);
        }

        broadcastStatus(true);

        // 尝试启动悬浮球（如果权限不足会延迟重试）
        tryStartFloatingWindow();

        // 启动定时刷新
        handler.postDelayed(periodicRefresh, REFRESH_INTERVAL);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            int eventType = event.getEventType();
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                captureScreenContent();
            }
        } catch (Exception e) {
            Log.e(TAG, "处理无障碍事件失败", e);
        }
    }

    /** 安全地尝试启动悬浮球 */
    private void tryStartFloatingWindow() {
        if (isFloatingStarted) return;

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "悬浮窗权限未授予，将在 " + (FLOATING_RETRY_INTERVAL / 1000) + " 秒后重试");
                handler.postDelayed(retryFloating, FLOATING_RETRY_INTERVAL);
                return;
            }
        }

        try {
            isFloatingStarted = true;
            Intent intent = new Intent(this, FloatingWindowService.class);
            startService(intent);
            Log.d(TAG, "悬浮球服务已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动悬浮球失败", e);
            isFloatingStarted = false;
            handler.postDelayed(retryFloating, FLOATING_RETRY_INTERVAL);
        }
    }

    /** 获取当前屏幕内容 */
    private void captureScreenContent() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return;

            capturedContent = new StringBuilder();
            CharSequence packageName = rootNode.getPackageName();
            if (packageName != null) {
                capturedContent.append("【").append(packageName).append("】\n");
            }
            traverseNode(rootNode, 0);
            rootNode.recycle();

            String content = capturedContent.toString();
            lastContent = content;
            broadcastContent(content);
        } catch (Exception e) {
            Log.e(TAG, "抓取屏幕内容失败", e);
        }
    }

    /** 递归遍历节点树 */
    private void traverseNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 50) return;

        CharSequence text = node.getText();
        CharSequence contentDesc = node.getContentDescription();
        CharSequence className = node.getClassName();

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");

        if (text != null && text.length() > 0) {
            capturedContent.append(indent)
                    .append("[").append(shortName(className)).append("] ")
                    .append(text).append("\n");
        } else if (contentDesc != null && contentDesc.length() > 0) {
            capturedContent.append(indent)
                    .append("[").append(shortName(className)).append("][描述] ")
                    .append(contentDesc).append("\n");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child, depth + 1);
                child.recycle();
            }
        }
    }

    private String shortName(CharSequence cls) {
        if (cls == null) return "?";
        String name = cls.toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private void broadcastContent(String content) {
        try {
            Intent intent = new Intent(ACTION_CONTENT_UPDATE);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_CONTENT, content);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "广播内容失败", e);
        }
    }

    private void broadcastStatus(boolean running) {
        try {
            Intent intent = new Intent(ACTION_STATUS_UPDATE);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_IS_RUNNING, running);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "广播状态失败", e);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "屏幕读取服务", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("无障碍服务运行状态通知");
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.createNotificationChannel(channel);
    }

    private void startForegroundNotification() {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕读取服务运行中")
                .setContentText("无障碍服务已激活")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务被中断");
        cleanup();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "无障碍服务已断开");
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
        Log.d(TAG, "无障碍服务已销毁");
    }

    private void cleanup() {
        isRunning = false;
        lastContent = "";
        handler.removeCallbacks(periodicRefresh);
        handler.removeCallbacks(retryFloating);
        broadcastStatus(false);
        try {
            stopService(new Intent(this, FloatingWindowService.class));
        } catch (Exception ignored) {}
    }
}
