package goha.win.wol.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/**
 * 一台可被唤醒的设备。
 *
 * <p>持久化字段（id/name/ip/mac/port/broadcast/wakeTimeoutSec/lastWakeAt）会写入
 * SharedPreferences；{@link #state} 与 {@link #latencyMs} 属于运行时状态，不落盘。
 */
public class Device {

    /** 设备在界面上的四种状态。 */
    public enum State {
        /** 离线：ping 不通，显示灰点。 */
        OFFLINE,
        /** 检测中：正在 ping，显示灰点 + 转圈。 */
        CHECKING,
        /** 唤醒中：已发出魔术包，正在轮询，显示琥珀点 + 脉冲。 */
        WAKING,
        /** 在线：ping 通，显示绿点 + 延迟。 */
        ONLINE
    }

    public static final int DEFAULT_PORT = 9;
    public static final String DEFAULT_BROADCAST = "255.255.255.255";
    public static final int DEFAULT_WAKE_TIMEOUT_SEC = 60;

    private String id;
    private String name;
    private String ip;
    private String mac;
    private int port = DEFAULT_PORT;
    private String broadcast = DEFAULT_BROADCAST;
    private int wakeTimeoutSec = DEFAULT_WAKE_TIMEOUT_SEC;
    private long lastWakeAt = 0L;

    private State state = State.OFFLINE;
    private int latencyMs = -1;

    public Device() {
        this.id = UUID.randomUUID().toString();
    }

    public Device(String name, String ip, String mac) {
        this();
        this.name = name;
        this.ip = ip;
        this.mac = mac;
    }

    // ---------------------------------------------------------------- 基本属性

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isEmpty()) ? UUID.randomUUID().toString() : id;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip == null ? "" : ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMac() {
        return mac == null ? "" : mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBroadcast() {
        return broadcast == null || broadcast.isEmpty() ? DEFAULT_BROADCAST : broadcast;
    }

    public void setBroadcast(String broadcast) {
        this.broadcast = broadcast;
    }

    public int getWakeTimeoutSec() {
        return wakeTimeoutSec <= 0 ? DEFAULT_WAKE_TIMEOUT_SEC : wakeTimeoutSec;
    }

    public void setWakeTimeoutSec(int wakeTimeoutSec) {
        this.wakeTimeoutSec = wakeTimeoutSec;
    }

    public long getLastWakeAt() {
        return lastWakeAt;
    }

    public void setLastWakeAt(long lastWakeAt) {
        this.lastWakeAt = lastWakeAt;
    }

    // ---------------------------------------------------------------- 运行时状态

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state == null ? State.OFFLINE : state;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isOnline() {
        return state == State.ONLINE;
    }

    public boolean isBusy() {
        return state == State.CHECKING || state == State.WAKING;
    }

    /** 副标题：「192.168.1.100 · AA:BB:CC:DD:EE:FF」。 */
    public String subtitle() {
        String m = getMac();
        return getIp() + (m.isEmpty() ? "" : " · " + m);
    }

    // ---------------------------------------------------------------- 校验与规范化

    /**
     * 把各种写法的 MAC 统一成 {@code AA:BB:CC:DD:EE:FF}。
     *
     * @return 规范化结果；不是合法 12 位十六进制时返回 {@code null}
     */
    public static String normalizeMac(String raw) {
        if (raw == null) {
            return null;
        }
        String hex = raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (hex.length() != 12) {
            return null;
        }
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(hex, i, i + 2);
        }
        return sb.toString();
    }

    /** MAC 转 6 字节；非法返回 {@code null}。 */
    public static byte[] macToBytes(String mac) {
        String normalized = normalizeMac(mac);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split(":");
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    /** 简单 IPv4 校验。 */
    public static boolean isValidIpv4(String ip) {
        if (ip == null) {
            return false;
        }
        String v = ip.trim();
        if (v.isEmpty()) {
            return false;
        }
        String[] parts = v.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) {
                return false;
            }
            for (int i = 0; i < p.length(); i++) {
                if (!Character.isDigit(p.charAt(i))) {
                    return false;
                }
            }
            int n;
            try {
                n = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                return false;
            }
            if (n < 0 || n > 255) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 序列化

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", getName());
        o.put("ip", getIp());
        o.put("mac", getMac());
        o.put("port", port);
        o.put("broadcast", getBroadcast());
        o.put("wakeTimeoutSec", getWakeTimeoutSec());
        o.put("lastWakeAt", lastWakeAt);
        return o;
    }

    public static Device fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        Device d = new Device();
        d.setId(o.optString("id", null));
        d.setName(o.optString("name", ""));
        d.setIp(o.optString("ip", ""));
        d.setMac(o.optString("mac", ""));
        d.setPort(o.optInt("port", DEFAULT_PORT));
        d.setBroadcast(o.optString("broadcast", DEFAULT_BROADCAST));
        d.setWakeTimeoutSec(o.optInt("wakeTimeoutSec", DEFAULT_WAKE_TIMEOUT_SEC));
        d.setLastWakeAt(o.optLong("lastWakeAt", 0L));
        return d;
    }

    @Override
    public String toString() {
        return "Device{" + name + ", " + ip + ", " + mac + ", state=" + state + "}";
    }
}
