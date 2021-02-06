package com.example.myapplication;

import androidx.annotation.NonNull;

import java.util.Set;

public class StaticProcessList {
    private Set<String> blacklist;
    private Set<String> whitelist;

    public StaticProcessList(Set<String> blacklist, Set<String> whitelist) {
        this.blacklist = blacklist;
        this.whitelist = whitelist;
    }

    static public StaticProcessList fromSettings(MyUtils myUtils) {
        Set<String> blacklist = null;
        if (myUtils.isNowDanger()) {
            blacklist = myUtils.getDangerProcessesSet();
        }

        Set<String> whitelist = null;
        if (myUtils.isNowCritical()) {
            whitelist = myUtils.getCriticalProcessesSet();
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
