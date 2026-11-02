package com.wdesign.wiseiptv.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.fragment.app.FragmentActivity;
import com.wdesign.wiseiptv.tv.R;

public class SplashActivity extends FragmentActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openActivation = () -> {
        startActivity(new Intent(this, TvActivationActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        animateEntrance(R.id.splash_logo, 0);
        animateEntrance(R.id.splash_title, 120);
        animateEntrance(R.id.splash_tagline, 220);
        handler.postDelayed(openActivation, 1400);
    }

    private void animateEntrance(int viewId, long delay) {
        View view = findViewById(viewId);
        view.setAlpha(0f);
        view.setTranslationY(12f);
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420).start();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openActivation);
        super.onDestroy();
    }
}