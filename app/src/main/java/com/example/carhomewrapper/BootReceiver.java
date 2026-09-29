package com.example.carhomewrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the default Home flow after boot so the helper can initialize once. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        boolean complete = context.getSharedPreferences(
                SettingsActivity.BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
                .getBoolean(SettingsActivity.BOOTSTRAP_DONE, false);
        Intent next = new Intent(context, complete ? HomeActivity.class : SettingsActivity.class);
        if (!complete) next.putExtra(SettingsActivity.EXTRA_AUTO_SETUP, true);
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(next);
    }
}
