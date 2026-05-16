package com.gree1d.reappzuku;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.Set;

/**
 * Periodically checks that appops restrictions are still applied for all
 * background-restricted packages, and re-applies any that have silently lost
 * their state (e.g. after OTA, package reinstall, or system resets appops).
 *
 * Automatically starts when there is at least one restricted package,
 * and stops when the list becomes empty.
 *
 * Skips packages temporarily released by RestrictionsScheduler.
 * Does not touch packages removed from restrictions via the ReAppzuku UI —
 * those are absent from the desired set by definition.
 */
public class RestrictionsWatchdogManager {

    private static final String TAG = "RestrictionsWatchdog";
    private static final long WATCHDOG_INTERVAL_MS = 35 * 60 * 1000L; // 35 minutes

    private final Context context;
    private final Handler handler;
    private final BackgroundAppManager appManager;
    private final ShellManager shellManager;
    private final RestrictionsScheduler scheduler;

    private boolean running = false;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            runCheck();
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    public RestrictionsWatchdogManager(Context context, Handler handler,
            BackgroundAppManager appManager, ShellManager shellManager,
            RestrictionsScheduler scheduler) {
        this.context      = context;
        this.handler      = handler;
        this.appManager   = appManager;
        this.shellManager = shellManager;
        this.scheduler    = scheduler;
    }

    /**
     * Starts the watchdog if there is at least one background-restricted package
     * and the required shell permission is available.
     * Safe to call multiple times — no-op if already running.
     */
    public void startIfNeeded() {
        if (running) return;
        if (!appManager.supportsBackgroundRestriction()) return;
        if (!shellManager.hasAnyShellPermission()) return;
        if (appManager.getBackgroundRestrictedApps().isEmpty()) {
            Log.d(TAG, "No restricted apps, watchdog not started");
            return;
        }
        running = true;
        // First check is delayed by one full interval:
        // the service already calls reapplySavedBackgroundRestrictions() on start,
        // so an immediate watchdog check would be redundant.
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
        Log.d(TAG, "Watchdog started, interval=" + (WATCHDOG_INTERVAL_MS / 60000) + " min");
    }

    /**
     * Stops the watchdog. Call from ShappkyService.onDestroy().
     */
    public void stop() {
        running = false;
        handler.removeCallbacks(watchdogRunnable);
        Log.d(TAG, "Watchdog stopped");
    }

    // -------------------------------------------------------------------------

    private void runCheck() {
        if (!appManager.supportsBackgroundRestriction()
                || !shellManager.hasAnyShellPermission()) {
            return;
        }

        Set<String> desired = appManager.sanitizeBackgroundRestrictionTargets(
                appManager.getBackgroundRestrictedApps());

        if (desired.isEmpty()) {
            // All restrictions were removed while watchdog was running — stop self
            stop();
            Log.d(TAG, "Watchdog stopped — no more restricted apps");
            return;
        }

        appManager.checkAndRepairRestrictions(desired, scheduler);
    }
}
