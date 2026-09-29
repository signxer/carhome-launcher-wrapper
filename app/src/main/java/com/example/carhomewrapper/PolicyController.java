package com.example.carhomewrapper;

import android.content.Context;
import android.provider.Settings;

/**
 * Android 9's legacy SystemUI policy switch. This changes the system bars for
 * the target package without modifying the target APK.
 */
public final class PolicyController {
    public static final String TARGET_PACKAGE = "com.paopao.lpyouthlaunch";
    private static final String POLICY_CONTROL = "policy_control";
    private static final String PREFS = "policy_snapshot";
    private static final String ORIGINAL_VALUE = "original_value";

    private PolicyController() {
    }

    public enum Mode {
        DESKTOP,
        TRIPLE_SCREEN
    }

    public static String read(Context context) {
        return Settings.Global.getString(
                context.getContentResolver(), POLICY_CONTROL);
    }

    public static void apply(Context context, Mode mode) {
        saveSnapshotIfNeeded(context);

        // Android 9 accepts a package name as a policy filter. The target is
        // affected, while other applications keep their existing behavior.
        String value = mode == Mode.DESKTOP
                ? "immersive.status=" + TARGET_PACKAGE
                : "immersive.full=" + TARGET_PACKAGE;

        try {
            if (!Settings.Global.putString(
                    context.getContentResolver(), POLICY_CONTROL, value)) {
                throw new IllegalStateException("Settings.Global.putString returned false");
            }
        } catch (SecurityException error) {
            throw new SecurityException(
                    "没有 WRITE_SECURE_SETTINGS 权限，请先执行 ADB 授权命令", error);
        }
    }

    public static void restore(Context context) {
        String original = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(ORIGINAL_VALUE, null);
        try {
            Settings.Global.putString(
                    context.getContentResolver(), POLICY_CONTROL,
                    original == null || original.length() == 0 ? null : original);
        } catch (SecurityException error) {
            throw new SecurityException(
                    "没有 WRITE_SECURE_SETTINGS 权限，请先执行 ADB 授权命令", error);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(ORIGINAL_VALUE)
                .apply();
    }

    private static void saveSnapshotIfNeeded(Context context) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(ORIGINAL_VALUE)) {
            String current = read(context);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    // An empty string represents the old unset state.
                    .putString(ORIGINAL_VALUE, current == null ? "" : current)
                    .apply();
        }
    }
}
