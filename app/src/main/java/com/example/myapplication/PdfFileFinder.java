package com.example.myapplication;

import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive .pdf scan of a user-configured folder, restricted to live inside primary
 * external storage so a mistyped path (e.g. "/") can't turn into a full-filesystem crawl.
 */
public class PdfFileFinder {

    public static class Entry {
        public final String path;
        public final long lastModified;

        Entry(String path, long lastModified) {
            this.path = path;
            this.lastModified = lastModified;
        }
    }

    public static boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    public static boolean isPathAllowed(@NonNull String configuredPath) {
        try {
            String externalRoot = Environment.getExternalStorageDirectory().getCanonicalPath();
            String candidate = new File(configuredPath).getCanonicalPath();
            return candidate.equals(externalRoot) || candidate.startsWith(externalRoot + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public static List<Entry> scan(@NonNull String rootPath) {
        List<Entry> results = new ArrayList<>();
        File root = new File(rootPath);
        if (root.isDirectory()) {
            scanDir(root, results);
        }
        return results;
    }

    private static void scanDir(File dir, List<Entry> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                scanDir(child, out);
            } else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                out.add(new Entry(child.getAbsolutePath(), child.lastModified()));
            }
        }
    }
}
