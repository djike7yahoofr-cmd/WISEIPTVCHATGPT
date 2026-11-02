package com.wdesign.wiseiptv.core.player;

import androidx.media3.exoplayer.DefaultLoadControl;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.OptIn;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.trackselection.*;
import androidx.media3.datasource.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.ui.PlayerView;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

@OptIn(markerClass = UnstableApi.class)
public class PlayerEngine {
    private static final String TAG = "PlayerEngine";

    public interface Listener {
        void onBuffering(boolean buffering);
        void onPlaying();
        void onError(String message);
    }

    public static final int[] QUALITY_BITRATES = {0, 200_000, 500_000, 1_000_000, 2_500_000, 5_000_000, 15_000_000};
    public static final String[] QUALITY_LABELS = {"Auto","240p","360p","480p","720p","1080p","4K"};
    
    private ExoPlayer exoPlayer;
    private final Context context;
    private final Listener listener;
    private int currentQuality = 0;

    public PlayerEngine(Context ctx, Listener l) {
        this.context = ctx.getApplicationContext(); 
        this.listener = l; 
        buildPlayer();
    }

    private void buildPlayer() {
        // OPTIMISATION CLIENT HTTP : Reconnexion automatique active (Anti-Freezing)
        OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // Relance instantanément la requête si le flux lâche
            .build();

        DataSource.Factory dsFactory = new OkHttpDataSource.Factory(http);

        // OPTIMISATION BUFFERING (DefaultLoadControl) : Évite les micro-coupures
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25_000,  // Min Buffer (Défaut: 15s -> Passé à 25s pour encaisser la gigue réseau)
                60_000,  // Max Buffer (Garde jusqu'à 60s de flux max en mémoire)
                2_500,   // Buffer initial requis avant de lancer la vidéo (Sécurité à l'ouverture)
                5_000    // Buffer requis pour relancer après un freeze (Évite les boucles de buffering)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build();

        DefaultTrackSelector ts = new DefaultTrackSelector(context);
        ts.setParameters(ts.buildUponParameters().setAllowVideoMixedMimeTypeAdaptiveness(true).build());

        exoPlayer = new ExoPlayer.Builder(context)
            .setLoadControl(lc)
            .setTrackSelector(ts)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dsFactory))
            .build();

        exoPlayer.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_BUFFERING) {
                    listener.onBuffering(true);
                } else if (s == Player.STATE_READY) { 
                    listener.onBuffering(false); 
                    listener.onPlaying(); 
                }
            }

            @Override public void onPlayerError(PlaybackException e) {
                Log.e(TAG, "ExoPlayer Error: " + e.getMessage());
                listener.onError(e.getMessage() != null ? e.getMessage() : "Erreur de lecture du flux");
            }
        });
    }

    public void attachView(PlayerView v) { 
        v.setPlayer(exoPlayer); 
    }

    public void play(String url) {
        if (url == null || url.isEmpty()) return;
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        exoPlayer.setPlayWhenReady(true); 
        exoPlayer.prepare(); 
        applyQuality(currentQuality);
    }

    public void stop()   { if (exoPlayer != null) exoPlayer.stop(); }
    public void pause()  { if (exoPlayer != null) exoPlayer.pause(); }
    public void resume() { if (exoPlayer != null) exoPlayer.play(); }
    
    public boolean isPlaying() { return exoPlayer != null && exoPlayer.isPlaying(); }
    public long getPosition()  { return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0; }
    public void seekTo(long ms) { if (exoPlayer != null) exoPlayer.seekTo(ms); }
    
    public void setQuality(int index) { currentQuality = index; applyQuality(index); }
    public int getQuality() { return currentQuality; }

    private void applyQuality(int i) {
        if (exoPlayer == null) return;
        TrackSelectionParameters.Builder b = exoPlayer.getTrackSelectionParameters().buildUpon();
        if (i == 0) b.setMaxVideoBitrate(Integer.MAX_VALUE);
        else b.setMaxVideoBitrate(QUALITY_BITRATES[i]);
        exoPlayer.setTrackSelectionParameters(b.build());
    }

    public void release() {
        if (exoPlayer != null) { 
            exoPlayer.stop(); 
            exoPlayer.release(); 
            exoPlayer = null; 
        }
    }
}