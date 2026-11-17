package goha.win.wol.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 判断一台主机是否在线。
 *
 * <p>Android 普通应用没有 ICMP 权限，{@link InetAddress#isReachable(int)} 在多数机型上
 * 会退化成对 TCP 7 端口的探测，而该端口通常关闭，结果不可靠。
 * 因此这里采用「先 ICMP、再 TCP 端口探测」的双重策略：
 * 只要任意一段成功，就判定主机已开机。
 */
public final class PingChecker {

    /** Windows 默认开放的端口排在前面，命中率最高。 */
    private static final int[] FALLBACK_PORTS = {445, 139, 135, 3389, 22, 80};

    /** 单个 TCP 探测的超时，短一些才能保证整体检测速度。 */
    private static final int TCP_TIMEOUT_MS = 260;

    private PingChecker() {
    }

    /** 探测结果。 */
    public static final class Result {
        public final boolean reachable;
        public final int latencyMs;

        Result(boolean reachable, int latencyMs) {
            this.reachable = reachable;
            this.latencyMs = latencyMs;
        }

        static Result offline() {
            return new Result(false, -1);
        }
    }

    /**
     * 探测指定 IP 是否在线。
     *
     * @param ip        目标 IPv4
     * @param timeoutMs ICMP 阶段的超时（毫秒）
     */
    public static Result check(String ip, int timeoutMs) {
        if (ip == null || ip.trim().isEmpty()) {
            return Result.offline();
        }
        String target = ip.trim();

        long start = System.currentTimeMillis();
        try {
            InetAddress address = InetAddress.getByName(target);
            if (address.isReachable(Math.max(300, timeoutMs))) {
                return new Result(true, (int) (System.currentTimeMillis() - start));
            }
        } catch (Exception ignored) {
            // 继续走 TCP 兜底
        }

        for (int port : FALLBACK_PORTS) {
            long portStart = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target, port), TCP_TIMEOUT_MS);
                return new Result(true, (int) (System.currentTimeMillis() - portStart));
            } catch (Exception ignored) {
                // 换下一个端口
            }
        }
        return Result.offline();
    }

    /** 简化版：只关心是否在线。 */
    public static boolean isOnline(String ip, int timeoutMs) {
        return check(ip, timeoutMs).reachable;
    }
}
