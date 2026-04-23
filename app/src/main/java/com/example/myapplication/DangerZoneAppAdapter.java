package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Same lazy-icon pattern as {@link PackagePickerAdapter}; row opens the app's launcher intent.
 */
public class DangerZoneAppAdapter extends RecyclerView.Adapter<DangerZoneAppAdapter.Holder> {

    public interface OnDangerZoneLaunchListener {
        void onDangerZoneLaunch(@NonNull String packageName);
    }

    private final Activity activity;
    private final LayoutInflater inflater;
    private final PackageManager pm;
    @Nullable
    private final OnDangerZoneLaunchListener launchListener;
    private final List<String> items = new ArrayList<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    DangerZoneAppAdapter(
            Activity activity,
            PackageManager pm,
            @Nullable OnDangerZoneLaunchListener launchListener) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.pm = pm;
        this.launchListener = launchListener;
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
        return new Holder(inflater.inflate(R.layout.item_danger_zone_app_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        String pkg = items.get(position);

        h.icon.setTag(pkg);
        h.icon.setImageDrawable(null);

        h.button.setText(pkg);
        View.OnClickListener launchClick = v -> launchFromRow(pkg);
        h.button.setOnClickListener(launchClick);
        h.icon.setOnClickListener(launchClick);

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

    private void launchFromRow(@NonNull String pkg) {
        Intent intent = pm.getLaunchIntentForPackage(pkg);
        if (intent != null) {
            if (launchListener != null) {
                launchListener.onDangerZoneLaunch(pkg);
            }
            activity.startActivity(intent);
        }
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
        final ImageView icon;
        final Button button;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.dz_row_icon);
            button = itemView.findViewById(R.id.dz_row_launch);
        }
    }
}
