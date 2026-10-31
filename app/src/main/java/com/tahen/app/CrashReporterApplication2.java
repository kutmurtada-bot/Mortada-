package com.tahen.app;

import android.app.Application;
import android.util.Log;
import io.sentry.Sentry;
import io.sentry.SentryLevel;

public class CrashReporterApplication2 extends Application {
    @Override public void onCreate() {
        super.onCreate();
        String dsn = BuildConfig.SENTRY_DSN;
        if (dsn == null || dsn.trim().isEmpty()) {
            Log.e("FlourDistribution", "SENTRY_DSN is empty");
            return;
        }
        Sentry.init(this, options -> {
            options.setDsn(dsn);
            options.setAttachStacktrace(true);
            options.setEnableAutoSessionTracking(true);
        });
        Sentry.captureMessage("Application startup reached", SentryLevel.INFO);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { Sentry.captureException(error); Sentry.flush(3000); } catch (Throwable ignored) {}
            Thread.getDefaultUncaughtExceptionHandler();
        });
    }
}
