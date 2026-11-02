package com.wdesign.wiseiptv.tv.ui;

import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.OptIn;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import com.wdesign.wiseiptv.core.db.AppDatabase;
import com.wdesign.wiseiptv.core.db.entity.ChannelEntity;
import com.wdesign.wiseiptv.core.player.PlayerEngine;
import com.wdesign.wiseiptv.tv.R;
import java.util.concurrent.Executors;

@OptIn(markerClass = UnstableApi.class)
public class TvPlayerActivity extends FragmentActivity implements PlayerEngine.Listener {
    public static final String EXTRA_ID    = "id";
    public static final String EXTRA_NAME  = "name";
    public static final String EXTRA_URL   = "url";
    public static final String EXTRA_TYPE  = "type";
    public static final String EXTRA_GROUP = "group";
    public static final String EXTRA_ORDER = "order";
    public static final String EXTRA_START_POSITION = "start_position";

    private PlayerView playerView;
    private PlayerEngine engine;
    private TextView tvName, tvQuality, tvStatus, tvGroup;
    private ProgressBar progressBar;
    private View osd;
    private AppDatabase db;
    private long channelId;
    private String channelName, streamUrl, groupTitle;
    private int contentType, sortOrder;
    private final Handler osdHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideOsd = () -> osd.animate().alpha(0).setDuration(400).start();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_tv_player);
        db = AppDatabase.get(this);

        channelId   = getIntent().getLongExtra(EXTRA_ID, -1);
        channelName = getIntent().getStringExtra(EXTRA_NAME);
        streamUrl   = getIntent().getStringExtra(EXTRA_URL);
        contentType = getIntent().getIntExtra(EXTRA_TYPE, 0);
        groupTitle  = getIntent().getStringExtra(EXTRA_GROUP);
        sortOrder   = getIntent().getIntExtra(EXTRA_ORDER, 0);

        playerView  = findViewById(R.id.tv_player_view);
        progressBar = findViewById(R.id.progress_bar);
        tvName      = findViewById(R.id.tv_channel_name);
        tvQuality   = findViewById(R.id.tv_quality);
        tvStatus    = findViewById(R.id.tv_status);
        tvGroup     = findViewById(R.id.tv_group);
        osd         = findViewById(R.id.osd);

        tvName.setText(channelName);
        if (tvGroup != null && groupTitle != null) tvGroup.setText(groupTitle);
        tvQuality.setText("Auto");

        engine = new PlayerEngine(this, this);
        engine.attachView(playerView);
        int preferredQuality = getSharedPreferences("wise_tv_display", MODE_PRIVATE)
            .getInt("preferred_quality", 0);
        preferredQuality = Math.max(0, Math.min(PlayerEngine.QUALITY_LABELS.length - 1,
            preferredQuality));
        engine.setQuality(preferredQuality);
        tvQuality.setText(PlayerEngine.QUALITY_LABELS[preferredQuality]);
        engine.play(streamUrl);
        long startPosition = getIntent().getLongExtra(EXTRA_START_POSITION, 0);
        if (startPosition > 0) engine.seekTo(startPosition);
        showOsd();
    }

    /** Zapping TV: DPAD_LEFT = précédent, DPAD_RIGHT = suivant */
    private void zapChannel(int dir) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ChannelEntity target = dir < 0
                ? db.channelDao().getPrev(sortOrder, contentType, groupTitle)
                : db.channelDao().getNext(sortOrder, contentType, groupTitle);
            if (target == null) return;
            runOnUiThread(() -> {
                channelId   = target.id;
                channelName = target.name;
                streamUrl   = target.streamUrl;
                groupTitle  = target.groupTitle;
                sortOrder   = target.sortOrder;
                tvName.setText(channelName);
                if (tvGroup != null && groupTitle != null) tvGroup.setText(groupTitle);
                engine.play(streamUrl);
                showOsd();
            });
        });
    }

    @Override public void onBuffering(boolean b) {
        runOnUiThread(() -> { progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
            tvStatus.setVisibility(b ? View.VISIBLE : View.GONE);
            if (b) tvStatus.setText("Chargement…"); });
    }
    @Override public void onPlaying() { runOnUiThread(() -> { tvStatus.setVisibility(View.GONE); progressBar.setVisibility(View.GONE); }); }
    @Override public void onError(String msg) { runOnUiThread(() -> { tvStatus.setText("⚠ " + msg); tvStatus.setVisibility(View.VISIBLE); progressBar.setVisibility(View.GONE); }); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (engine == null) return super.onKeyDown(keyCode, event);
        showOsd();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (engine.isPlaying()) engine.pause(); else engine.resume(); return true;

            // Zapping chaîne (gauche/droite)
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                zapChannel(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                zapChannel(+1); return true;

            // Qualité (haut/bas)
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
                int q = Math.min(engine.getQuality()+1, PlayerEngine.QUALITY_LABELS.length-1);
                engine.setQuality(q); tvQuality.setText(PlayerEngine.QUALITY_LABELS[q]); return true; }
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_REWIND: {
                int q = Math.max(engine.getQuality()-1, 0);
                engine.setQuality(q); tvQuality.setText(PlayerEngine.QUALITY_LABELS[q]); return true; }

            case KeyEvent.KEYCODE_BACK: finish(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showOsd() {
        osd.animate().alpha(1).setDuration(200).start();
        osdHandler.removeCallbacks(hideOsd); osdHandler.postDelayed(hideOsd, 5000);
    }
    @Override protected void onStop() { super.onStop(); if (engine != null) engine.pause(); }
    @Override protected void onDestroy() {
        super.onDestroy(); osdHandler.removeCallbacksAndMessages(null);
        if (engine != null) { engine.release(); engine = null; }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
