package goha.win.wol.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import goha.win.wol.R;
import goha.win.wol.model.Device;
import goha.win.wol.ui.DeviceStateUi;

/**
 * 设备列表适配器。状态刷新走 {@link #refresh(Device)}，只重绘受影响的那一项。
 */
public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    /** 列表交互回调。 */
    public interface Listener {
        void onWakeClick(Device device);

        void onDeviceClick(Device device);

        void onDeviceLongClick(Device device);
    }

    private final List<Device> items = new ArrayList<>();
    private final Listener listener;

    public DeviceAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 整体替换数据源。 */
    public void submit(List<Device> devices) {
        items.clear();
        if (devices != null) {
            items.addAll(devices);
        }
        notifyDataSetChanged();
    }

    /** 某台设备状态变化时调用。 */
    public void refresh(Device device) {
        int index = indexOf(device);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    private int indexOf(Device device) {
        if (device == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(device.getId())) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 列表项。 */
    class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvSubtitle;
        private final TextView tvStatus;
        private final TextView btnWake;
        private final View pillStatus;
        private final View dotStatus;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnWake = itemView.findViewById(R.id.btnWake);
            pillStatus = itemView.findViewById(R.id.pillStatus);
            dotStatus = itemView.findViewById(R.id.dotStatus);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDeviceClick(items.get(position));
                }
            });
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDeviceLongClick(items.get(position));
                    return true;
                }
                return false;
            });
            btnWake.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onWakeClick(items.get(position));
                }
            });
        }

        void bind(Device device) {
            Context context = itemView.getContext();
            tvName.setText(device.getName());
            tvSubtitle.setText(device.subtitle());
            DeviceStateUi.bindPill(context, pillStatus, dotStatus, tvStatus, device);
            bindWakeButton(context, device);
        }

        private void bindWakeButton(Context context, Device device) {
            switch (device.getState()) {
                case ONLINE:
                    btnWake.setText(R.string.wake_button_online);
                    btnWake.setTextColor(context.getColor(R.color.text_tertiary));
                    btnWake.setBackgroundResource(R.drawable.bg_wake_button_disabled);
                    btnWake.setEnabled(false);
                    break;
                case WAKING:
                    btnWake.setText(R.string.wake_button_busy);
                    btnWake.setTextColor(context.getColor(R.color.status_waking));
                    btnWake.setBackgroundResource(R.drawable.bg_wake_button_disabled);
                    btnWake.setEnabled(false);
                    break;
                case CHECKING:
                case OFFLINE:
                default:
                    btnWake.setText(R.string.detail_wake_short);
                    btnWake.setTextColor(context.getColor(R.color.on_primary));
                    btnWake.setBackgroundResource(R.drawable.bg_wake_button);
                    btnWake.setEnabled(true);
                    break;
            }
        }
    }
}
