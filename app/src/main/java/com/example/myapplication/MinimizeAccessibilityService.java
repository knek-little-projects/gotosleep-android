package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service used as a bypass for Android 10+ Background Activity Launch (BAL)
 * restrictions. An accessibility service can invoke {@link #performGlobalAction(int)} at any time
 * - including from a background context - which is exactly what {@link Kernel#bringToFront()}
 * needs when a forbidden foreground app is detected during a non-safe period.
 *
 * We keep a static instance reference so {@link Kernel} can reach the live service instance from
 * any thread (Timer/Worker/AlarmReceiver). The OS creates/destroys the instance; we only mirror
 * that lifecycle here.
 */
public class MinimizeAccessibilityService extends AccessibilityService {

    private static final String TAG = "A11yMinimize";

    private static volatile MinimizeAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty: we only need this service for its performGlobalAction() capability,
        // not for observing events. Keeping this a no-op avoids CPU work for every a11y event.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        Log.i(TAG, "Accessibility service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    /**
     * Send the system "Go Home" action. Works from any thread and from any calling context
     * because accessibility services are exempt from background-activity-launch restrictions.
     *
     * @return {@code true} if an accessibility service instance is alive and the action was
     *         dispatched, {@code false} if the user has not enabled the service.
     */
    public static boolean goHome() {
        MinimizeAccessibilityService s = instance;
        if (s == null) {
            return false;
        }
        try {
            boolean ok = s.performGlobalAction(GLOBAL_ACTION_HOME);
            Log.d(TAG, "performGlobalAction(HOME)=" + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "performGlobalAction failed", t);
            return false;
        }
    }

    /**
     * Fast check for whether our service is currently bound. Uses the live instance ref rather
     * than Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES so it also reflects the runtime state
     * (e.g. the OS killed/unbound the service after a crash).
     */
    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * Secondary check based on Settings.Secure - useful at UI build time (e.g. status screen)
     * when the service hasn't had a chance to bind yet after a toggle. Returns true if our
     * ComponentName is listed in the OS enabled-accessibility-services string.
     */
    public static boolean isEnabledInSettings(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        String needle = context.getPackageName() + "/" + MinimizeAccessibilityService.class.getName();
        for (String token : enabled.split(":")) {
            if (token.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }
}
