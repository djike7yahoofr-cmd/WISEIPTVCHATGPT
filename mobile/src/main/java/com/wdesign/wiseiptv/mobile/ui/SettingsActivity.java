package com.wdesign.wiseiptv.mobile.ui;
import android.os.Bundle;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.wdesign.wiseiptv.mobile.R;
import com.wdesign.wiseiptv.mobile.util.MobileNavigation;
import com.wdesign.wiseiptv.core.player.PlayerEngine;
public class SettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_settings);
        MobileNavigation.bind(this, findViewById(R.id.bottom_nav), R.id.nav_settings);
        Button btnAdd = findViewById(R.id.btn_add_playlist);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_OPEN_ADD_PLAYLIST, true);
            startActivity(intent);
        });
        findViewById(R.id.btn_website).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wise.alwaysdata.net/iptv/public/"));
            if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        });
        findViewById(R.id.btn_about).setOnClickListener(v ->
            startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btn_settings_support).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/240555445514"));
            if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        });
        findViewById(R.id.btn_grid_columns).setOnClickListener(v -> chooseGridColumns());
        findViewById(R.id.btn_playback_quality).setOnClickListener(v -> chooseQuality());
    }

    private void chooseGridColumns() {
        String[] options = {"2 colonnes", "3 colonnes", "4 colonnes", "5 colonnes"};
        int selected = getSharedPreferences("wise_display", MODE_PRIVATE)
            .getInt("grid_columns", 3) - 2;
        new AlertDialog.Builder(this)
            .setTitle("Disposition de la grille")
            .setSingleChoiceItems(options, Math.max(0, Math.min(3, selected)), (dialog, which) -> {
                getSharedPreferences("wise_display", MODE_PRIVATE).edit()
                    .putInt("grid_columns", which + 2).apply();
                dialog.dismiss();
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void chooseQuality() {
        int selected = getSharedPreferences("wise_display", MODE_PRIVATE)
            .getInt("preferred_quality", 0);
        new AlertDialog.Builder(this)
            .setTitle("Qualité vidéo par défaut")
            .setSingleChoiceItems(PlayerEngine.QUALITY_LABELS, selected, (dialog, which) -> {
                getSharedPreferences("wise_display", MODE_PRIVATE).edit()
                    .putInt("preferred_quality", which).apply();
                dialog.dismiss();
            })
            .setNegativeButton("Annuler", null)
            .show();
    }
}
