package com.tahen.app;

import android.app.Application;
import android.util.Log;

import io.sentry.Sentry;

public class CrashReporterApplication extends Application {
    private static final String TAG = "Sanabil";

    @Override
    public void onCreate() {
        super.onCreate();
        String dsn = BuildConfig.SENTRY_DSN == null ? "" : BuildConfig.SENTRY_DSN.trim();
        if (dsn.isEmpty()) {
            Log.w(TAG, "Sentry DSN is empty; crash reporting is disabled.");
            return;
        }
        try {
            Sentry.init(this, options -> {
                options.setDsn(dsn);
                options.setEnvironment("production");
                options.setRelease(getPackageName() + "@" + BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
                options.setSendDefaultPii(false);
                options.setEnableAutoSessionTracking(true);
                options.setAttachStacktrace(true);
            });
        } catch (Throwable e) {
            Log.e(TAG, "Sentry initialization failed", e);
        }
    }
}
