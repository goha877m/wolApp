package goha.win.wol;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import goha.win.wol.data.DeviceStore;
import goha.win.wol.model.Device;
import goha.win.wol.net.DeviceScanner;
import goha.win.wol.ui.DeviceStateUi;
import goha.win.wol.util.ViewUtils;

/**
 * 设备详情：大号延迟圆环 + 完整参数 + 唤醒入口。
 */
public class DeviceDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ID = "extra_device_id";

    private DeviceStore store;
    private DeviceScanner scanner;
    private Device device;
    private String deviceId;

    private TextView tvName;
    private TextView tvAddress;
    private TextView tvLatency;
    private TextView tvStateLabel;
    private TextView tvMac;
    private TextView tvIp;
    private TextView tvPort;
    private TextView tvBroadcast;
    private TextView tvLastWake;
    private TextView btnWake;
    private View ringView;

    private final ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> reloadDevice());

    private final DeviceScanner.Callback scanCallback = new DeviceScanner.Callback() {
        @Override
        public void onChecking(Device d) {
            d.setState(Device.State.CHECKING);
            render();
        }

        @Override
        public void onOnline(Device d, int latencyMs) {
            d.setState(Device.State.ONLINE);
            d.setLatencyMs(latencyMs);
            render();
        }

        @Override
        public void onOffline(Device d) {
            d.setState(Device.State.OFFLINE);
            d.setLatencyMs(-1);
            render();
        }
    };

    private final DeviceScanner.WakeWatcher wakeWatcher = new DeviceScanner.WakeWatcher() {
        @Override
        public void onPacketSent(Device d, boolean success) {
            if (!success) {
                d.setState(Device.State.OFFLINE);
                render();
                toast(getString(R.string.toast_packet_failed));
                return;
            }
            d.setLastWakeAt(System.currentTimeMillis());
            tvLastWake.setText(formatLastWake(d.getLastWakeAt()));
            toast(getString(R.string.toast_packet_sent));
        }

        @Override
        public void onWoke(Device d, int latencyMs) {
            d.setState(Device.State.ONLINE);
            d.setLatencyMs(latencyMs);
            render();
            toast(getString(R.string.toast_wake_success, d.getName()));
        }

        @Override
        public void onTimeout(Device d) {
            d.setState(Device.State.OFFLINE);
            d.setLatencyMs(-1);
            render();
            toast(getString(R.string.toast_wake_timeout));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_device_detail);
        applyWindowInsets();

        store = new DeviceStore(this);
        scanner = new DeviceScanner();

        deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        device = store.findById(deviceId);
        if (device == null) {
            finish();
            return;
        }

        bindViews();
        render();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditTop).setOnClickListener(v -> openEditor());
        btnWake.setOnClickListener(v -> wake());

        scanner.check(device, scanCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanner.shutdown();
    }

    private void bindViews() {
        tvName = findViewById(R.id.tvName);
        tvAddress = findViewById(R.id.tvAddress);
        tvLatency = findViewById(R.id.tvLatency);
        tvStateLabel = findViewById(R.id.tvStateLabel);
        tvMac = findViewById(R.id.tvMac);
        tvIp = findViewById(R.id.tvIp);
        tvPort = findViewById(R.id.tvPort);
        tvBroadcast = findViewById(R.id.tvBroadcast);
        tvLastWake = findViewById(R.id.tvLastWake);
        btnWake = findViewById(R.id.btnWake);
        ringView = findViewById(R.id.ringView);
    }

    /** 编辑页返回后，设备对象可能被重建或被删除，需要重新取。 */
    private void reloadDevice() {
        store.load();
        Device fresh = store.findById(deviceId);
        if (fresh == null) {
            finish();
            return;
        }
        device = fresh;
        render();
        scanner.check(device, scanCallback);
    }

    private void render() {
        if (device == null) {
            return;
        }
        Device.State state = device.getState();

        tvName.setText(device.getName());
        tvAddress.setText(device.subtitle());
        tvMac.setText(device.getMac());
        tvIp.setText(device.getIp());
        tvPort.setText(String.valueOf(device.getPort()));
        tvBroadcast.setText(device.getBroadcast());
        tvLastWake.setText(formatLastWake(device.getLastWakeAt()));

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(ViewUtils.dp(this, 6), DeviceStateUi.dotColor(this, state));
        ringView.setBackground(ring);

        if (state == Device.State.ONLINE && device.getLatencyMs() > 0) {
            String value = device.getLatencyMs() + " ms";
            SpannableString styled = new SpannableString(value);
            int unitStart = value.indexOf("ms");
            styled.setSpan(new RelativeSizeSpan(0.42f), unitStart, value.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new ForegroundColorSpan(getColor(R.color.text_secondary)),
                    unitStart, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvLatency.setText(styled);
            tvLatency.setTextColor(getColor(R.color.text_primary));
        } else {
            tvLatency.setText("—");
            tvLatency.setTextColor(getColor(R.color.text_tertiary));
        }

        tvStateLabel.setText(DeviceStateUi.statusText(this, device));
        tvStateLabel.setTextColor(DeviceStateUi.textColor(this, state));

        bindWakeButton(state);
    }

    private void bindWakeButton(Device.State state) {
        switch (state) {
            case ONLINE:
                btnWake.setText(R.string.wake_button_online);
                btnWake.setTextColor(getColor(R.color.text_tertiary));
                btnWake.setBackgroundResource(R.drawable.bg_wake_button_disabled);
                btnWake.setEnabled(false);
                break;
            case WAKING:
                btnWake.setText(R.string.wake_button_busy);
                btnWake.setTextColor(getColor(R.color.status_waking));
                btnWake.setBackgroundResource(R.drawable.bg_wake_button_disabled);
                btnWake.setEnabled(false);
                break;
            default:
                btnWake.setText(R.string.detail_wake);
                btnWake.setTextColor(getColor(R.color.on_primary));
                btnWake.setBackgroundResource(R.drawable.bg_primary_button);
                btnWake.setEnabled(true);
                break;
        }
    }

    private void wake() {
        if (device == null || device.isOnline()) {
            toast(getString(R.string.toast_already_online));
            return;
        }
        if (device.getState() == Device.State.WAKING) {
            return;
        }
        device.setState(Device.State.WAKING);
        device.setLatencyMs(-1);
        render();
        scanner.wake(device, wakeWatcher);
    }

    private void openEditor() {
        Intent intent = new Intent(this, DeviceEditActivity.class);
        intent.putExtra(DeviceEditActivity.EXTRA_DEVICE_ID, deviceId);
        editLauncher.launch(intent);
    }

    private String formatLastWake(long timestamp) {
        if (timestamp <= 0) {
            return getString(R.string.detail_never_wake);
        }
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private void applyWindowInsets() {
        final View root = findViewById(R.id.detailRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
