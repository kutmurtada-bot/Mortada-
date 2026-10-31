package com.tahen.app;

import android.app.Application;
import android.util.Log;

import io.sentry.Sentry;
import io.sentry.SentryLevel;

public class CrashReporterApplication extends Application {
    private static final String TAG = "FlourDistribution";

    @Override
    public void onCreate() {
        super.onCreate();

        String dsn = BuildConfig.SENTRY_DSN;
        if (dsn != null && !dsn.trim().isEmpty()) {
            Sentry.init(this, options -> {
                options.setDsn(dsn);
                options.setEnableAutoSessionTracking(true);
                options.setAttachStacktrace(true);
                options.setDebug(false);
            });
            Sentry.captureMessage("Application startup reached", SentryLevel.INFO);
        } else {
            Log.e(TAG, "SENTRY_DSN is empty; crash reporting is disabled.");
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                Sentry.captureException(error);
                Sentry.flush(2000);
            } catch (Throwable ignored) {
                Log.e(TAG, "Failed to report uncaught exception", ignored);
            }
        });
    }
}
