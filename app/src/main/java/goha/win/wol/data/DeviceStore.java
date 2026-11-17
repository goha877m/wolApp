package goha.win.wol.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import goha.win.wol.model.Device;

/**
 * 设备列表的本地存储。
 *
 * <p>数据量很小（几十条），直接用 SharedPreferences 存一段 JSON，省掉数据库依赖。
 * 内存里保留一份 {@link #devices}，{@link #all()} 返回的是引用副本，
 * 因此界面层对 {@link Device} 的运行时状态修改（state / latencyMs）无需回写。
 */
public class DeviceStore {

    private static final String PREFS_NAME = "wol_devices";
    private static final String KEY_DEVICES = "devices";

    private final SharedPreferences prefs;
    private final List<Device> devices = new ArrayList<>();

    public DeviceStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    /** 从磁盘重新读取。 */
    public void load() {
        devices.clear();
        String raw = prefs.getString(KEY_DEVICES, null);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Device device = Device.fromJson(array.optJSONObject(i));
                if (device != null) {
                    devices.add(device);
                }
            }
        } catch (JSONException ignored) {
            // 数据损坏时按空列表处理，不影响 App 启动
        }
    }

    /** 返回当前列表的浅拷贝，调用方增删不会影响内部集合。 */
    public List<Device> all() {
        return new ArrayList<>(devices);
    }

    public int size() {
        return devices.size();
    }

    public boolean isEmpty() {
        return devices.isEmpty();
    }

    public Device findById(String id) {
        if (id == null) {
            return null;
        }
        for (Device device : devices) {
            if (id.equals(device.getId())) {
                return device;
            }
        }
        return null;
    }

    /** 按 id 覆盖已有项，不存在则追加。 */
    public void upsert(Device device) {
        if (device == null) {
            return;
        }
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId().equals(device.getId())) {
                devices.set(i, device);
                persist();
                return;
            }
        }
        devices.add(device);
        persist();
    }

    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < devices.size(); i++) {
            if (id.equals(devices.get(i).getId())) {
                devices.remove(i);
                persist();
                return true;
            }
        }
        return false;
    }

    /** 通过 MAC 查重，避免同一条电脑被添加两次。 */
    public Device findByMac(String mac) {
        String normalized = Device.normalizeMac(mac);
        if (normalized == null) {
            return null;
        }
        for (Device device : devices) {
            if (normalized.equals(Device.normalizeMac(device.getMac()))) {
                return device;
            }
        }
        return null;
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (Device device : devices) {
            try {
                array.put(device.toJson());
            } catch (JSONException ignored) {
                // 单条序列化失败不影响其余设备
            }
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply();
    }
}
