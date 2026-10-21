
package com.widge.couchlight.indo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout appRow;
    private TextView clockText, dateText, weatherText, prayerText, titleText, descText, nowPlayingText;
    private ImageView backdrop;
    private MediaSessionManager mediaSessionManager;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionListener;
    private Timer backdropTimer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appRow = findViewById(R.id.app_row);
        clockText = findViewById(R.id.clock);
        dateText = findViewById(R.id.date);
        weatherText = findViewById(R.id.weather);
        prayerText = findViewById(R.id.prayer);
        titleText = findViewById(R.id.title);
        descText = findViewById(R.id.desc);
        nowPlayingText = findViewById(R.id.now_playing);
        backdrop = findViewById(R.id.backdrop);

        clockText.setText("12:30 WIB");
        dateText.setText("Senin, 21 Okt 2026 - Jakarta");
        weatherText.setText("Jakarta 32°C - Live Backdrop ON");
        prayerText.setText("Subuh 04:42 • Dzuhur 11:52 • Ashar 15:08 • Maghrib 18:02 • Isya 19:13");
        
        loadAllTvApps();
        setupLiveBackdrop();
        
        backdropTimer = new Timer();
        backdropTimer.schedule(new TimerTask() {
            @Override public void run() { runOnUiThread(() -> checkNowPlaying()); }
        }, 1000, 2000);
    }

    private void loadAllTvApps() {
        appRow.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent lean = new Intent(Intent.ACTION_MAIN, null); lean.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent normal = new Intent(Intent.ACTION_MAIN, null); normal.addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, ResolveInfo> map = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(lean, 0)) map.put(ri.activityInfo.packageName, ri);
        for (ResolveInfo ri : pm.queryIntentActivities(normal, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.startsWith("com.android.") && !pkg.contains("youtube") && !pkg.contains("vending") && !pkg.contains("tv")) {
                if (!pkg.equals("com.android.tv")) continue;
            }
            if (!map.containsKey(pkg) && !pkg.equals("com.widge.couchlight.indo")) map.put(pkg, ri);
        }
        List<ResolveInfo> sorted = new ArrayList<>(map.values());
        Collections.sort(sorted, (a,b) -> {
            int sa = getPriority(a.activityInfo.packageName), sb = getPriority(b.activityInfo.packageName);
            if (sa!=sb) return sb-sa;
            return pm.getApplicationLabel(a.activityInfo.applicationInfo).toString().compareToIgnoreCase(pm.getApplicationLabel(b.activityInfo.applicationInfo).toString());
        });

        for (ResolveInfo ri : sorted) {
            View tile = getLayoutInflater().inflate(R.layout.item_app, null);
            ImageView iconView = tile.findViewById(R.id.icon);
            TextView labelView = tile.findViewById(R.id.label);
            String appName = pm.getApplicationLabel(ri.activityInfo.applicationInfo).toString();
            Drawable icon = null; try { icon = pm.getApplicationIcon(ri.activityInfo.applicationInfo); } catch(Exception e){}
            labelView.setText(appName); if(icon!=null) iconView.setImageDrawable(icon);
            final String pkg = ri.activityInfo.packageName; final String name = appName; final Drawable appIcon = icon;
            tile.setFocusable(true);
            tile.setOnClickListener(v -> launchApp(pkg));
            tile.setOnFocusChangeListener((v, hasFocus) -> {
                if(hasFocus) {
                    if(nowPlayingText.getVisibility()!=View.VISIBLE) {
                        titleText.setText(name);
                        descText.setText("Buka " + name + " • " + pkg + " • OK buka, Tahan OK info");
                        if(appIcon!=null) { backdrop.setImageDrawable(appIcon); backdrop.setAlpha(0.15f); }
                    }
                }
            });
            tile.setOnLongClickListener(v -> {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:"+pkg)); startActivity(i);
                } catch(Exception e){}
                return true;
            });
            appRow.addView(tile);
        }
    }

    private int getPriority(String pkg) {
        if(pkg.contains("vidio")) return 100;
        if(pkg.contains("smarttube") || pkg.contains("youtube") && !pkg.contains("kids")) return 98;
        if(pkg.contains("kids")) return 97;
        if(pkg.contains("netflix")) return 95;
        if(pkg.contains("loklok") || pkg.contains("movie") || pkg.contains("hub")) return 92;
        if(pkg.contains("vision")) return 90;
        if(pkg.contains("prime")) return 85;
        if(pkg.contains("hotstar") || pkg.contains("disney")) return 80;
        if(pkg.contains("tv") || pkg.contains("live") || pkg.contains("antena")) return 88;
        return 10;
    }

    private void launchApp(String pkg) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if(i!=null) startActivity(i);
        } catch(Exception e){}
    }

    private void setupLiveBackdrop() {
        try {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, MediaListenerService.class);
            sessionListener = controllers -> checkNowPlaying();
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, cn);
            if(!isListenerEnabled()) {
                nowPlayingText.setText("⚠️ Aktifkan Media Listener: Settings > Apps > Special Access > Notification Access > CouchLight Indo ON");
                nowPlayingText.setVisibility(View.VISIBLE);
            }
        } catch(Exception e) {
            nowPlayingText.setText("Live Backdrop: "+e.getMessage());
            nowPlayingText.setVisibility(View.VISIBLE);
        }
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat!=null && flat.contains(getPackageName());
    }

    private void checkNowPlaying() {
        try {
            ComponentName cn = new ComponentName(this, MediaListenerService.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
            if(controllers.isEmpty()) {
                nowPlayingText.setVisibility(View.GONE);
                return;
            }
            for(MediaController ctrl : controllers) {
                if(ctrl.getPlaybackState()!=null && ctrl.getPlaybackState().getState()==3) {
                    MediaMetadata meta = ctrl.getMetadata();
                    if(meta!=null) {
                        String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                        String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                        Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                        if(art==null) art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
                        if(art==null) art = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                        String appPkg = ctrl.getPackageName();
                        String displayTitle = (title!=null?title:"Playing");
                        String displaySub = "";
                        if(artist!=null) displaySub+=artist;
                        displaySub+=" • "+appPkg+" • LIVE BACKDROP";
                        titleText.setText(displayTitle);
                        descText.setText(displaySub);
                        nowPlayingText.setText("▶ NOW PLAYING dari "+appPkg+" - "+displayTitle);
                        nowPlayingText.setVisibility(View.VISIBLE);
                        if(art!=null) {
                            backdrop.setImageDrawable(new BitmapDrawable(getResources(), art));
                            backdrop.setAlpha(0.65f);
                            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        }
                        return;
                    }
                }
            }
        } catch(Exception e) {}
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_MENU) {
            try {
                if(!isListenerEnabled()) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                    Toast.makeText(this, "Aktifkan CouchLight Indo di Notification Access untuk Live Backdrop", Toast.LENGTH_LONG).show();
                } else {
                    loadAllTvApps();
                    Toast.makeText(this, "Refresh "+appRow.getChildCount()+" apps | Live Backdrop ON", Toast.LENGTH_SHORT).show();
                }
            } catch(Exception e){ loadAllTvApps(); }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if(backdropTimer!=null) backdropTimer.cancel();
        try { if(mediaSessionManager!=null && sessionListener!=null) mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener); } catch(Exception e){}
    }
}
