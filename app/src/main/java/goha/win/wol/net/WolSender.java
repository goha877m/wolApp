package goha.win.wol.net;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import goha.win.wol.model.Device;

/**
 * WOL（Wake on LAN）魔术包发送器。
 *
 * <p>魔术包结构：6 字节 {@code 0xFF} + 目标 MAC 重复 16 次，共 102 字节，
 * 以 UDP 广播方式发出。局域网内目标网卡只要处于 WOL 监听状态，
 * 即使主机关机也会被这段固定字节序列唤醒。
 */
public final class WolSender {

    private static final String TAG = "WolSender";

    /** 6 字节同步头 + 16 × 6 字节 MAC。 */
    private static final int PACKET_LENGTH = 6 + 16 * 6;

    /** 每个目标地址重复发送的次数，UDP 有丢包可能。 */
    private static final int REPEAT = 3;

    private WolSender() {
    }

    /**
     * 向设备发送唤醒包。
     *
     * @return 至少有一次发送成功即为 {@code true}
     */
    public static boolean wake(Device device) {
        if (device == null) {
            return false;
        }
        byte[] mac = Device.macToBytes(device.getMac());
        if (mac == null) {
            Log.w(TAG, "非法 MAC：" + device.getMac());
            return false;
        }

        byte[] packet = buildPacket(mac);
        int port = device.getPort() > 0 ? device.getPort() : Device.DEFAULT_PORT;

        boolean anySent = false;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            for (String target : resolveTargets(device)) {
                try {
                    InetAddress address = InetAddress.getByName(target);
                    for (int i = 0; i < REPEAT; i++) {
                        DatagramPacket datagram =
                                new DatagramPacket(packet, packet.length, address, port);
                        socket.send(datagram);
                    }
                    anySent = true;
                } catch (Exception e) {
                    Log.w(TAG, "发送到 " + target + " 失败：" + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "建立 UDP 通道失败", e);
            return false;
        }
        return anySent;
    }

    /**
     * 广播目标优先级：
     * 1. 用户配置的广播地址（默认 255.255.255.255）
     * 2. 受限广播地址（应对配置错误的情况）
     * 3. 设备单播 IP（部分路由器会过滤广播，单播依赖 ARP 表仍可命中）
     */
    private static List<String> resolveTargets(Device device) {
        Set<String> targets = new LinkedHashSet<>();
        targets.add(device.getBroadcast());
        targets.add(Device.DEFAULT_BROADCAST);
        if (!device.getIp().isEmpty()) {
            targets.add(device.getIp());
        }
        return new ArrayList<>(targets);
    }

    /** 组装 102 字节魔术包。 */
    public static byte[] buildPacket(byte[] mac) {
        if (mac == null || mac.length != 6) {
            throw new IllegalArgumentException("MAC 必须是 6 字节");
        }
        byte[] packet = new byte[PACKET_LENGTH];
        for (int i = 0; i < 6; i++) {
            packet[i] = (byte) 0xFF;
        }
        for (int repeat = 0; repeat < 16; repeat++) {
            System.arraycopy(mac, 0, packet, 6 + repeat * 6, 6);
        }
        return packet;
    }
}
