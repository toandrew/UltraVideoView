package com.xiaoyezi.zhibo.zhibovideoview.widget;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.WindowManager;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Created by jim on 2017/5/11.
 */
public class ZhiBoVideoView extends TextureView implements ZhiBoMediaController.MediaPlayerControl {
    private static final String TAG = "ZhiBoVideoView";

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // mCurrentState is a TextureVideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the TextureVideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private Uri mVideoUri;

    // recording the seek position while preparing
    private int mSeekWhenPrepared;

    private ZhiBoMediaController mMediaController;

    private Surface mSurface;

    // This will be replaced by interface for different live media player
    private MediaPlayer mMediaPlayer = null;

    private boolean mShouldRequestAudioFocus = true;

    private int mAudioSession;

    private int mCurrentBufferPercentage = 0;

    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnInfoListener mOnInfoListener;

    private boolean mCanPause;

    private boolean mCanSeekBack;

    private boolean mCanSeekForward;

    private boolean mPreparedBeforeStart;

    private Context mContext;

    private int mVideoViewLayoutWidth = 0;
    private int mVideoViewLayoutHeight = 0;

    private VideoViewCallback mVideoViewCallback;

    /**
     * Callback for video view.
     */
    public interface VideoViewCallback {
        void onScaleChanged(boolean isFullScreen);

        void onPause(final MediaPlayer mediaPlayer);

        void onStart(final MediaPlayer mediaPlayer);

        void onBufferingStart(final MediaPlayer mediaPlayer);

        void onBufferingEnd(final MediaPlayer mediaplayer);
    }

    public ZhiBoVideoView(Context context) {
        this(context, null);
    }

    public ZhiBoVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZhiBoVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.init(context, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        fixupOnMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
    }

    /**
     * Set video controller
     *
     * @param controller
     */
    public void setMediaController(ZhiBoMediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }

        mMediaController = controller;

