package goha.win.wol;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import goha.win.wol.data.DeviceStore;
import goha.win.wol.model.Device;

/**
 * 添加 / 编辑设备。
 *
 * <p>通过 {@link #EXTRA_DEVICE_ID} 区分模式：带上 id 为编辑，不带为新增。
 */
public class DeviceEditActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ID = "extra_device_id";

    private static final int MIN_TIMEOUT_SEC = 5;
    private static final int MAX_TIMEOUT_SEC = 600;

    private DeviceStore store;
    private Device editing;

    private EditText etName;
    private EditText etIp;
    private EditText etMac;
    private EditText etPort;
    private EditText etTimeout;
    private EditText etBroadcast;
    private TextView tvTitle;
    private TextView btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_device_edit);
        applyWindowInsets();

        store = new DeviceStore(this);

        String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        if (!TextUtils.isEmpty(deviceId)) {
            editing = store.findById(deviceId);
        }

        bindViews();
        if (editing == null) {
            editing = null;
            tvTitle.setText(R.string.edit_title_new);
            btnDelete.setVisibility(TextView.GONE);
            applyDefaults();
        } else {
            tvTitle.setText(R.string.edit_title_edit);
            btnDelete.setVisibility(TextView.VISIBLE);
            fillForm(editing);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void bindViews() {
        etName = findViewById(R.id.etName);
        etIp = findViewById(R.id.etIp);
        etMac = findViewById(R.id.etMac);
        etPort = findViewById(R.id.etPort);
        etTimeout = findViewById(R.id.etTimeout);
        etBroadcast = findViewById(R.id.etBroadcast);
        tvTitle = findViewById(R.id.tvTitle);
        btnDelete = findViewById(R.id.btnDelete);
    }

    private void applyDefaults() {
        etPort.setText(String.valueOf(Device.DEFAULT_PORT));
        etTimeout.setText(String.valueOf(Device.DEFAULT_WAKE_TIMEOUT_SEC));
        etBroadcast.setText(Device.DEFAULT_BROADCAST);
    }

    private void fillForm(Device device) {
        etName.setText(device.getName());
        etIp.setText(device.getIp());
        etMac.setText(device.getMac());
        etPort.setText(String.valueOf(device.getPort()));
        etTimeout.setText(String.valueOf(device.getWakeTimeoutSec()));
        etBroadcast.setText(device.getBroadcast());
    }

    // ------------------------------------------------------------ 保存

    private void save() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            toast(getString(R.string.error_name));
            etName.requestFocus();
            return;
        }

        String ip = etIp.getText().toString().trim();
        if (!Device.isValidIpv4(ip)) {
            toast(getString(R.string.error_ip));
            etIp.requestFocus();
            return;
        }

        String mac = Device.normalizeMac(etMac.getText().toString());
        if (mac == null) {
            toast(getString(R.string.error_mac));
            etMac.requestFocus();
            return;
        }

        int port = parseInt(etPort.getText().toString(), Device.DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            port = Device.DEFAULT_PORT;
        }

        int timeout = parseInt(etTimeout.getText().toString(), Device.DEFAULT_WAKE_TIMEOUT_SEC);
        if (timeout < MIN_TIMEOUT_SEC) {
            timeout = MIN_TIMEOUT_SEC;
        } else if (timeout > MAX_TIMEOUT_SEC) {
            timeout = MAX_TIMEOUT_SEC;
        }

        String broadcast = etBroadcast.getText().toString().trim();
        if (!Device.isValidIpv4(broadcast)) {
            broadcast = Device.DEFAULT_BROADCAST;
        }

        Device duplicate = store.findByMac(mac);
        if (duplicate != null && (editing == null || !duplicate.getId().equals(editing.getId()))) {
            toast(getString(R.string.error_mac_duplicate, duplicate.getName()));
            return;
        }

        Device target = editing != null ? editing : new Device();
        target.setName(name);
        target.setIp(ip);
        target.setMac(mac);
        target.setPort(port);
        target.setWakeTimeoutSec(timeout);
        target.setBroadcast(broadcast);
        store.upsert(target);

        toast(getString(R.string.toast_saved));
        finish();
    }

    private void confirmDelete() {
        if (editing == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_msg, editing.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    store.delete(editing.getId());
                    toast(getString(R.string.toast_deleted));
                    finish();
                })
                .show();
    }

    // ------------------------------------------------------------ 工具

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void applyWindowInsets() {
        final View root = findViewById(R.id.editRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
