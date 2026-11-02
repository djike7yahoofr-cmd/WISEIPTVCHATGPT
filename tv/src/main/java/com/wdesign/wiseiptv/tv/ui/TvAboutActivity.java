package com.wdesign.wiseiptv.tv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import com.wdesign.wiseiptv.tv.R;

public class TvAboutActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_about);
        String version = "2.0.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.tv_tv_about_version)).setText("Version " + version);
        findViewById(R.id.btn_tv_about_site).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wise.alwaysdata.net/iptv/public/"));
            if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        });
    }
}