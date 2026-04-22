package com.example.myapplication;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rows bind only for visible items; icons load off the main thread and apply only if the row still
 * shows the same package (handles fast scroll / recycle).
 */
public class PackagePickerAdapter extends RecyclerView.Adapter<PackagePickerAdapter.Holder> {

    private final LayoutInflater inflater;
    private final PackageManager pm;
    private final Set<String> selectedApps;
    private final List<String> items = new ArrayList<>();
    /** Master icon from PackageManager (one per package); rows clone via ConstantState when needed. */
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    PackagePickerAdapter(Context context, PackageManager pm, Set<String> selectedApps) {
        this.inflater = LayoutInflater.from(context);
        this.pm = pm;
        this.selectedApps = selectedApps;
    }

    void shutdown() {
        iconExecutor.shutdownNow();
    }

    void setItems(List<String> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_select_app_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        String pkg = items.get(position);

        h.icon.setTag(pkg);
        h.icon.setImageDrawable(null);

        h.checkBox.setOnCheckedChangeListener(null);
        h.checkBox.setChecked(selectedApps.contains(pkg));
        h.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedApps.add(pkg);
            } else {
                selectedApps.remove(pkg);
            }
        });

        h.button.setText(pkg);
        h.button.setOnClickListener(v -> h.checkBox.toggle());

        Drawable master = iconCache.get(pkg);
        if (master != null) {
            h.icon.setImageDrawable(cloneDrawable(master));
            return;
        }

        final String loadPkg = pkg;
        iconExecutor.execute(() -> {
            try {
                Drawable loaded = pm.getApplicationIcon(loadPkg);
                mainHandler.post(() -> {
                    if (!loadPkg.equals(h.icon.getTag())) {
                        return;
                    }
                    iconCache.put(loadPkg, loaded);
                    h.icon.setImageDrawable(cloneDrawable(loaded));
                });
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        });
    }

    private static Drawable cloneDrawable(Drawable source) {
        if (source == null) {
            return null;
        }
        Drawable.ConstantState state = source.getConstantState();
        if (state != null) {
            return state.newDrawable().mutate();
        }
        return source.mutate();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final ImageView icon;
        final Button button;

        Holder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.row_check);
            icon = itemView.findViewById(R.id.row_icon);
            button = itemView.findViewById(R.id.row_pkg_button);
        }
    }
}
