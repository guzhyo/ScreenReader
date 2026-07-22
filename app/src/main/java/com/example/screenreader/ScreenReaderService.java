package com.example.screenreader;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class ScreenReaderService extends AccessibilityService {

    private static final String TAG = "ScreenReaderService";
    private static final String CHANNEL_ID = "screen_reader_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_CONTENT_UPDATE = "com.example.screenreader.CONTENT_UPDATE";
    public static final String ACTION_STATUS_UPDATE = "com.example.screenreader.STATUS_UPDATE";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_IS_RUNNING = "is_running";
    public static final String ACTION_REQUEST_CAPTURE = "com.example.screenreader.REQUEST_CAPTURE";

    private StringBuilder capturedContent;
    private boolean isRunning = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");

        // 配置服务参数
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.notificationTimeout = 300;
        setServiceInfo(info);

        capturedContent = new StringBuilder();
        isRunning = true;

        createNotificationChannel();
        startForegroundNotification();
        broadcastStatus(true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();

        // 窗口状态变化时重新获取屏幕内容
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            captureScreenContent();
        }
    }

    /**
     * 获取当前屏幕内容
     */
    private void captureScreenContent() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "无法获取窗口根节点");
            return;
        }

        capturedContent = new StringBuilder();
        traverseNode(rootNode, 0);
        rootNode.recycle();

        String content = capturedContent.toString();
        Log.d(TAG, "屏幕内容已更新，共 " + content.length() + " 字符");
        broadcastContent(content);
    }

    /**
     * 递归遍历 AccessibilityNodeInfo 节点树，提取文本内容
     */
    private void traverseNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 50) return;

        // 获取节点文本
        CharSequence text = node.getText();
        CharSequence contentDescription = node.getContentDescription();
        CharSequence className = node.getClassName();

        // 缩进表示层级
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }

        if (text != null && text.length() > 0) {
            capturedContent.append(indent)
                    .append("[")
                    .append(getShortClassName(className))
                    .append("] ")
                    .append(text)
                    .append("\n");
        } else if (contentDescription != null && contentDescription.length() > 0) {
            capturedContent.append(indent)
                    .append("[")
                    .append(getShortClassName(className))
                    .append("][描述] ")
                    .append(contentDescription)
                    .append("\n");
        }

        // 递归遍历子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child, depth + 1);
                child.recycle();
            }
        }
    }

    /**
     * 获取简短的类名（去掉包名前缀）
     */
    private String getShortClassName(CharSequence className) {
        if (className == null) return "Unknown";
        String name = className.toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }

    /**
     * 向 MainActivity 广播屏幕内容
     */
    private void broadcastContent(String content) {
        Intent intent = new Intent(ACTION_CONTENT_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_CONTENT, content);
        sendBroadcast(intent);
    }

    /**
     * 广播服务运行状态
     */
    private void broadcastStatus(boolean running) {
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_IS_RUNNING, running);
        sendBroadcast(intent);
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "屏幕读取服务",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("无障碍服务运行状态通知");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * 启动前台通知，防止服务被系统杀死
     */
    private void startForegroundNotification() {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕读取服务运行中")
                .setContentText("正在使用无障碍服务获取屏幕内容")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务被中断");
        isRunning = false;
        broadcastStatus(false);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "无障碍服务已断开");
        isRunning = false;
        broadcastStatus(false);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        broadcastStatus(false);
        Log.d(TAG, "无障碍服务已销毁");
    }
}
