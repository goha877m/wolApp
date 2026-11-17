package goha.win.wol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import goha.win.wol.model.Device;
import goha.win.wol.net.WolSender;

/**
 * 数据模型与魔术包构造的单元测试。
 *
 * <p>这几处逻辑最容易被写错又最难在真机上察觉，因此单独覆盖。
 */
public class DeviceTest {

    @Test
    public void normalizeMac_acceptsCommonFormats() {
        assertEquals("AA:BB:CC:DD:EE:FF", Device.normalizeMac("aa:bb:cc:dd:ee:ff"));
        assertEquals("AA:BB:CC:DD:EE:FF", Device.normalizeMac("AA-BB-CC-DD-EE-FF"));
        assertEquals("AA:BB:CC:DD:EE:FF", Device.normalizeMac("aabbccddeeff"));
        assertEquals("AA:BB:CC:DD:EE:FF", Device.normalizeMac(" aa bb cc dd ee ff "));
        assertEquals("AA:BB:CC:DD:EE:FF", Device.normalizeMac("AABB.CCDD.EEFF"));
    }

    @Test
    public void normalizeMac_rejectsIllegalInput() {
        assertNull(Device.normalizeMac(null));
        assertNull(Device.normalizeMac(""));
        assertNull(Device.normalizeMac("AA:BB:CC:DD:EE"));
        assertNull(Device.normalizeMac("AA:BB:CC:DD:EE:FF:11"));
        assertNull(Device.normalizeMac("GG:BB:CC:DD:EE:FF"));
    }

    @Test
    public void macToBytes_producesSixBytes() {
        byte[] bytes = Device.macToBytes("01:02:03:0A:0B:0C");
        assertNotNull(bytes);
        assertEquals(6, bytes.length);
        assertEquals(1, bytes[0]);
        assertEquals(10, bytes[3]);
        assertNull(Device.macToBytes("not-a-mac"));
    }

    @Test
    public void isValidIpv4_acceptsOnlyProperAddresses() {
        assertTrue(Device.isValidIpv4("192.168.1.100"));
        assertTrue(Device.isValidIpv4("255.255.255.255"));
        assertTrue(Device.isValidIpv4("0.0.0.0"));

        assertFalse(Device.isValidIpv4("192.168.1"));
        assertFalse(Device.isValidIpv4("192.168.1.256"));
        assertFalse(Device.isValidIpv4("192.168.1.a"));
        assertFalse(Device.isValidIpv4("192.168..1"));
        assertFalse(Device.isValidIpv4(""));
        assertFalse(Device.isValidIpv4(null));
    }

    @Test
    public void magicPacket_matchesWolSpec() {
        byte[] mac = Device.macToBytes("01:02:03:04:05:06");
        byte[] packet = WolSender.buildPacket(mac);

        assertEquals(102, packet.length);
        // 前 6 字节为 0xFF 同步头
        for (int i = 0; i < 6; i++) {
            assertEquals((byte) 0xFF, packet[i]);
        }
        // 其后为 MAC 重复 16 次
        for (int repeat = 0; repeat < 16; repeat++) {
            for (int i = 0; i < 6; i++) {
                assertEquals(mac[i], packet[6 + repeat * 6 + i]);
            }
        }
    }

    @Test
    public void device_defaultsAreSane() {
        Device device = new Device("测试机", "192.168.1.10", "AA:BB:CC:DD:EE:FF");

        assertEquals(9, device.getPort());
        assertEquals("255.255.255.255", device.getBroadcast());
        assertEquals(60, device.getWakeTimeoutSec());
        assertEquals(Device.State.OFFLINE, device.getState());
        assertFalse(device.isOnline());
        assertNotNull(device.getId());
    }

    @Test
    public void device_subtitleJoinsIpAndMac() {
        Device device = new Device("客厅主机", "192.168.1.100", "AA:BB:CC:DD:EE:FF");
        assertEquals("192.168.1.100 · AA:BB:CC:DD:EE:FF", device.subtitle());
    }
}
