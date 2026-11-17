package goha.win.wol.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import goha.win.wol.R;
import goha.win.wol.model.Device;
import goha.win.wol.util.ViewUtils;

/**
 * 设备状态在界面上的统一表达：颜色、文案、胶囊样式。
 *
 * <p>列表卡片和详情页共用这一套规则，保证「绿=在线 / 灰=离线 / 琥珀=唤醒中」始终一致。
 */
public final class DeviceStateUi {

    private DeviceStateUi() {
    }

    /** 状态点的颜色。 */
    public static int dotColor(Context context, Device.State state) {
        switch (state) {
            case ONLINE:
                return context.getColor(R.color.status_online);
            case WAKING:
                return context.getColor(R.color.status_waking);
            case CHECKING:
                return context.getColor(R.color.text_tertiary);
            case OFFLINE:
            default:
                return context.getColor(R.color.status_offline);
        }
    }

    /** 胶囊底色。 */
    public static int pillColor(Context context, Device.State state) {
        switch (state) {
            case ONLINE:
                return context.getColor(R.color.status_online_bg);
            case WAKING:
                return context.getColor(R.color.status_waking_bg);
            case CHECKING:
            case OFFLINE:
            default:
                return context.getColor(R.color.status_offline_bg);
        }
    }

    /** 胶囊文字颜色。 */
    public static int textColor(Context context, Device.State state) {
        switch (state) {
            case ONLINE:
                return context.getColor(R.color.status_online);
            case WAKING:
                return context.getColor(R.color.status_waking);
            case CHECKING:
                return context.getColor(R.color.text_secondary);
            case OFFLINE:
            default:
                return context.getColor(R.color.text_secondary);
        }
    }

    /** 状态文案：在线时附带延迟，信息量更大。 */
    public static CharSequence statusText(Context context, Device device) {
        switch (device.getState()) {
            case ONLINE:
                if (device.getLatencyMs() > 0) {
                    return context.getString(R.string.status_online) + " " + device.getLatencyMs() + "ms";
                }
                return context.getString(R.string.status_online);
            case WAKING:
                return context.getString(R.string.status_waking);
            case CHECKING:
                return context.getString(R.string.status_checking);
            case OFFLINE:
            default:
                return context.getString(R.string.status_offline);
        }
    }

    /** 一次性把胶囊、圆点、文字刷成当前状态。 */
    public static void bindPill(Context context, View pill, View dot, TextView label, Device device) {
        Device.State state = device.getState();
        pill.setBackground(ViewUtils.roundRect(pillColor(context, state), ViewUtils.dp(context, 15)));
        dot.setBackground(ViewUtils.oval(dotColor(context, state)));
        label.setTextColor(textColor(context, state));
        label.setText(statusText(context, device));
    }
}
