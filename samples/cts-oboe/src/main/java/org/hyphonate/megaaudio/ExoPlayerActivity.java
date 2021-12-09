/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hyphonate.megaaudio;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.util.concurrent.ExecutorService;


/**
 * An activity that plays media using {@link SimpleExoPlayer}.
 */
public class ExoPlayerActivity extends AppCompatActivity
    implements StyledPlayerControlView.VisibilityListener {

  private static final String TAG = "EventLogger";
  protected StyledPlayerView playerView;
  protected LinearLayout debugRootView;
  protected @Nullable SimpleExoPlayer player;

  private DataSource.Factory dataSourceFactory;
  private MediaItem mediaItem;
  private DefaultTrackSelector trackSelector;
  private DefaultTrackSelector.Parameters trackSelectorParameters;
  private boolean startAutoPlay;
  private int startWindow;
  private long startPosition;

  private static final String USER_AGENT =
      "ExoPlayerDemo/"
          + ExoPlayerLibraryInfo.VERSION
          + " (Linux; Android "
          + Build.VERSION.RELEASE
          + ") "
          + ExoPlayerLibraryInfo.VERSION_SLASHY;


  // For async to request drm decryption key
  private ExecutorService mExecutorService;
  private String videoUri;

  // Activity lifecycle.

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.exoplayer_activity);
    debugRootView = findViewById(R.id.controls_root);

    playerView = findViewById(R.id.player_view);
    playerView.setControllerVisibilityListener(this);
    playerView.requestFocus();


//  normal clear stream
    videoUri = "http://hls.cntv.myhwcdn.cn/asp/hls/main//0303000a/3/default/00b621218d9841169bdf1f7dcf287e64/main.m3u8?maxbr=2048";

    clearStartPosition();

  }

  private boolean maybeRequestPermission() {
    Uri uri = Uri.parse(videoUri);
    if (Util.maybeRequestReadExternalStoragePermission(this, uri)) {
      return true;
    }
    return false;
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
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults.length == 0) {
      // Empty results are triggered if a permission is requested while another request was already
      // pending and can be safely ignored in this case.
      return;
    }
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast("Permission to access storage was denied");
      finish();
    }
  }

  // Activity input

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  // PlayerControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  protected void initializePlayer(){
      initializePlayerInternal();
  }

  /**
   * @return Whether initialization was successful.
   */
  protected boolean initializePlayerInternal() {

    mediaItem = MediaItem.fromUri(videoUri);

    if (maybeRequestPermission()) return false;

    if (player == null) {
      dataSourceFactory = getDataSourceFactory(/* context= */ this);

      DefaultTrackSelector.ParametersBuilder builder =
          new DefaultTrackSelector.ParametersBuilder(/* context= */ this);
      trackSelectorParameters = builder.build();

      MediaSourceFactory mediaSourceFactory =
          new DefaultMediaSourceFactory(dataSourceFactory);

      trackSelector = new DefaultTrackSelector(/* context= */ this);
      trackSelector.setParameters(trackSelectorParameters);
      player =
              new SimpleExoPlayer.Builder(/* context= */ this, buildOboeRenderersFactory(this))
                      .setMediaSourceFactory(mediaSourceFactory)
                      .setTrackSelector(trackSelector)
                      .build();
      player.addAnalyticsListener(new EventLogger(trackSelector));
      player.setAudioAttributes(AudioAttributes.DEFAULT, true);
      player.setPlayWhenReady(startAutoPlay);
      playerView.setPlayer(player);
    }
    boolean haveStartPosition = startWindow != C.INDEX_UNSET;
    if (haveStartPosition) {
      player.seekTo(startWindow, startPosition);
    }
    player.setMediaItem(mediaItem, /* resetPosition= */ !haveStartPosition);
    player.prepare();
    return true;
  }

  protected void releasePlayer() {
    if (player != null) {
      player.release();
      player = null;
      mediaItem = null;
      trackSelector = null;
    }
  }

  public static RenderersFactory buildDefaultRenderersFactory(
      Context context) {
    return new DefaultRenderersFactory(context.getApplicationContext());
  }


  public static RenderersFactory buildOboeRenderersFactory(
          Context context) {
    return new OboeAudioRenderersFactory(context.getApplicationContext());
  }

  public DataSource.Factory getDataSourceFactory(Context context) {
    if (dataSourceFactory == null) {
      context = context.getApplicationContext();

      dataSourceFactory = new DataSource.Factory() {
        @Override
        public DataSource createDataSource() {
          return new DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT).createDataSource();
        }
      };
    }
    return dataSourceFactory;
  }


  protected void clearStartPosition() {
    startAutoPlay = true;
    startWindow = C.INDEX_UNSET;
    startPosition = C.TIME_UNSET;
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }


}
