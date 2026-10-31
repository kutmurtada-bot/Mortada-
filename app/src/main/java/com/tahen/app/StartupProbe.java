package com.tahen.app;

import android.util.Log;

import io.sentry.Sentry;
import io.sentry.SentryLevel;

public final class StartupProbe {
    private StartupProbe() {}

    public static void mark(String stage) {
        try {
            Log.i("FlourDistribution", "STARTUP: " + stage);
            Sentry.addBreadcrumb("STARTUP: " + stage);
            Sentry.captureMessage("STARTUP: " + stage, SentryLevel.INFO);
        } catch (Throwable ignored) {
        }
    }
}
