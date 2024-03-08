package com.example.myapplication;

import static androidx.media3.common.C.CONTENT_TYPE_HLS;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaAdsLoader;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.exoplayer.source.ads.AdsMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.multidex.MultiDex;
import com.google.ads.interactivemedia.v3.api.AdEvent;

/** Main Activity. */
@OptIn(markerClass = UnstableApi.class)
public class MyActivity extends Activity {

    private static final String SAMPLE_VAST_TAG_URL =
            "https://pubads.g.doubleclick.net/gampad/ads?iu=/21775744923/external/"
                    + "single_ad_samples&sz=640x480&cust_params=sample_ct%3Dlinear&ciu_szs=300x250%2C728x90"
                    + "&gdfp_req=1&output=vast&unviewed_position_start=1&env=vp&impl=s&correlator=";
    private static final String KEY_ADS_LOADER_STATE = "ads_loader_state";
    private static final String SAMPLE_ASSET_KEY = "c-rArva4ShKVIAkNfy6HUQ";
    private static final String LOG_TAG = "ImaExoPlayerExample";

    private PlayerView playerView;
    private TextView logText;
    private ExoPlayer player;

    private ImaAdsLoader imaAdsLoader;
    private ImaServerSideAdInsertionMediaSource.AdsLoader adsLoader;
    private ImaServerSideAdInsertionMediaSource.AdsLoader.State adsLoaderState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        MultiDex.install(this);

        playerView = findViewById(R.id.player_view);

        imaAdsLoader = new ImaAdsLoader.Builder(this).build();

        // Checks if there is a saved AdsLoader state to be used later when initiating the AdsLoader.
        if (savedInstanceState != null) {
            Bundle adsLoaderStateBundle = savedInstanceState.getBundle(KEY_ADS_LOADER_STATE);
            if (adsLoaderStateBundle != null) {
                adsLoaderState =
                        ImaServerSideAdInsertionMediaSource.AdsLoader.State.CREATOR.fromBundle(
                                adsLoaderStateBundle);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Attempts to save the AdsLoader state to handle app backgrounding.
        if (adsLoaderState != null) {
            outState.putBundle(KEY_ADS_LOADER_STATE, adsLoaderState.toBundle());
        }
    }

    private void releasePlayer() {
        // Set the player references to null and release the player's resources.
        playerView.setPlayer(null);
        player.release();
        player = null;

        // Release the adsLoader state so that it can be initiated again.
        adsLoaderState = adsLoader.release();
    }

    // Create a server side ad insertion (SSAI) AdsLoader.
    private ImaServerSideAdInsertionMediaSource.AdsLoader createAdsLoader() {
        ImaServerSideAdInsertionMediaSource.AdsLoader.Builder adsLoaderBuilder =
                new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(this, playerView);

        // Attempts to set the AdsLoader state if available from a previous session.
        if (adsLoaderState != null) {
            adsLoaderBuilder.setAdsLoaderState(adsLoaderState);
        }

        return adsLoaderBuilder.setAdEventListener(buildAdEventListener()).build();
    }

    public AdEvent.AdEventListener buildAdEventListener() {
        logText = findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());

        AdEvent.AdEventListener imaAdEventListener =
                event -> {
                    AdEvent.AdEventType eventType = event.getType();
                    if (eventType == AdEvent.AdEventType.AD_PROGRESS) {
                        return;
                    }
                    String log = "IMA event: " + eventType;
                    if (logText != null) {
                        logText.append(log + "\n");
                    }
                    Log.i(LOG_TAG, log);
                };

        return imaAdEventListener;
    }

    private void initializePlayer() {
        adsLoader = createAdsLoader();

        // Set up the factory for media sources, passing the ads loader.
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

        // MediaSource.Factory to create the ad sources for the current player.
        ImaServerSideAdInsertionMediaSource.Factory adsMediaSourceFactory =
                new ImaServerSideAdInsertionMediaSource.Factory(adsLoader, mediaSourceFactory);

        // 'mediaSourceFactory' is an ExoPlayer component for the DefaultMediaSourceFactory.
        // 'adsMediaSourceFactory' is an ExoPlayer component for a MediaSource factory for IMA server
        // side inserted ad streams.
        mediaSourceFactory.setServerSideAdInsertionMediaSourceFactory(adsMediaSourceFactory);
        // Create a SimpleExoPlayer and set it as the player for content and ads.
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        playerView.setPlayer(player);
        adsLoader.setPlayer(player);
        imaAdsLoader.setPlayer(player);

        // Build an IMA SSAI media item to prepare the player with.
        Uri ssaiLiveUri =
                new ImaServerSideAdInsertionUriBuilder()
                        .setAssetKey(SAMPLE_ASSET_KEY)
                        .setFormat(CONTENT_TYPE_HLS)
                        // Use CONTENT_TYPE_DASH for dash streams.`1
                        .build();

        // Create the MediaItem to play, specifying the stream URI.
        MediaItem ssaiMediaItem = MediaItem.fromUri(ssaiLiveUri);

        // Prepare the content and ad to be played with the ExoPlayer.
        Uri adTagUri = Uri.parse(SAMPLE_VAST_TAG_URL);
        player.addMediaSource(0,
                new AdsMediaSource(
                        new SilenceMediaSource(/* durationUs= */ 0),
                        new DataSpec(adTagUri),
                        /* adsId= */ "preroll",
                        new DefaultMediaSourceFactory(this),
                        imaAdsLoader,
                        playerView));
        player.addMediaItem(1,ssaiMediaItem);
        player.prepare();

        // Set PlayWhenReady. If true, content and ads will autoplay.
        player.setPlayWhenReady(true);
    }
}