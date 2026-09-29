package com.example.carhomewrapper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Setup and fallback screen for the launcher helper. */
public final class SettingsActivity extends Activity {
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_AUTO_SETUP = "auto_setup";
    public static final String BOOTSTRAP_PREFS = "bootstrap";
    public static final String BOOTSTRAP_DONE = "adb_granted";
    private TextView status;
    private EditText adbHost;
    private EditText adbPort;
    private final ExecutorService adbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(40, 36, 40, 36);

        TextView title = new TextView(this);
        title.setText("跑跑桌面辅助工具");
        title.setTextColor(Color.DKGRAY);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 24);
        content.addView(title);

        TextView message = new TextView(this);
        message.setTextColor(Color.DKGRAY);
        message.setTextSize(16);
        message.setPadding(0, 0, 0, 20);

        String error = getIntent().getStringExtra(EXTRA_ERROR);
        if (error == null) {
            message.setText("不修改原 APK，通过 Android 9 的系统栏策略辅助跑跑桌面。\n\n目标应用："
                    + PolicyController.TARGET_PACKAGE);
        } else {
            message.setText("无法启动跑跑桌面。\n\n请确认原 APK 已安装，并且设备支持它的车机依赖。\n\n错误类型：" + error);
        }
        content.addView(message);

        status = new TextView(this);
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(14);
        status.setPadding(0, 0, 0, 20);
        content.addView(status);

        addAction(content, "主页模式：隐藏顶部栏，保留底栏", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyMode(PolicyController.Mode.DESKTOP);
            }
        });
        addAction(content, "三分屏模式：顶部栏和底栏都隐藏", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyMode(PolicyController.Mode.TRIPLE_SCREEN);
            }
        });
        addAction(content, "恢复系统原来的系统栏设置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    PolicyController.restore(SettingsActivity.this);
                    showStatus("已恢复。当前 policy_control：" + displayPolicy());
                } catch (RuntimeException error) {
                    showStatus(error.getMessage());
                }
            }
        });
        addAction(content, "打开无障碍设置（首次使用自动 ADB 前开启）", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            }
        });
        addAction(content, "打开无线调试 / ADB 设置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // This action is standard on Android 11+, and may be supplied
                // by the car vendor on Android 9. The fallback is deliberate.
                openSettings("android.settings.WIFI_ADB_SETTINGS");
            }
        });

        TextView adbTitle = new TextView(this);
        adbTitle.setText("ADB 连接地址（车机已开启 TCP ADB 时使用）");
        adbTitle.setTextColor(Color.DKGRAY);
        adbTitle.setTextSize(15);
        adbTitle.setPadding(0, 18, 0, 4);
        content.addView(adbTitle);

        LinearLayout endpoint = new LinearLayout(this);
        endpoint.setOrientation(LinearLayout.HORIZONTAL);
        adbHost = new EditText(this);
        adbHost.setSingleLine(true);
        adbHost.setHint("127.0.0.1 或车机 IP");
        adbHost.setText("127.0.0.1");
        endpoint.addView(adbHost, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        adbPort = new EditText(this);
        adbPort.setSingleLine(true);
        adbPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        adbPort.setHint("5555");
        adbPort.setText("5555");
        endpoint.addView(adbPort, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f));
        content.addView(endpoint);

        addAction(content, "连接 ADB 并授权系统栏控制", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectAndGrant();
            }
        });
        addAction(content, "打开跑跑桌面", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchTarget();
            }
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        setContentView(scrollView);
        showStatus("当前 policy_control：" + displayPolicy()
                + "\n首次使用前请完成 README 中的 ADB 授权。");

        if (getIntent().getBooleanExtra(EXTRA_AUTO_SETUP, false)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    openSettings("android.settings.WIFI_ADB_SETTINGS");
                }
            }, 600L);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    connectAndGrant();
                }
            }, 4500L);
        }
    }

    @Override
    protected void onDestroy() {
        adbExecutor.shutdownNow();
        super.onDestroy();
    }

    private void addAction(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void applyMode(PolicyController.Mode mode) {
        try {
            PolicyController.apply(this, mode);
            String label = mode == PolicyController.Mode.DESKTOP ? "主页模式" : "三分屏模式";
            showStatus("已切换到" + label + "。\n当前 policy_control：" + displayPolicy());
        } catch (RuntimeException error) {
            showStatus(error.getMessage());
        }
    }

    private String displayPolicy() {
        String value = PolicyController.read(this);
        return value == null || value.length() == 0 ? "<未设置>" : value;
    }

    private void showStatus(String text) {
        status.setText(text == null ? "操作失败" : text);
    }

    private void connectAndGrant() {
        final String host = adbHost.getText().toString().trim();
        final String portText = adbPort.getText().toString().trim();
        final int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535 || host.length() == 0) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            showStatus("ADB 地址或端口无效");
            return;
        }

        showStatus("正在连接 " + host + ":" + port + "，首次连接可能需要确认 RSA 授权…");
        adbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String output = new AdbClient(SettingsActivity.this).grant(host, port);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showStatus("ADB 授权命令已执行。\n返回："
                                    + (output.length() == 0 ? "<空>" : output)
                                    + "\n现在可以切换主页/三分屏系统栏模式。");
                            getSharedPreferences(BOOTSTRAP_PREFS, MODE_PRIVATE)
                                    .edit().putBoolean(BOOTSTRAP_DONE, true).apply();
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    launchTarget();
                                }
                            }, 500L);
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showStatus("ADB 连接失败：" + error.getClass().getSimpleName()
                                    + "\n" + (error.getMessage() == null ? "请确认端口和 RSA 授权" : error.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private void launchTarget() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                PolicyController.TARGET_PACKAGE, "com.carhome.HomeEntryActivity"));
        try {
            startActivity(intent);
            finish();
        } catch (RuntimeException error) {
            showStatus("跑跑桌面无法启动：" + error.getClass().getSimpleName());
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
            showStatus("已打开设置页面。若车机没有这个页面，说明 Android 9 固件未提供无线 ADB 入口。\n"
                    + "当前 policy_control：" + displayPolicy());
        } catch (RuntimeException error) {
            if ("android.settings.WIFI_ADB_SETTINGS".equals(action)) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    showStatus("车机没有无线 ADB 页面，已改为打开开发者选项。\n"
                            + "Android 9 通常只能在这里开启 USB ADB，不能由普通 APK 创建无线 ADB。\n"
                            + "当前 policy_control：" + displayPolicy());
                    return;
                } catch (RuntimeException ignored) {
                    // Use the common error below.
                }
            }
            showStatus("车机不支持此设置入口：" + action + "\n"
                    + "请使用网络 ADB 手动授权，或联系车机固件提供方开启 ADB。\n"
                    + "错误类型：" + error.getClass().getSimpleName());
        }
    }
}
