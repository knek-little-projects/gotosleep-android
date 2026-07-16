package com.example.myapplication;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Single source of truth for whether the app should currently be dark: either the manual
 * switch on the unlock panel, or - if a schedule is set - a time-of-day window that overrides
 * the manual switch entirely. See {@link Kernel#isDarkModeActiveNow()}.
 */
public class NightMode {
    public static void apply(Context context) {
        Kernel kernel = new Kernel(context);
        boolean dark = kernel.isDarkModeActiveNow();
        int desired = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != desired) {
            AppCompatDelegate.setDefaultNightMode(desired);
        }
    }
}
