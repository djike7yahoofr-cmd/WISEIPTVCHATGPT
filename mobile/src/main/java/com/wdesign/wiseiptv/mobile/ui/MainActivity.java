package com.wdesign.wiseiptv.mobile.ui;

import android.content.*;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.wdesign.wiseiptv.core.db.AppDatabase;
import com.wdesign.wiseiptv.core.db.entity.ChannelEntity;
import com.wdesign.wiseiptv.core.db.entity.PlaylistEntity;
import com.wdesign.wiseiptv.core.player.PlayerEngine;
import com.wdesign.wiseiptv.mobile.R;
import com.wdesign.wiseiptv.mobile.adapter.ChannelAdapter;
import com.wdesign.wiseiptv.mobile.util.PlaylistLoader;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * MainActivity — s'ouvre DIRECTEMENT depuis ActivationActivity.
 * Le téléchargement des playlists se fait ici, en background, DNS par DNS.
 *
 * Flux identique à TvMainActivity :
 *  1. Lit les extras passés par ActivationActivity (login + dnsUrls)
 *  2. Sinon lit les SharedPreferences "wise_activation" (reboot / rotation)
 *  3. Sinon refresh stale des playlists manuelles
 *  4. processNextDns() : séquentiel, auto-détect Xtream vs M3U URL
 */
