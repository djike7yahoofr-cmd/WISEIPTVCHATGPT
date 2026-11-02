package com.wdesign.wiseiptv.mobile.util;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.wdesign.wiseiptv.mobile.R;
import com.wdesign.wiseiptv.mobile.ui.MainActivity;
import com.wdesign.wiseiptv.mobile.ui.ProfileActivity;
import com.wdesign.wiseiptv.mobile.ui.SettingsActivity;

public final class MobileNavigation {

    private MobileNavigation() {}

    public static void bind(AppCompatActivity activity, BottomNavigationView nav, int selectedId) {
        nav.setSelectedItemId(selectedId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId
                && ((id == R.id.nav_account && activity instanceof ProfileActivity)
                    || (id == R.id.nav_settings && activity instanceof SettingsActivity))) return true;
            Intent intent;
            if (id == R.id.nav_account) {
                intent = new Intent(activity, ProfileActivity.class);
            } else if (id == R.id.nav_settings) {
                intent = new Intent(activity, SettingsActivity.class);
            } else {
                intent = new Intent(activity, MainActivity.class);
                int tab = id == R.id.nav_favorites ? 4 : id == R.id.nav_live ? 1 : 0;
                intent.putExtra(MainActivity.EXTRA_OPEN_TAB, tab);
            }
            activity.startActivity(intent);
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        });
    }
}