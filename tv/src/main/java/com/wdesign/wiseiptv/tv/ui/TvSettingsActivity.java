package com.wdesign.wiseiptv.tv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import com.wdesign.wiseiptv.tv.R;
import com.wdesign.wiseiptv.core.player.PlayerEngine;

public class TvSettingsActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_settings);
        findViewById(R.id.btn_tv_settings_playlists).setOnClickListener(v ->
            startActivity(new Intent(this, PlaylistManagerActivity.class)));
        findViewById(R.id.btn_tv_settings_quality).setOnClickListener(v -> chooseQuality());
        findViewById(R.id.btn_tv_settings_profile).setOnClickListener(v ->
            startActivity(new Intent(this, TvProfileActivity.class)));
        findViewById(R.id.btn_tv_settings_about).setOnClickListener(v ->
            startActivity(new Intent(this, TvAboutActivity.class)));
        findViewById(R.id.btn_tv_settings_site).setOnClickListener(v -> openWebsite());
        findViewById(R.id.btn_tv_settings_playlists).requestFocus();
    }

    private void openWebsite() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://wise.alwaysdata.net/iptv/public/"));
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
    }

    private void chooseQuality() {
        int selected = getSharedPreferences("wise_tv_display", MODE_PRIVATE)
            .getInt("preferred_quality", 0);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Qualité vidéo par défaut")
            .setSingleChoiceItems(PlayerEngine.QUALITY_LABELS, selected, (dialog, which) -> {
                getSharedPreferences("wise_tv_display", MODE_PRIVATE).edit()
                    .putInt("preferred_quality", which).apply();
                dialog.dismiss();
            })
            .setNegativeButton("Annuler", null)
            .show();
    }
}