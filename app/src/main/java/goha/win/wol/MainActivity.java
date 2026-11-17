package goha.win.wol;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

import goha.win.wol.adapter.DeviceAdapter;
import goha.win.wol.data.DeviceStore;
import goha.win.wol.model.Device;
import goha.win.wol.net.DeviceScanner;

/**
 * 主界面：设备列表 + 概览统计 + 一键唤醒。
 */
public class MainActivity extends AppCompatActivity implements DeviceAdapter.Listener {

    private DeviceStore store;
    private DeviceScanner scanner;
    private DeviceAdapter adapter;

    private TextView tvSummary;
    private TextView tvSummarySub;
    private TextView tvLocalIp;
    private TextView btnCheckAll;
    private RecyclerView rvDevices;
    private View emptyView;

    /** 从添加/编辑页、详情页返回后统一刷新。 */
    private final ActivityResultLauncher<Intent> pageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> reloadFromStore());

    private final DeviceScanner.Callback scanCallback = new DeviceScanner.Callback() {
        @Override
        public void onChecking(Device device) {
            device.setState(Device.State.CHECKING);
            adapter.refresh(device);
        }

        @Override
        public void onOnline(Device device, int latencyMs) {
            device.setState(Device.State.ONLINE);
            device.setLatencyMs(latencyMs);
            adapter.refresh(device);
            updateSummary();
            finishOneCheck();
        }

        @Override
        public void onOffline(Device device) {
            device.setState(Device.State.OFFLINE);
            device.setLatencyMs(-1);
            adapter.refresh(device);
            updateSummary();
            finishOneCheck();
        }
    };

    /** 全部检测的剩余台数，用于恢复按钮状态。 */
    private int pendingChecks = 0;

    /** 兜底：网络异常导致回调缺失时，也能把「全部检测」按钮恢复。 */
    private final Runnable resetCheckUi = () -> {
        pendingChecks = 0;
        setCheckingUi(false);
    };

    private final DeviceScanner.WakeWatcher wakeWatcher = new DeviceScanner.WakeWatcher() {
        @Override
        public void onPacketSent(Device device, boolean success) {
            if (!success) {
                device.setState(Device.State.OFFLINE);
                adapter.refresh(device);
                toast(getString(R.string.toast_packet_failed));
                return;
            }
            device.setLastWakeAt(System.currentTimeMillis());
            toast(getString(R.string.toast_packet_sent));
        }

        @Override
        public void onWoke(Device device, int latencyMs) {
            device.setState(Device.State.ONLINE);
            device.setLatencyMs(latencyMs);
            adapter.refresh(device);
            updateSummary();
            toast(getString(R.string.toast_wake_success, device.getName()));
        }

        @Override
        public void onTimeout(Device device) {
            device.setState(Device.State.OFFLINE);
            device.setLatencyMs(-1);
            adapter.refresh(device);
            updateSummary();
            toast(getString(R.string.toast_wake_timeout));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        applyWindowInsets();

        store = new DeviceStore(this);
        scanner = new DeviceScanner();
        adapter = new DeviceAdapter(this);

        tvSummary = findViewById(R.id.tvSummary);
        tvSummarySub = findViewById(R.id.tvSummarySub);
        tvLocalIp = findViewById(R.id.tvLocalIp);
        btnCheckAll = findViewById(R.id.btnCheckAll);
        rvDevices = findViewById(R.id.rvDevices);
        emptyView = findViewById(R.id.emptyView);

        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        rvDevices.setAdapter(adapter);
        // 状态刷新频率高，关掉条目变更动画避免闪烁
        RecyclerView.ItemAnimator animator = rvDevices.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        btnCheckAll.setOnClickListener(v -> checkAll());
        findViewById(R.id.fabAdd).setOnClickListener(v -> openEditor(null));
        findViewById(R.id.btnEmptyAdd).setOnClickListener(v -> openEditor(null));

        showLocalIp();
        reloadFromStore();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanner.shutdown();
    }

    // ------------------------------------------------------------ 数据刷新

    /** 重新读盘并刷新整个界面，随后触发一次全量检测。 */
    private void reloadFromStore() {
        store.load();
        List<Device> devices = store.all();
        adapter.submit(devices);
        updateEmptyState();
        updateSummary();
        if (!devices.isEmpty()) {
            checkAll();
        }
    }

    private void updateEmptyState() {
        boolean empty = store.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvDevices.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateSummary() {
        List<Device> devices = store.all();
        if (devices.isEmpty()) {
            tvSummary.setText(R.string.summary_empty);
            tvSummarySub.setText("");
            return;
        }
        int online = 0;
        int waking = 0;
        for (Device device : devices) {
            if (device.isOnline()) {
                online++;
            } else if (device.getState() == Device.State.WAKING) {
                waking++;
            }
        }
        tvSummary.setText(getString(R.string.summary_format, online, devices.size() - online));
        tvSummarySub.setText(waking > 0
                ? getString(R.string.summary_waking, waking)
                : getString(R.string.summary_hint));
    }

    private void showLocalIp() {
        String ip = detectLocalIp();
        tvLocalIp.setText(ip == null
                ? getString(R.string.home_subtitle_offline)
                : getString(R.string.home_subtitle_ip, ip));
    }

    /** 读取当前网络的 IPv4 地址，未联网或只有 IPv6 时返回 null。 */
    private String detectLocalIp() {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return null;
            }
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return null;
            }
            LinkProperties properties = connectivityManager.getLinkProperties(network);
            if (properties == null) {
                return null;
            }
            for (LinkAddress linkAddress : properties.getLinkAddresses()) {
                InetAddress address = linkAddress.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // 权限缺失或系统服务异常时按未连接处理
        }
        return null;
    }

    // ------------------------------------------------------------ 用户操作

    private void checkAll() {
        List<Device> devices = store.all();
        if (devices.isEmpty()) {
            return;
        }
        pendingChecks = devices.size();
        setCheckingUi(true);
        scanner.checkAll(devices, scanCallback);

        btnCheckAll.removeCallbacks(resetCheckUi);
        btnCheckAll.postDelayed(resetCheckUi, 6000L);
    }

    /** 每台设备检测结束调用一次，全部完成即恢复按钮。 */
    private void finishOneCheck() {
        if (pendingChecks > 0 && --pendingChecks == 0) {
            btnCheckAll.removeCallbacks(resetCheckUi);
            setCheckingUi(false);
        }
    }

    private void setCheckingUi(boolean checking) {
        btnCheckAll.setEnabled(!checking);
        btnCheckAll.setText(checking ? R.string.checking_all : R.string.check_all);
    }

    private void openEditor(Device device) {
        Intent intent = new Intent(this, DeviceEditActivity.class);
        if (device != null) {
            intent.putExtra(DeviceEditActivity.EXTRA_DEVICE_ID, device.getId());
        }
        pageLauncher.launch(intent);
    }

    @Override
    public void onWakeClick(Device device) {
        if (device.isOnline()) {
            toast(getString(R.string.toast_already_online));
            return;
        }
        if (device.getState() == Device.State.WAKING) {
            return;
        }
        device.setState(Device.State.WAKING);
        device.setLatencyMs(-1);
        adapter.refresh(device);
        updateSummary();
        scanner.wake(device, wakeWatcher);
    }

    @Override
    public void onDeviceClick(Device device) {
        Intent intent = new Intent(this, DeviceDetailActivity.class);
        intent.putExtra(DeviceDetailActivity.EXTRA_DEVICE_ID, device.getId());
        pageLauncher.launch(intent);
    }

    @Override
    public void onDeviceLongClick(Device device) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_msg, device.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    scanner.cancel(device.getId());
                    store.delete(device.getId());
                    adapter.submit(store.all());
                    updateEmptyState();
                    updateSummary();
                    toast(getString(R.string.toast_deleted));
                })
                .show();
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.main);
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
