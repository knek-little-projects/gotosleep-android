package com.example.myapplication;

import java.util.Set;

public class StaticProcessList {
    private Set<String> blacklist;
    private Set<String> whitelist;

    public StaticProcessList(Set<String> blacklist, Set<String> whitelist) {
        this.blacklist = blacklist;
        this.whitelist = whitelist;
    }

    static public StaticProcessList fromPreferences(Kernel kernel, Preferences preferences) {
        Set<String> blacklist = null;
        if (kernel.isNowDanger()) {
            blacklist = preferences.getDangerProcessesSet();
        }

        Set<String> whitelist = null;
        if (kernel.isNowCritical()) {
            whitelist = preferences.getCriticalProcessesSet();
        }

        return new StaticProcessList(blacklist, whitelist);
    }

    public boolean isPackageAllowed(String packageName) {
        if (blacklist != null && blacklist.contains(packageName)) {
            return false;
        }

        if (whitelist != null && !whitelist.contains(packageName)) {
            return false;
        }

        return true;
    }
}
