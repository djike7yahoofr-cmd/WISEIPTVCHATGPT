package com.wdesign.wiseiptv.tv.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import com.wdesign.wiseiptv.core.security.DeviceSecurity;
import com.wdesign.wiseiptv.tv.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TvProfileActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_profile);
        SharedPreferences prefs = getSharedPreferences("wise_activation_tv", MODE_PRIVATE);
        String login = prefs.getString("login", "");
        String expiry = prefs.getString("expires_at", "");
        String status = prefs.getString("status", "INACTIVE");
        ((TextView) findViewById(R.id.tv_tv_profile_login)).setText(
            login.isEmpty() ? "Abonné WiseIPTV" : login);
        ((TextView) findViewById(R.id.tv_tv_profile_key)).setText(
            "Clé appareil : " + DeviceSecurity.getOrCreateKey(this));
        ((TextView) findViewById(R.id.tv_tv_profile_subscription)).setText(
            "ACTIVE".equals(status) ? "Abonnement actif\nExpire le : " + expiry
                : "Aucun abonnement actif");
        showExpiryNotice(status, expiry);
        findViewById(R.id.btn_tv_renew).setOnClickListener(v -> openUrl(
            "https://wa.me/240555445514?text=Bonjour%20WiseIPTV,%20je%20souhaite%20renouveler%20mon%20abonnement."));
        findViewById(R.id.btn_tv_profile_about).setOnClickListener(v ->
            startActivity(new Intent(this, TvAboutActivity.class)));
        findViewById(R.id.btn_tv_profile_site).setOnClickListener(v -> openUrl(
            "https://wise.alwaysdata.net/iptv/public/"));
        findViewById(R.id.btn_tv_profile_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_tv_renew).setNextFocusDownId(R.id.btn_tv_profile_about);
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
            showExpiryAlert(remaining <= 0 || "EXPIRED".equals(status)
                ? "Votre abonnement a expiré. Renouvelez pour retrouver vos chaînes."
                : "Votre abonnement expire dans " + days + " jour(s).");
        } catch (Exception ignored) {
            findViewById(R.id.tv_expiry_alert).setVisibility(View.GONE);
        }
    }

    private void showExpiryAlert(String message) {
        View alert = findViewById(R.id.tv_expiry_alert);
        alert.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tv_tv_expiry_message)).setText(message);
        alert.setAlpha(0f);
        alert.setTranslationY(8f);
        alert.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
    }
}