@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity implements ChannelAdapter.OnChannelClick {

    public static final String EXTRA_OPEN_ADD_PLAYLIST = "open_add_playlist";
    public static final String EXTRA_OPEN_TAB = "open_tab";

    // Extras reçus depuis ActivationActivity
    public static final String EXTRA_LOGIN    = "act_login";
    public static final String EXTRA_PASSWORD = "act_password";
    public static final String EXTRA_EXPIRES  = "act_expires";
    public static final String EXTRA_DNS_URLS = "act_dns_urls";
    public static final String EXTRA_DNS_EPGS = "act_dns_epg_urls";

    // Alias attendus par ActivationActivity
    public static final String EXTRA_ACT_LOGIN       = EXTRA_LOGIN;
    public static final String EXTRA_ACT_PASSWORD    = EXTRA_PASSWORD;
    public static final String EXTRA_ACT_EXPIRES     = EXTRA_EXPIRES;
    public static final String EXTRA_ACT_DNS_URLS    = EXTRA_DNS_URLS;
    public static final String EXTRA_ACT_DNS_EPG_URLS = EXTRA_DNS_EPGS;

    private static final String TAG   = "MainActivity";
    private static final String PREFS = "wise_activation";

    private RecyclerView          rvChannels;
    private ChannelAdapter        adapter;
    private ProgressBar           progressBar;
    private TextView              tvEmpty, tvSyncStatus;
    private TabLayout             tabLayout;
    private Spinner               spinnerGroup;
    private BottomNavigationView  bottomNav;
    private SearchView            searchView;
    private ImageButton            btnGrid;
    private GridLayoutManager      gridLayoutManager;
    private ImageView              homeBackdrop;
    private FrameLayout            homePreviewContainer;
    private PlayerView             homePreviewView;
    private TextView               tvHomePreviewName;
    private TextView               tvHomeGreeting, tvHomeSubscription;
    private PlayerEngine           homePreviewEngine;
    private List<ChannelEntity>    homeChannels = new java.util.ArrayList<>();
    private long                   previewChannelId = -1;
    private long                   previewPositionMs;
    private boolean                showingHome = true;
    private static final String    PREFS_RECENTS = "wise_mobile_recents";
    private static final int       MAX_RECENTS = 20;
    private AppDatabase           db;
    private int                   currentTab = 0;
    private String                selectedGroup;

    // Executor unique pour toute la chaîne processNextDns — évite conflits Room multi-thread
    private final java.util.concurrent.ExecutorService syncExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRecentPreview = this::previewVisibleRecent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db           = AppDatabase.get(this);
        rvChannels   = findViewById(R.id.rv_channels);
        progressBar  = findViewById(R.id.progress_bar);
        tvEmpty      = findViewById(R.id.tv_empty);
        tvSyncStatus = findViewById(R.id.tv_sync_status);
        tabLayout    = findViewById(R.id.tab_layout);
        spinnerGroup = findViewById(R.id.spinner_group);
        bottomNav    = findViewById(R.id.bottom_nav);
        searchView   = findViewById(R.id.search_view);
        btnGrid      = findViewById(R.id.btn_grid);
        homeBackdrop = findViewById(R.id.home_backdrop);
        homePreviewContainer = findViewById(R.id.home_preview_container);
        homePreviewView = findViewById(R.id.home_preview_player);
        tvHomePreviewName = findViewById(R.id.tv_home_preview_name);
        tvHomeGreeting = findViewById(R.id.tv_home_greeting);
        tvHomeSubscription = findViewById(R.id.tv_home_subscription);

        adapter = new ChannelAdapter(this);
        int gridColumns = getSharedPreferences("wise_display", MODE_PRIVATE)
            .getInt("grid_columns", 3);
        gridColumns = Math.max(2, Math.min(5, gridColumns));
        gridLayoutManager = new GridLayoutManager(this, gridColumns);
        rvChannels.setLayoutManager(gridLayoutManager);
        rvChannels.setAdapter(adapter);
        rvChannels.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@androidx.annotation.NonNull RecyclerView view, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE && showingHome) {
                    previewHandler.removeCallbacks(updateRecentPreview);
                    previewHandler.postDelayed(updateRecentPreview, 350);
                }
            }
        });

        setupGridChooser();
        setupTabs();
        setupGroupFilter();
        setupSearch();
        setupBottomNav();
        startBackgroundSync();
        handlePendingActions(getIntent());
    }

    // ── Sync background ───────────────────────────────────────────

    private void startBackgroundSync() {
        Intent intent = getIntent();

        // Chemin 1 : extras frais depuis ActivationActivity
        String login    = intent.getStringExtra(EXTRA_LOGIN);
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        String[] dnsUrls = intent.getStringArrayExtra(EXTRA_DNS_URLS);
        String[] dnsEpgs = intent.getStringArrayExtra(EXTRA_DNS_EPGS);

        if (login != null && dnsUrls != null && dnsUrls.length > 0) {
            Log.d(TAG, "Sync ch1: extras DNS=" + dnsUrls.length);
            syncExecutor.execute(() ->
                processNextDns(0, dnsUrls, login,
                    password != null ? password : "",
                    dnsEpgs != null ? dnsEpgs : new String[0], 0));
            return;
        }

        // Chemin 2 : SharedPreferences (reboot / rotation)
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String savedStatus = prefs.getString("status",   "UNKNOWN");
        String savedLogin  = prefs.getString("login",    null);
        String savedPass   = prefs.getString("password", "");
        String savedDnsRaw = prefs.getString("dns_urls", "");

        if ("ACTIVE".equals(savedStatus) && savedLogin != null && !savedDnsRaw.isEmpty()) {
            // Vérifier si DB déjà peuplée — éviter re-sync inutile
            syncExecutor.execute(() -> {
                boolean empty = (db.channelDao().count() == 0);
                long lastSync = prefs.getLong("last_sync_ts", 0);
                long weekMs   = 7L * 24 * 60 * 60 * 1000;
                boolean stale = (System.currentTimeMillis() - lastSync) > weekMs;
                if (!empty && !stale) {
                    Log.d(TAG, "Sync ch2: DB ok + sync récente, skip");
                    return;
                }
                Log.d(TAG, "Sync ch2: prefs dns=" + savedDnsRaw);
                String[] urls = savedDnsRaw.split(",");
                String[] epgs = prefs.getString("dns_epg_urls", "").split(",");
                processNextDns(0, urls, savedLogin, savedPass, epgs, 0);
            });
            return;
        }

        // Chemin 3 : playlists manuelles stale
        Log.d(TAG, "Sync ch3: stale refresh");
        PlaylistLoader.refreshStaleIfNeeded(db, new PlaylistLoader.Callback() {
            @Override public void onDone(int c) {
                if (c > 0 && !isFinishing() && !isDestroyed())
                    runOnUiThread(MainActivity.this::observeCurrentTab);
            }
            @Override public void onError(String msg) { Log.w(TAG, "stale: " + msg); }
        });
    }

    /**
     * Traitement séquentiel DNS — identique à TvMainActivity.
     * Appels DB via syncExecutor uniquement → pas de conflit Room.
     * PlaylistLoader.load() callbacks re-postés sur syncExecutor.
     */
    private void processNextDns(int index, String[] dnsUrls, String login,
                                 String password, String[] epgUrls, int accumulated) {
        if (index >= dnsUrls.length) {
            // Tous les DNS traités
            final int total = accumulated;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                showSync(false, null);
                if (showingHome) showRecentHome(); else observeCurrentTab();
                if (total > 0)
                    Toast.makeText(this, "✅ " + total + " chaînes chargées",
                        Toast.LENGTH_SHORT).show();
                else {
                    // DB vide → proposer ajout manuel
                    syncExecutor.execute(() -> {
                        if (db.channelDao().count() == 0)
                            runOnUiThread(this::showAddPlaylistDialog);
                    });
                }
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong("last_sync_ts", System.currentTimeMillis()).apply();
            });
            return;
        }

        String url = dnsUrls[index].trim();
        if (url.isEmpty()) {
            processNextDns(index + 1, dnsUrls, login, password, epgUrls, accumulated);
            return;
        }

        final int idx   = index + 1;
        final int total = dnsUrls.length;
        runOnUiThread(() -> showSync(true, "📥 Serveur " + idx + "/" + total + "…"));

        // findOrCreatePlaylist s'exécute sur syncExecutor (déjà le cas ici)
        PlaylistEntity pl = findOrCreatePlaylist(login, password, url, idx);

        PlaylistLoader.load(pl, db, new PlaylistLoader.Callback() {
            @Override public void onDone(int count) {
                Log.d(TAG, "DNS " + idx + " OK: " + count + " ch");
                // Re-poster sur syncExecutor — évite appel DB sur thread PlaylistLoader
                syncExecutor.execute(() ->
                    processNextDns(index + 1, dnsUrls, login, password, epgUrls,
                        accumulated + count));
            }
            @Override public void onError(String msg) {
                Log.w(TAG, "DNS " + idx + " err: " + msg);
                syncExecutor.execute(() ->
                    processNextDns(index + 1, dnsUrls, login, password, epgUrls, accumulated));
            }
        });
    }

    /**
     * Auto-détecte Xtream (get.php / username= / base serveur) vs M3U URL (.m3u/.m3u8).
     * Exemples :
     *   https://tvradiozap.eu/get.php?username=x&password=y  → XTREAM
     *   https://iptv-org.github.io/iptv/languages/eng.m3u    → M3U URL
     */
    private PlaylistEntity findOrCreatePlaylist(String login, String password,
                                                 String dnsUrl, int idx) {
        // Chercher une playlist existante avec cette URL + login
        PlaylistEntity existing = db.playlistDao().findByUrlAndLogin(dnsUrl, login);
        if (existing != null) {
            existing.password = password;
            existing.isActive = true;
            db.playlistDao().update(existing);
            return existing;
        }
        PlaylistEntity pl = new PlaylistEntity();
        String low = dnsUrl.toLowerCase();
        // Détecter M3U : se termine par .m3u ou .m3u8, ou contient "m3u" SANS "get.php"/"username"
        boolean isM3uUrl = low.endsWith(".m3u") || low.endsWith(".m3u8")
            || (low.contains("m3u") && !low.contains("get.php") && !low.contains("username="));
        if (isM3uUrl) {
            pl.type = PlaylistEntity.TYPE_M3U_URL;
            pl.name = "M3U #" + idx;
        } else {
            // Tout le reste → Xtream (get.php, username=, base serveur)
            pl.type = PlaylistEntity.TYPE_XTREAM;
            pl.name = "IPTV #" + idx;
        }
        pl.url      = dnsUrl;
        pl.username = login;
        pl.password = password;
        pl.isActive = true;
        pl.lastUpdated = 0;
        pl.id = db.playlistDao().insert(pl);
        return pl;
    }

    // ── UI helpers ────────────────────────────────────────────────

    private void showSync(boolean show, String msg) {
        if (progressBar  != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (tvSyncStatus != null) {
            tvSyncStatus.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show && msg != null) tvSyncStatus.setText(msg);
        }
        if (!show && tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    }

    // ── Tabs, search, nav ─────────────────────────────────────────

    private void setupTabs() {
        String[] tabs = {"Tout","Live","Films","Séries","Favoris"};
        for (String t : tabs) tabLayout.addTab(tabLayout.newTab().setText(t));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                currentTab = t.getPosition();
                showingHome = false;
                selectedGroup = null;
                spinnerGroup.setVisibility(currentTab == 1 ? View.VISIBLE : View.GONE);
                if (currentTab == 1 && spinnerGroup.getSelectedItemPosition() > 0)
                    spinnerGroup.setSelection(0);
                showCategoryList();
                observeCurrentTab();
                if (currentTab == 1) bottomNav.setSelectedItemId(R.id.nav_live);
                else if (currentTab == 4) bottomNav.setSelectedItemId(R.id.nav_favorites);
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });
    }

    private void observeCurrentTab() {
        switch (currentTab) {
            case 0: db.channelDao().getAll().observe(this, this::updateList); break;
            case 1:
                if (selectedGroup == null) db.channelDao().getLive().observe(this, this::updateList);
                else db.channelDao().getLiveByGroup(selectedGroup).observe(this, this::updateList);
                break;
            case 2: db.channelDao().getFilms().observe(this, this::updateList); break;
            case 3: db.channelDao().getSeries().observe(this, this::updateList); break;
            case 4: db.channelDao().getFavorites().observe(this, this::updateList); break;
        }
    }

    private void setupGroupFilter() {
        if (spinnerGroup == null) return;
        spinnerGroup.setVisibility(View.GONE);
        db.channelDao().getLiveGroups().observe(this, groups -> {
            java.util.ArrayList<String> options = new java.util.ArrayList<>();
            options.add("Toutes catégories");
            if (groups != null) options.addAll(groups);
            ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
            groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerGroup.setAdapter(groupAdapter);
        });
        spinnerGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedGroup = position == 0 ? null : String.valueOf(parent.getItemAtPosition(position));
                if (currentTab == 1 && !showingHome) observeCurrentTab();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateList(List<ChannelEntity> list) {
        if (showingHome) return;
        if (adapter != null) adapter.setData(list);
        if (tvEmpty != null)
            tvEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCategoryList() {
        tabLayout.setVisibility(View.VISIBLE);
        homePreviewContainer.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rvChannels.getLayoutParams();
        params.topMargin = 0;
        rvChannels.setLayoutParams(params);
        if (homePreviewEngine != null) homePreviewEngine.pause();
    }

    private void showRecentHome() {
        showingHome = true;
        tabLayout.setVisibility(View.GONE);
        spinnerGroup.setVisibility(View.GONE);
        homePreviewContainer.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rvChannels.getLayoutParams();
        params.topMargin = Math.round(204 * getResources().getDisplayMetrics().density);
        rvChannels.setLayoutParams(params);
        SharedPreferences activation = getSharedPreferences(PREFS, MODE_PRIVATE);
        String login = activation.getString("login", "");
        tvHomeGreeting.setText(login.isEmpty() ? "Bonjour, bienvenue" : "Bonjour, " + login);
        String status = activation.getString("status", "");
        String expiry = activation.getString("expires_at", "");
        tvHomeSubscription.setText("ACTIVE".equals(status)
            ? "Abonnement actif · expire le " + (expiry.isEmpty() ? "" : expiry)
            : "EXPIRED".equals(status) ? "Abonnement expiré · renouveler"
                : "Votre télévision, partout avec vous");
        tvHomeSubscription.setOnClickListener(v ->
            startActivity(new Intent(this, ProfileActivity.class)));
        homePreviewContainer.setVisibility(View.VISIBLE);
        syncExecutor.execute(() -> {
            String raw = getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE).getString("ids", "");
            List<ChannelEntity> recent = new java.util.ArrayList<>();
            if (!raw.isEmpty()) {
                for (String id : raw.split(",")) {
                    try {
                        ChannelEntity channel = db.channelDao().findById(Long.parseLong(id));
                        if (channel != null) recent.add(channel);
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (recent.isEmpty()) recent.addAll(db.channelDao().searchSync(""));
            runOnUiThread(() -> {
                if (!showingHome || isFinishing() || isDestroyed()) return;
                homeChannels = recent;
                adapter.setData(recent);
                if (tvEmpty != null) {
                    tvEmpty.setText("Aucune chaîne. Ajoutez une playlist pour commencer.");
                    tvEmpty.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recent.isEmpty()) {
                    homePreviewView.setVisibility(View.GONE);
                    tvHomePreviewName.setText("Ajoutez une playlist pour commencer");
                    clearAdaptiveBackdrop();
                } else {
                    homePreviewView.setVisibility(View.VISIBLE);
                    homePreviewContainer.setVisibility(View.VISIBLE);
                    previewRecent(recent.get(0));
                }
            });
        });
    }

    private void previewVisibleRecent() {
        if (!showingHome || homeChannels.isEmpty()) return;
        int position = gridLayoutManager.findFirstVisibleItemPosition();
        if (position >= 0 && position < homeChannels.size()) previewRecent(homeChannels.get(position));
    }

    private void previewRecent(ChannelEntity channel) {
        homePreviewView.setVisibility(View.VISIBLE);
        if (channel.logoUrl != null && !channel.logoUrl.isEmpty()) {
            homeBackdrop.setVisibility(View.VISIBLE);
            Glide.with(this).load(channel.logoUrl).centerCrop().into(homeBackdrop);
            homeBackdrop.animate().alpha(0.18f).setDuration(280).start();
        } else {
            clearAdaptiveBackdrop();
        }
        tvHomePreviewName.setText(channel.name);
        if (homePreviewEngine == null) {
            homePreviewEngine = new PlayerEngine(this, new PlayerEngine.Listener() {
                @Override public void onBuffering(boolean buffering) {}
                @Override public void onPlaying() {}
                @Override public void onError(String message) {}
            });
            homePreviewEngine.attachView(homePreviewView);
        }
        if (previewChannelId != channel.id) {
            previewChannelId = channel.id;
            previewPositionMs = 0;
            homePreviewEngine.play(channel.streamUrl);
        } else if (!homePreviewEngine.isPlaying()) {
            homePreviewEngine.resume();
        }
    }

    private void clearAdaptiveBackdrop() {
        homeBackdrop.animate().alpha(0f).setDuration(240)
            .withEndAction(() -> homeBackdrop.setVisibility(View.GONE)).start();
    }

    private void saveRecent(ChannelEntity channel) {
        String raw = getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE).getString("ids", "");
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        ids.add(String.valueOf(channel.id));
        if (!raw.isEmpty()) {
            for (String id : raw.split(",")) {
                if (ids.size() >= MAX_RECENTS) break;
                if (!id.equals(String.valueOf(channel.id))) ids.add(id);
            }
        }
        getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE).edit()
            .putString("ids", TextUtils.join(",", ids)).apply();
    }

    private void setupSearch() {
        if (searchView == null) return;
        int searchTextId = getResources().getIdentifier("search_src_text", "id", "android");
        TextView searchText = searchView.findViewById(searchTextId);
        if (searchText != null) {
            searchText.setTextColor(getResources().getColor(R.color.white));
            searchText.setHintTextColor(0xFFB8C0CC);
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) {
                if (TextUtils.isEmpty(q)) {
                    if (showingHome) showRecentHome(); else observeCurrentTab();
                    return true;
                }
                showingHome = false;
                showCategoryList();
                db.channelDao().search(q).observe(MainActivity.this, MainActivity.this::updateList);
                return true;
            }
        });
    }

    private void setupGridChooser() {
        if (btnGrid == null) return;
        btnGrid.setOnClickListener(anchor -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
            for (int columns = 2; columns <= 5; columns++) {
                menu.getMenu().add(0, columns, columns, columns + " colonnes");
            }
            menu.setOnMenuItemClickListener(item -> {
                setGridColumns(item.getItemId());
                return true;
            });
            menu.show();
        });
    }

    private void setGridColumns(int columns) {
        gridLayoutManager.setSpanCount(columns);
        getSharedPreferences("wise_display", MODE_PRIVATE).edit()
            .putInt("grid_columns", columns).apply();
    }

    private void setupBottomNav() {
        if (bottomNav == null) return;
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_home)    { showRecentHome(); return true; }
            else if (id == R.id.nav_live) {
                tabLayout.getTabAt(1).select();
                return true;
            }
            else if (id == R.id.nav_favorites) {
                tabLayout.getTabAt(4).select();
                return true;
            }
            else if (id == R.id.nav_account) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            else if (id == R.id.nav_settings){
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    private void handlePendingActions(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_OPEN_TAB)) {
            int tab = intent.getIntExtra(EXTRA_OPEN_TAB, 0);
            if (tab == 0) showRecentHome();
            else if (tab >= 0 && tab < tabLayout.getTabCount()) tabLayout.getTabAt(tab).select();
            intent.removeExtra(EXTRA_OPEN_TAB);
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_ADD_PLAYLIST, false)) {
            intent.removeExtra(EXTRA_OPEN_ADD_PLAYLIST);
            new Handler(Looper.getMainLooper()).post(this::showAddPlaylistDialog);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int columns = getSharedPreferences("wise_display", MODE_PRIVATE)
            .getInt("grid_columns", 3);
        if (gridLayoutManager != null) gridLayoutManager.setSpanCount(columns);
        handlePendingActions(intent);
    }

    // ── Dialog ajout playlist manuel ──────────────────────────────

    public void showAddPlaylistDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("➕ Ajouter une playlist");
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_add_playlist, null);
        EditText etName = v.findViewById(R.id.et_playlist_name);
        EditText etUrl  = v.findViewById(R.id.et_playlist_url);
        b.setView(v);
        b.setPositiveButton("Charger", (d, w) -> {
            String name = etName.getText().toString().trim();
            String url  = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(url)) return;
            showSync(true, "Chargement…");
            syncExecutor.execute(() -> {
                PlaylistEntity pl = new PlaylistEntity();
                pl.name = name.isEmpty() ? "Playlist" : name;
                String low = url.toLowerCase();
                pl.type = (low.endsWith(".m3u") || low.endsWith(".m3u8"))
                    ? PlaylistEntity.TYPE_M3U_URL : PlaylistEntity.TYPE_XTREAM;
                pl.url = url; pl.isActive = true; pl.lastUpdated = 0;
                pl.id = db.playlistDao().insert(pl);
                PlaylistLoader.load(pl, db, new PlaylistLoader.Callback() {
                    @Override public void onDone(int c) {
                        runOnUiThread(() -> {
                            showSync(false, null);
                            if (showingHome) showRecentHome(); else observeCurrentTab();
                            Toast.makeText(MainActivity.this, c + " chaînes ✅",
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> {
                            showSync(false, null);
                            Toast.makeText(MainActivity.this, "Erreur : " + msg,
                                Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        });
        b.setNegativeButton("Annuler", null);
        b.show();
    }

    // ── Clic sur une chaîne ───────────────────────────────────────

    @Override
    public void onClick(ChannelEntity ch) {
        saveRecent(ch);
        previewRecent(ch);
        previewPositionMs = ch.contentType == ChannelEntity.TYPE_LIVE
            || ch.id != previewChannelId || homePreviewEngine == null
            ? 0 : homePreviewEngine.getPosition();
        if (homePreviewEngine != null) homePreviewEngine.pause();
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_ID,   ch.id);
        i.putExtra(PlayerActivity.EXTRA_NAME, ch.name);
        i.putExtra(PlayerActivity.EXTRA_URL,  ch.streamUrl);
        i.putExtra(PlayerActivity.EXTRA_TYPE, ch.contentType);
        i.putExtra(PlayerActivity.EXTRA_GROUP, ch.groupTitle);
        i.putExtra(PlayerActivity.EXTRA_ORDER, ch.sortOrder);
        if (previewPositionMs > 0) i.putExtra(PlayerActivity.EXTRA_START_POSITION, previewPositionMs);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override protected void onResume() {
        super.onResume();
        if (showingHome) showRecentHome(); else observeCurrentTab();
    }

    @Override protected void onPause() {
        if (homePreviewEngine != null) {
            previewPositionMs = homePreviewEngine.getPosition();
            homePreviewEngine.pause();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        previewHandler.removeCallbacksAndMessages(null);
        if (homePreviewView != null) homePreviewView.setPlayer(null);
        if (homePreviewEngine != null) { homePreviewEngine.release(); homePreviewEngine = null; }
        syncExecutor.shutdownNow();
    }
}