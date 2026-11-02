package com.wdesign.wiseiptv.mobile.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.wdesign.wiseiptv.mobile.R;
import com.wdesign.wiseiptv.mobile.util.MobileNavigation;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        MobileNavigation.bind(this, findViewById(R.id.bottom_nav), R.id.nav_settings);
        String version = "2.0.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.tv_about_version)).setText("Version " + version);
        findViewById(R.id.btn_about_website).setOnClickListener(v -> openUrl(
            "https://wise.alwaysdata.net/iptv/public/"));
        findViewById(R.id.btn_about_support).setOnClickListener(v -> openUrl(
            "https://wa.me/240555445514"));
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
    }
}