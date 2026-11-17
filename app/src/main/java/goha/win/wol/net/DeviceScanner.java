package goha.win.wol.net;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import goha.win.wol.model.Device;

/**
 * 设备状态探测器：负责并发执行在线检测，以及「唤醒后持续轮询」的完整流程。
 *
 * <p>所有网络操作都在后台线程池执行，回调统一切回主线程，界面层无需自己处理线程切换。
 */
public class DeviceScanner {

    /** 普通状态回调。 */
    public interface Callback {
        void onChecking(Device device);

        void onOnline(Device device, int latencyMs);

        void onOffline(Device device);
    }

    /** 唤醒流程回调。 */
    public interface WakeWatcher {
        /** 魔术包是否发出；失败时界面应把状态回退为离线。 */
        void onPacketSent(Device device, boolean success);

        /** 轮询到设备上线。 */
        void onWoke(Device device, int latencyMs);

        /** 超过设定的检测时长仍未上线。 */
        void onTimeout(Device device);
    }

    private static final int ICMP_TIMEOUT_MS = 800;
    private static final long POLL_INTERVAL_MS = 1500L;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    /** 单台设备检测一次。 */
    public void check(Device device, Callback callback) {
        if (device == null || callback == null) {
            return;
        }
        String id = device.getId();
        main.post(() -> callback.onChecking(device));
        pool.execute(() -> {
            PingChecker.Result result = PingChecker.check(device.getIp(), ICMP_TIMEOUT_MS);
            main.post(() -> {
                if (cancelled.contains(id)) {
                    return;
                }
                if (result.reachable) {
                    callback.onOnline(device, result.latencyMs);
                } else {
                    callback.onOffline(device);
                }
            });
        });
    }

    /** 批量检测，内部并发执行。 */
    public void checkAll(List<Device> devices, Callback callback) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        for (Device device : devices) {
            check(device, callback);
        }
    }

    /**
     * 发送魔术包并在超时窗口内持续轮询，成功或超时都会回调。
     */
    public void wake(Device device, WakeWatcher watcher) {
        if (device == null || watcher == null) {
            return;
        }
        final String id = device.getId();
        cancelled.remove(id);

        pool.execute(() -> {
            boolean sent = WolSender.wake(device);
            main.post(() -> {
                if (!cancelled.contains(id)) {
                    watcher.onPacketSent(device, sent);
                }
            });
            if (!sent) {
                return;
            }

            long deadline = System.currentTimeMillis() + device.getWakeTimeoutSec() * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (cancelled.contains(id)) {
                    return;
                }
                PingChecker.Result result = PingChecker.check(device.getIp(), ICMP_TIMEOUT_MS);
                if (result.reachable) {
                    main.post(() -> {
                        if (!cancelled.contains(id)) {
                            watcher.onWoke(device, result.latencyMs);
                        }
                    });
                    return;
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            main.post(() -> {
                if (!cancelled.contains(id)) {
                    watcher.onTimeout(device);
                }
            });
        });
    }

    /** 取消某台设备正在进行的轮询（例如设备被删除）。 */
    public void cancel(String deviceId) {
        if (deviceId != null) {
            cancelled.add(deviceId);
        }
    }

    /** 页面销毁时释放线程池。 */
    public void shutdown() {
        pool.shutdownNow();
    }
}