        attachMediaController();
    }

    /**
     * Set video path
     *
     * @param path
     */
    public void setVideoPath(String path) {
        setVideoUri(Uri.parse(path));
    }

    /**
     * Set video's uri
     *
     * @param uri
     */
    public void setVideoUri(Uri uri) {
        mVideoUri = uri;

        mSeekWhenPrepared = 0;

        openVideo();

        requestLayout();
        invalidate();
    }

    /**
     * Stop video
     */
    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;

            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;

            if (mShouldRequestAudioFocus) {
                AudioManager am = (AudioManager) getContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                am.abandonAudioFocus(null);
            }
        }

        clearSurface();
    }

    /**
     * Callback when media prepared
     *
     * @param l
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Callback when media completed
     *
     * @param l
     */
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Callback when media error
     *
     * @param l
     */
    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Callback when query media info
     *
     * @param l
     */
    public void setOnInfoListener(MediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    /**
     * Register callback for video view
     *
     * @param callback
     */
    public void setVideoViewCallback(VideoViewCallback callback) {
        mVideoViewCallback = callback;
    }

    @Override
    public void start() {
        if (!mPreparedBeforeStart && mMediaController != null) {
            mMediaController.showLoading();
        }

        if (isInPlaybackState()) {
            mMediaPlayer.start();

            mCurrentState = STATE_PLAYING;

            if (mVideoViewCallback != null) {
                mVideoViewCallback.onStart(mMediaPlayer);
            }
        }

        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();

                mCurrentState = STATE_PAUSED;

                if (mVideoViewCallback != null) {
                    mVideoViewCallback.onPause(mMediaPlayer);
                }
            }
        }

        mTargetState = STATE_PAUSED;
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }

        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState() && mMediaPlayer != null) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }

        return 0;
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public void closePlayer() {
        release(true);
    }

    @Override
    public void setFullScreen(boolean fullscreen) {
        int screenOrientation = fullscreen ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        setFullScreen(fullscreen, screenOrientation);
    }

    @Override
    public void setFullScreen(boolean fullscreen, int screenOrientation) {
        Activity activity = (Activity) mContext;

        if (fullscreen) {
            if (mVideoViewLayoutWidth == 0 && mVideoViewLayoutHeight == 0) {
                ViewGroup.LayoutParams params = getLayoutParams();
                mVideoViewLayoutWidth = params.width;
                mVideoViewLayoutHeight = params.height;
            }
        } else {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = mVideoViewLayoutWidth;
            params.height = mVideoViewLayoutHeight;
        }

        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.setRequestedOrientation(screenOrientation);

        mMediaController.toggleFullButton(fullscreen);

        if (mVideoViewCallback != null) {
            mVideoViewCallback.onScaleChanged(fullscreen);
        }
    }

    TextureView.SurfaceTextureListener mSurfaceTextureListener = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            mSurface = new Surface(surfaceTexture);

            openVideo();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (width > 0 && height > 0);

            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }

                start();
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            if (mSurface != null) {
                mSurface.release();
                mSurface = null;
            }

            if (mMediaController != null) {
                mMediaController.hide();
            }

            release(true);

            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            // TODO
        }
    };

    /**
     * Media player's listeners
     */
    private MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {

        @Override
        public void onPrepared(MediaPlayer mediaPlayer) {
            mCurrentState = STATE_PREPARED;

            mCanPause = mCanSeekBack = mCanSeekForward = true;

            mPreparedBeforeStart = true;
            if (mMediaController != null) {
                mMediaController.hideLoading();
            }

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mediaPlayer);
            }

            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }

            mVideoWidth = mediaPlayer.getVideoWidth();
            mVideoHeight = mediaPlayer.getVideoHeight();

            int seekToPosition = mSeekWhenPrepared;
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }

            if (mVideoWidth != 0 && mVideoHeight != 0) {
                getSurfaceTexture().setDefaultBufferSize(mVideoWidth, mVideoHeight);

                // We won't get a "surface changed" callback if the surface is already the right size, so
                // start the video here instead of in the callback.
                if (mTargetState == STATE_PLAYING) {
                    start();

                    if (mMediaController != null) {
                        mMediaController.show();
                    }
                } else if (!isPlaying() && (seekToPosition != 0 || getCurrentPosition() > 0)) {
                    if (mMediaController != null) {
                        // Show the media controls when we're paused into a video and make 'em stick.
                        mMediaController.show(0);
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };
    private MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
            mVideoWidth = mediaPlayer.getVideoWidth();
            mVideoHeight = mediaPlayer.getVideoHeight();

            if (mVideoWidth != 0 && mVideoHeight != 0) {
                getSurfaceTexture().setDefaultBufferSize(mVideoWidth, mVideoHeight);
                requestLayout();
            }
        }
    };
    private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            mCurrentState = STATE_PLAYBACK_COMPLETED;
            mTargetState = STATE_PLAYBACK_COMPLETED;

            if (mMediaController != null) {
                mMediaController.showComplete();
            }

            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mediaPlayer);
            }
        }
    };
    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mediaPlayer, int frameworkErr, int implErr) {
            Log.e(TAG, "Error: " + frameworkErr + ", " + implErr);

            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;

            if (mMediaController != null) {
                mMediaController.showError();
            }

            /* If an error handler has been supplied, use it and finish. */
            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mediaPlayer, frameworkErr, implErr)) {
                    return true;
                }
            }

            // TODO

            return true;
        }
    };
    private MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
            boolean handled = false;
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.d(TAG, "onInfo MediaPlayer.MEDIA_INFO_BUFFERING_START");

                    if (mVideoViewCallback != null) {
                        mVideoViewCallback.onBufferingStart(mediaPlayer);
                    }

                    if (mMediaController != null) {
                        mMediaController.showLoading();
                    }

                    handled = true;
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.d(TAG, "onInfo MediaPlayer.MEDIA_INFO_BUFFERING_END");

                    if (mVideoViewCallback != null) {
                        mVideoViewCallback.onBufferingEnd(mediaPlayer);
                    }

                    if (mMediaController != null) {
                        mMediaController.hideLoading();
                    }

                    handled = true;
                    break;
            }

            if (mOnInfoListener != null) {
                return mOnInfoListener.onInfo(mediaPlayer, what, extra) || handled;
            }

            return handled;
        }
    };
    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(MediaPlayer mediaPlayer, int percent) {
            mCurrentBufferPercentage = percent;
        }
    };

    /**
     * Init view
     *
     * @param context
     * @param attrs
     */
    private void init(Context context, AttributeSet attrs) {
        mContext = context;

        setSurfaceTextureListener(mSurfaceTextureListener);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    /**
     * Change video's width and height
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    private void fixupOnMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);

        if (mVideoWidth > 0 && mVideoHeight > 0) {
            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            // The size is fixed?
            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = heightSpecSize;

                // Adjust size
                if (mVideoWidth * height < width * mVideoHeight) {
                    // Too wide
                    width = height * mVideoWidth / mVideoHeight;
                } else if (mVideoWidth * height > width * mVideoHeight) {
                    // Too tall
                    height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height
                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;

                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;

                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide
                    width = widthSpecSize;
                }
            } else {
                // neither the width or height is fixed
                width = mVideoWidth;
                height = mVideoHeight;

                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }

                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        }

        setMeasuredDimension(width, height);
    }

    /**
     * play video
     */
    private void openVideo() {
        // not ready to play
        if (mVideoUri == null || mSurface == null) {
            Log.w(TAG, "Sorry not ready to play!mVideoUri[" + mVideoUri + "]mSurface[" + mSurface + "]");
            return;
        }

        release(false);

        if (mShouldRequestAudioFocus) {
            AudioManager am = (AudioManager) getContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }

        try {
            mMediaPlayer = new MediaPlayer();

            if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }

            // init status's listeners
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);

            mCurrentBufferPercentage = 0;

            mMediaPlayer.setDataSource(getContext().getApplicationContext(), mVideoUri);
            mMediaPlayer.setSurface(mSurface);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);

            // ready to play
            mMediaPlayer.prepareAsync();

            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (Exception e) {
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;

            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    /**
     * Release the media player
     *
     * @param clearTargetState
     */
    private void release(boolean clearTargetState) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;

            mCurrentState = STATE_IDLE;

            if (clearTargetState) {
                mTargetState = STATE_IDLE;
            }

            if (mShouldRequestAudioFocus) {
                AudioManager am = (AudioManager) getContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                am.abandonAudioFocus(null);
            }
        }
    }

    /**
     * Clears the surface texture by attaching a GL context and clearing it.
     * Code taken from <a href="http://stackoverflow.com/a/31582209">Hugo Gresse's answer on stackoverflow.com</a>.
     */
    private void clearSurface() {
        if (mSurface == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        egl.eglInitialize(display, null);

        int[] attribList = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL10.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        egl.eglChooseConfig(display, attribList, configs, configs.length, numConfigs);
        EGLConfig config = configs[0];
        EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[]{
                12440, 2, EGL10.EGL_NONE
        });
        EGLSurface eglSurface = egl.eglCreateWindowSurface(display, config, mSurface, new int[]{
                EGL10.EGL_NONE
        });

        egl.eglMakeCurrent(display, eglSurface, eglSurface, context);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        egl.eglSwapBuffers(display, eglSurface);
        egl.eglDestroySurface(display, eglSurface);
        egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        egl.eglDestroyContext(display, context);
        egl.eglTerminate(display);
    }

    /**
     * Attach media controller in parent view
     */
    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);

            // TODO. Should we dynamically add this view into its parent view?

            mMediaController.setEnabled(isInPlaybackState());
            mMediaController.hide();
        }
    }

    /**
     * Whether it's in playing state
     *
     * @return
     */
    private boolean isInPlaybackState() {
        return (mMediaPlayer != null
                && mCurrentState != STATE_ERROR
                && mCurrentState != STATE_IDLE
                && mCurrentState != STATE_PREPARING);
    }
}
