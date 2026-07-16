package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PdfListAdapter extends RecyclerView.Adapter<PdfListAdapter.Holder> {

    public interface OnPdfClickListener {
        void onPdfClick(@NonNull String path);
    }

    private final LayoutInflater inflater;
    @Nullable
    private final OnPdfClickListener clickListener;
    private final List<String> items = new ArrayList<>();

    PdfListAdapter(android.content.Context context, @Nullable OnPdfClickListener clickListener) {
        this.inflater = LayoutInflater.from(context);
        this.clickListener = clickListener;
    }

    void setItems(List<String> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(inflater.inflate(R.layout.item_danger_zone_pdf_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        String path = items.get(position);
        h.button.setText(new File(path).getName());
        h.button.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPdfClick(path);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final Button button;

        Holder(View itemView) {
            super(itemView);
            button = itemView.findViewById(R.id.pdf_row_name);
        }
    }
}
