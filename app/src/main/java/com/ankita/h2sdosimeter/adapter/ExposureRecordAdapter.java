package com.ankita.h2sdosimeter.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ankita.h2sdosimeter.R;
import com.ankita.h2sdosimeter.model.ExposureRecord;

import java.util.List;

/**
 * ExposureRecordAdapter - displays exposure history records in a RecyclerView.
 * All displayed values carry DEMO labels.
 */
public class ExposureRecordAdapter
        extends RecyclerView.Adapter<ExposureRecordAdapter.ViewHolder> {

    public interface OnRecordClickListener {
        void onRecordClick(ExposureRecord record);
    }

    private final List<ExposureRecord> records;
    private final Context context;
    private OnRecordClickListener listener;

    public ExposureRecordAdapter(Context context, List<ExposureRecord> records) {
        this.context = context;
        this.records = records;
    }

    public void setOnRecordClickListener(OnRecordClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_exposure_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExposureRecord record = records.get(position);
        holder.bind(record);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRecordClick(record);
        });
    }

    @Override
    public int getItemCount() {
        return records != null ? records.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final View categoryBar;
        private final TextView tvDate;
        private final TextView tvShift;
        private final TextView tvExposure;
        private final TextView tvCategory;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryBar = itemView.findViewById(R.id.categoryBar);
            tvDate      = itemView.findViewById(R.id.tvRecordDate);
            tvShift     = itemView.findViewById(R.id.tvRecordShift);
            tvExposure  = itemView.findViewById(R.id.tvRecordExposure);
            tvCategory  = itemView.findViewById(R.id.tvRecordCategory);
        }

        void bind(ExposureRecord record) {
            tvDate.setText(record.getDate());
            tvShift.setText(record.getShiftName());

            // Show exposure value without the long DEMO suffix for compact display
            String exp = record.getEstimatedExposureDisplay();
            if (exp != null && exp.contains(" [")) {
                exp = exp.substring(0, exp.indexOf(" ["));
            }
            tvExposure.setText(exp);

            // Apply category styling
            switch (record.getCategory()) {
                case HIGH:
                    tvCategory.setText("HIGH");
                    tvCategory.setTextColor(ContextCompat.getColor(context, R.color.colorStatusDanger));
                    tvCategory.setBackgroundResource(R.drawable.bg_status_high);
                    categoryBar.setBackgroundColor(ContextCompat.getColor(context, R.color.colorStatusDanger));
                    break;
                case ELEVATED:
                    tvCategory.setText("ELEV.");
                    tvCategory.setTextColor(ContextCompat.getColor(context, R.color.colorStatusWarning));
                    tvCategory.setBackgroundResource(R.drawable.bg_status_elevated);
                    categoryBar.setBackgroundColor(ContextCompat.getColor(context, R.color.colorStatusWarning));
                    break;
                default:
                    tvCategory.setText("LOW");
                    tvCategory.setTextColor(ContextCompat.getColor(context, R.color.colorStatusSafe));
                    tvCategory.setBackgroundResource(R.drawable.bg_status_low);
                    categoryBar.setBackgroundColor(ContextCompat.getColor(context, R.color.colorStatusSafe));
                    break;
            }
        }
    }
}
