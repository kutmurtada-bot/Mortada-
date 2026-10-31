package com.tahen.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class SafeLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        TextView test = new TextView(this);
        test.setText("اختبار تشغيل التطبيق\n\nإذا ظهرت هذه الشاشة، فالنظام الأساسي للتطبيق يعمل.\nالمشكلة ستكون داخل WebView أو واجهة HTML.");
        test.setTextSize(18);
        test.setTextColor(Color.DKGRAY);
        test.setGravity(Gravity.CENTER);
        test.setPadding(40, 40, 40, 40);
        setContentView(test);
    }
}
