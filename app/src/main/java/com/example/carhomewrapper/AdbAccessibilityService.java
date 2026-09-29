package com.example.carhomewrapper;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

/**
 * Small, deliberately conservative Settings-page helper. It only runs on a
 * Settings package and only clicks nodes whose text clearly describes a
 * wireless ADB/debugging switch.
 */
public final class AdbAccessibilityService extends AccessibilityService {
    private static final String SETTINGS_PACKAGE_PREFIX = "com.android.settings";
    private static final String TARGET_PACKAGE = PolicyController.TARGET_PACKAGE;
    private long lastClickAt;
    private long lastModeChangeAt;
    private boolean hasClickedWirelessAdb;
    private PolicyController.Mode lastMode;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String packageName = event.getPackageName().toString();
        if (packageName.startsWith(SETTINGS_PACKAGE_PREFIX)) {
            handleSettingsPage();
            return;
        }
        if (!TARGET_PACKAGE.equals(packageName)) {
            hasClickedWirelessAdb = false;
            return;
        }
        hasClickedWirelessAdb = false;
        handleTargetPage();
    }

    private void handleSettingsPage() {
        if (hasClickedWirelessAdb) return;
        if (System.currentTimeMillis() - lastClickAt < 1200L) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        if (clickWirelessAdbSwitch(root)) {
            lastClickAt = System.currentTimeMillis();
            hasClickedWirelessAdb = true;
        }
    }

    /**
     * The launcher has one Activity and changes mode internally. Accessibility
     * is therefore more useful than the top-Activity name: Compose semantics
     * can expose the mode hint even though the Activity never changes.
     */
    private void handleTargetPage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        StringBuilder visibleText = new StringBuilder(2048);
        collectText(root, visibleText, new int[]{0});
        String text = visibleText.toString().toLowerCase(Locale.ROOT);

        boolean tripleScreen = containsAny(text,
                "双指左滑，进入三分屏", "三分屏", "三联屏", "分屏布局");
        boolean desktop = containsAny(text,
                "双指右滑，回到主屏", "壁纸与车辆信息", "桌面模式");

        // Do not guess when both mode descriptions are present in a menu.
        if (tripleScreen == desktop || (!tripleScreen && !desktop)) return;
        PolicyController.Mode mode = tripleScreen
                ? PolicyController.Mode.TRIPLE_SCREEN : PolicyController.Mode.DESKTOP;
        if (mode == lastMode && System.currentTimeMillis() - lastModeChangeAt < 3000L) return;
        try {
            PolicyController.apply(getApplicationContext(), mode);
            lastMode = mode;
            lastModeChangeAt = System.currentTimeMillis();
        } catch (RuntimeException ignored) {
            // The UI still works when the user has not granted secure settings.
        }
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder output, int[] count) {
        if (node == null || count[0]++ > 600 || output.length() > 18000) return;
        if (node.getText() != null) output.append(node.getText()).append(' ');
        if (node.getContentDescription() != null) {
            output.append(node.getContentDescription()).append(' ');
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), output, count);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to cancel; this service has no background loop.
    }

    private boolean clickWirelessAdbSwitch(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String value = ((text == null ? "" : text.toString()) + " "
                + (description == null ? "" : description.toString()))
                .toLowerCase(Locale.ROOT);

        boolean adbLabel = containsAny(value,
                "无线调试", "无线 adb", "wifi adb", "wi-fi adb", "wireless debugging",
                "wifi debugging", "adb over network", "adb over wifi", "网络调试");
        if (adbLabel && node.isEnabled()) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) {
                clickable = clickable.getParent();
            }
            if (clickable != null && clickable.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && clickWirelessAdbSwitch(child)) return true;
        }
        return false;
    }

    private boolean containsAny(String source, String... values) {
        for (String value : values) {
            if (source.contains(value)) return true;
        }
        return false;
    }
}
