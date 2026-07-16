package com.example.myapplication;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk-backed cache of the recursive PDF scan, kept out of {@link Preferences}'
 * SharedPreferences on purpose: with potentially thousands of files, writing every
 * click through the shared prefs file would slow down every unrelated read/write of
 * app-wide settings. Scan results and per-file "last clicked" timestamps live in their
 * own JSON file instead; disk writes are always done off the caller's thread.
 */
public class PdfCache {

    private static class Entry {
        final String path;
        final long lastModified;
        long lastClickMillis;

        Entry(String path, long lastModified, long lastClickMillis) {
            this.path = path;
            this.lastModified = lastModified;
            this.lastClickMillis = lastClickMillis;
        }
    }

    private final File cacheFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public PdfCache(@NonNull Context context) {
        this.cacheFile = new File(context.getFilesDir(), "pdf_cache.json");
    }

    public boolean cacheFileExists() {
        return cacheFile.exists();
    }

    public synchronized void load() {
        entries.clear();
        if (!cacheFile.exists()) {
            return;
        }
        try (InputStream in = new FileInputStream(cacheFile)) {
            byte[] bytes = new byte[(int) cacheFile.length()];
            int off = 0;
            while (off < bytes.length) {
                int read = in.read(bytes, off, bytes.length - off);
                if (read < 0) {
                    break;
                }
                off += read;
            }
            JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String path = obj.getString("path");
                entries.put(path, new Entry(
                        path,
                        obj.optLong("lastModified", 0L),
                        obj.optLong("lastClickMillis", 0L)));
            }
        } catch (IOException | JSONException e) {
            entries.clear();
        }
    }

    public synchronized void mergeScanResults(@NonNull List<PdfFileFinder.Entry> scanned) {
        Map<String, Entry> updated = new LinkedHashMap<>();
        for (PdfFileFinder.Entry found : scanned) {
            Entry existing = entries.get(found.path);
            long lastClick = existing != null ? existing.lastClickMillis : 0L;
            updated.put(found.path, new Entry(found.path, found.lastModified, lastClick));
        }
        entries.clear();
        entries.putAll(updated);
    }

    public synchronized void recordClick(@NonNull String path) {
        Entry entry = entries.get(path);
        if (entry != null) {
            entry.lastClickMillis = System.currentTimeMillis();
        }
    }

    @NonNull
    public synchronized List<String> getSortedPaths() {
        List<Entry> list = new ArrayList<>(entries.values());
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int byClick = Long.compare(b.lastClickMillis, a.lastClickMillis);
                if (byClick != 0) {
                    return byClick;
                }
                int byModified = Long.compare(b.lastModified, a.lastModified);
                if (byModified != 0) {
                    return byModified;
                }
                return a.path.compareTo(b.path);
            }
        });

        List<String> paths = new ArrayList<>(list.size());
        for (Entry entry : list) {
            paths.add(entry.path);
        }
        return paths;
    }

    /** Must be called off the main thread - serializes the whole cache to disk. */
    public synchronized void flushToDisk() {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries.values()) {
                JSONObject obj = new JSONObject();
                obj.put("path", entry.path);
                obj.put("lastModified", entry.lastModified);
                obj.put("lastClickMillis", entry.lastClickMillis);
                array.put(obj);
            }
        } catch (JSONException e) {
            return;
        }

        File tmp = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        tmp.renameTo(cacheFile);
    }
}
