package com.tahen.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class CrashCaptureActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringBuilder out = new StringBuilder();
                out.append("APP CRASH\n\n");
                out.append("Thread: ").append(thread == null ? "unknown" : thread.getName()).append("\n\n");
                out.append("Exception: ").append(error == null ? "null" : error.getClass().getName()).append("\n");
                out.append("Message: ").append(error == null ? "null" : String.valueOf(error.getMessage())).append("\n\n");
                if (error != null) {
                    for (StackTraceElement e : error.getStackTrace()) {
                        out.append("at ").append(e.toString()).append("\n");
                    }
                    Throwable cause = error.getCause();
                    if (cause != null) {
                        out.append("\nCAUSE: ").append(cause.getClass().getName()).append("\n");
                        out.append("Cause message: ").append(String.valueOf(cause.getMessage())).append("\n");
                        for (StackTraceElement e : cause.getStackTrace()) {
                            out.append("at ").append(e.toString()).append("\n");
                        }
                    }
                }
                final String text = out.toString();
                runOnUiThread(() -> showError(text));
            } catch (Throwable ignored) {
                showError("APP CRASH\n\nUnable to display crash details.");
            }
        });

        showError("Crash capture is active.\n\nIf the app crashes, the error details will be shown here.");
    }

    private void showError(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.DKGRAY);
        view.setGravity(Gravity.START | Gravity.TOP);
        view.setPadding(24, 32, 24, 32);
        setContentView(view);
    }
}
