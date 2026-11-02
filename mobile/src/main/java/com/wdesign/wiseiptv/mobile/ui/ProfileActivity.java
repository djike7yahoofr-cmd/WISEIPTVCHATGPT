package com.wdesign.wiseiptv.mobile.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.wdesign.wiseiptv.core.db.AppDatabase;
import com.wdesign.wiseiptv.core.security.DeviceSecurity;
import com.wdesign.wiseiptv.mobile.R;
import com.wdesign.wiseiptv.mobile.util.MobileNavigation;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        MobileNavigation.bind(this, findViewById(R.id.bottom_nav), R.id.nav_account);

        SharedPreferences prefs = getSharedPreferences("wise_activation", MODE_PRIVATE);
        String login = prefs.getString("login", "");
        String expiry = prefs.getString("expires_at", "");
        String status = prefs.getString("status", "INACTIVE");
        ((TextView) findViewById(R.id.tv_profile_login)).setText(
            login.isEmpty() ? "Abonné WiseIPTV" : login);
        ((TextView) findViewById(R.id.tv_profile_device)).setText(
            "Clé appareil : " + DeviceSecurity.getOrCreateKey(this));

        TextView subscription = findViewById(R.id.tv_subscription);
        subscription.setText("ACTIVE".equals(status)
            ? "Abonnement actif\nExpire le : " + (expiry.isEmpty() ? "Non renseigné" : expiry)
            : "Aucun abonnement actif");
        showExpiryNotice(status, expiry);
        findViewById(R.id.btn_profile_website).setOnClickListener(v -> openUrl(
            "https://wise.alwaysdata.net/iptv/public/"));
        findViewById(R.id.btn_renew).setOnClickListener(v -> openUrl(
            "https://wa.me/240555445514?text=Bonjour%20WiseIPTV,%20je%20souhaite%20renouveler%20mon%20abonnement."));
        loadStats();
    }

    private void showExpiryNotice(String status, String expiry) {
        if (expiry.isEmpty()) {
            if ("EXPIRED".equals(status)) showExpiryAlert(
                "Votre abonnement a expiré. Renouvelez pour retrouver vos chaînes.");
            return;
        }
        if (!"ACTIVE".equals(status) && !"EXPIRED".equals(status)) return;
        try {
            Date end = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(expiry);
            if (end == null) return;
            long remaining = end.getTime() - System.currentTimeMillis();
            long days = remaining > 0 ? (remaining + 86399999L) / 86400000L : -1;
            if (days > 5) return;
            String message = remaining <= 0 || "EXPIRED".equals(status)
                ? "Votre abonnement a expiré. Renouvelez pour retrouver vos chaînes."
                : "Votre abonnement expire dans " + days + " jour(s).";
            showExpiryAlert(message);
        } catch (Exception ignored) {
            findViewById(R.id.expiry_alert).setVisibility(View.GONE);
        }
    }

    private void showExpiryAlert(String message) {
        View alert = findViewById(R.id.expiry_alert);
        alert.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tv_expiry_alert)).setText(message);
        alert.setAlpha(0f);
        alert.setTranslationY(8f);
        alert.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }

    private void loadStats() {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.get(this);
            int channels = db.channelDao().count();
            int movies = db.channelDao().countByType(1);
            int series = db.channelDao().countByType(2);
            int favorites = db.channelDao().countFavorites();
            runOnUiThread(() -> {
                setStat(R.id.stat_channels, channels, "Chaînes");
                setStat(R.id.stat_movies, movies, "Films");
                setStat(R.id.stat_series, series, "Séries");
                setStat(R.id.stat_favorites, favorites, "Favoris");
            });
        });
    }

    private void setStat(int id, int value, String label) {
        ((TextView) findViewById(id)).setText(value + "\n" + label);
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}