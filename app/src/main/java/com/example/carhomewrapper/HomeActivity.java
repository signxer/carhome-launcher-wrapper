package com.example.carhomewrapper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/**
 * The real Home entry point. It forwards Home launches to the installed CARHOME APK.
 */
public final class HomeActivity extends Activity {
    private static final String TARGET_PACKAGE = "com.paopao.lpyouthlaunch";
    private static final String TARGET_ACTIVITY = "com.carhome.HomeEntryActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        routeHome();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        routeHome();
    }

    private void routeHome() {
        if (!isBootstrapComplete()) {
            Intent setup = new Intent(this, SettingsActivity.class);
            setup.putExtra(SettingsActivity.EXTRA_AUTO_SETUP, true);
            startActivity(setup);
            finish();
        } else {
            launchTargetOrFallback();
        }
    }

    private void launchTargetOrFallback() {
        Intent target = new Intent();
        target.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try {
            startActivity(target);
            finish();
        } catch (RuntimeException unavailable) {
            Intent fallback = new Intent(this, SettingsActivity.class);
            fallback.putExtra(SettingsActivity.EXTRA_ERROR, unavailable.getClass().getSimpleName());
            startActivity(fallback);
            finish();
        }
    }

    private boolean isBootstrapComplete() {
        return getSharedPreferences(SettingsActivity.BOOTSTRAP_PREFS, MODE_PRIVATE)
                .getBoolean(SettingsActivity.BOOTSTRAP_DONE, false);
    }
}
