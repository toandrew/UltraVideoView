package com.xiaoyezi.zhibo.zhibovideoview.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.xiaoyezi.zhibo.zhibovideoview.R;

import java.util.Formatter;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by jim on 2017/5/11.
 */
public class ZhiBoMediaController extends FrameLayout {
    private static final String TAG = "ZhiBoMediaController";

    private static final int DEFAULT_TIMEOUT = 3000;

    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SHOW_LOADING = 3;
    private static final int HIDE_LOADING = 4;
    private static final int SHOW_ERROR = 5;
    private static final int HIDE_ERROR = 6;
    private static final int SHOW_COMPLETE = 7;
    private static final int HIDE_COMPLETE = 8;

    @BindView(R.id.title_part)
    View mTitleLayout;

    @BindView(R.id.control_layout)
    View mControlLayout;

    @BindView(R.id.loading_layout)
    ViewGroup mLoadingLayout;

    @BindView(R.id.error_layout)
    ViewGroup mErrorLayout;

    @BindView(R.id.turn_button)
    ImageButton mPlayPauseButton;

    @BindView(R.id.scale_button)
    ImageButton mScaleButton;

    @BindView(R.id.center_play_btn)
    View mCenterPlayButton;

    @BindView(R.id.back_btn)
    View mBackButton;

    @BindView(R.id.seekbar)
    SeekBar mProgressBar;

    @BindView(R.id.has_played)
    TextView mCurrentTime;

    @BindView(R.id.duration)
    TextView mEndTime;

    @BindView(R.id.title)
    TextView mTitle;

    private boolean mShowing = true;

    private boolean mIsFullScreen = false;

    private StringBuilder mFormatBuilder;

    private Formatter mFormatter;

    private Context mContext;

    private MediaPlayerControl mPlayer;

    private boolean mDragging;

    /**
     * Process the controller view's show/hide messages
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FADE_OUT:
                    hide();
                    break;

                case SHOW_PROGRESS:
                    int pos = setProgress();
                    if (!mDragging && mShowing && mPlayer != null && mPlayer.isPlaying()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;

                case SHOW_LOADING:
                    show();

                    showCenterView(R.id.loading_layout);
                    break;

                case SHOW_COMPLETE:
                    showCenterView(R.id.center_play_btn);
                    break;

                case SHOW_ERROR:
                    showCenterView(R.id.error_layout);
                    break;

                case HIDE_LOADING:
                case HIDE_ERROR:
                case HIDE_COMPLETE:
                    hide();
                    hideCenterView();
                    break;
            }
        }
    };

    // Whether the touch event is consumed.
    private boolean mTouchEventHandled = false;
    private OnTouchListener mTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                if (mShowing) {
                    hide();

                    mTouchEventHandled = true;
                }
            }

            return false;
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        private int newPosition = 0;

        private boolean change = false;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromuser) {
            if (mPlayer == null || !fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = mPlayer.getDuration();
            long position = (duration * progress) / 1000L;

            newPosition = (int) position;
            change = true;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mPlayer == null) {
                return;
            }

            show(3600000);

            mDragging = true;

            mHandler.removeMessages(SHOW_PROGRESS);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (mPlayer == null) {
                return;
            }

            if (change) {
                mPlayer.seekTo(newPosition);

                if (mCurrentTime != null) {
                    mCurrentTime.setText(stringForTime(newPosition));
                }
            }

            mDragging = false;

            setProgress();
            updatePlayPauseButton();

            show(DEFAULT_TIMEOUT);

            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            mShowing = true;
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    };

    public ZhiBoMediaController(Context context) {
        this(context, null);
    }

    public ZhiBoMediaController(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZhiBoMediaController(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        mPlayPauseButton.setEnabled(enabled);
        mProgressBar.setEnabled(enabled);
        mScaleButton.setEnabled(enabled);

        mBackButton.setEnabled(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0);
                mTouchEventHandled = false;
                break;

            case MotionEvent.ACTION_UP:
                if (!mTouchEventHandled) {
                    mTouchEventHandled = true;

                    show(DEFAULT_TIMEOUT);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
        }

        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        show(DEFAULT_TIMEOUT);

        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        boolean uniqueDown = event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN;

        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();

                show(DEFAULT_TIMEOUT);

                mPlayPauseButton.requestFocus();
            }

            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();

                updatePlayPauseButton();
                show(DEFAULT_TIMEOUT);
            }

            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();

                updatePlayPauseButton();
                show(DEFAULT_TIMEOUT);
            }

            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_CAMERA
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }

            return true;
        }

        show(DEFAULT_TIMEOUT);

        return super.dispatchKeyEvent(event);
    }

    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;

        updatePlayPauseButton();
    }

    /**
     * Show the controller view. It goes away in 3s
     */
    public void show() {
        show(DEFAULT_TIMEOUT);
    }

    /**
     * Show the controller view in timeout
     *
     * @param timeout
     */
    public void show(int timeout) {
        if (!mShowing) {
            setProgress();

            mPlayPauseButton.requestFocus();

            disableUnsupportedButtons();

            mShowing = true;
        }

        updatePlayPauseButton();
        updateBackButton();

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }

        if (mTitleLayout.getVisibility() != VISIBLE) {
            mTitleLayout.setVisibility(VISIBLE);
        }

        if (mControlLayout.getVisibility() != VISIBLE) {
            mControlLayout.setVisibility(VISIBLE);
        }

        // cause the progress bar to be updated even if mShowing
        // was already true. This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mHandler.sendEmptyMessage(SHOW_PROGRESS);

        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
    }

    /**
     * Whether this controller is showing
     *
     * @return
     */
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Hide media controller's views
     */
    public void hide() {
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);

            mTitleLayout.setVisibility(GONE);
            mControlLayout.setVisibility(GONE);

            mShowing = false;
        }
    }

    /**
     * Reset media controller's status
     */
    public void reset() {
        mCurrentTime.setText("00:00");
        mEndTime.setText("00:00");

        mProgressBar.setProgress(0);
        mPlayPauseButton.setImageResource(R.drawable.zhibo_player_player_btn);
        setVisibility(VISIBLE);
    }

    /**
     * Show loading view
     */
    public void showLoading() {
        mHandler.sendEmptyMessage(SHOW_LOADING);
    }

    /**
     * Hide loading view
     */
    public void hideLoading() {
        mHandler.sendEmptyMessage(HIDE_LOADING);
    }

    /**
     * Show error view
     */
    public void showError() {
        mHandler.sendEmptyMessage(SHOW_ERROR);
    }

    /**
     * Hide error view
     */
    public void hideError() {
        mHandler.sendEmptyMessage(HIDE_ERROR);
    }

    /**
     * Show complete view
     */
    public void showComplete() {
        mHandler.sendEmptyMessage(SHOW_COMPLETE);
    }

    /**
     * Hide complete view
     */
    public void hideComplete() {
        mHandler.sendEmptyMessage(HIDE_COMPLETE);
    }

    /**
     * Set video title
     *
     * @param title
     */
    public void setTitle(String title) {
        mTitle.setText(title);
    }

    /**
     * Set error view
     *
     * @param resId
     */
    public void setOnErrorView(int resId) {
        mErrorLayout.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(resId, mErrorLayout, true);
    }

    /**
     * Set error view
     *
     * @param onErrorView
     */
    public void setOnErrorView(View onErrorView) {
        mErrorLayout.removeAllViews();
        mErrorLayout.addView(onErrorView);
    }

    /**
     * Set loading view
     *
     * @param resId
     */
    public void setOnLoadingView(int resId) {
        mLoadingLayout.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(resId, mLoadingLayout, true);
    }

    /**
     * Set loading view
     *
     * @param onLoadingView
     */
    public void setOnLoadingView(View onLoadingView) {
        mLoadingLayout.removeAllViews();

        mLoadingLayout.addView(onLoadingView);
    }

    /**
     * Toggle full screen
     *
     * @param isFullScreen
     */
    public void toggleFullButton(boolean isFullScreen) {
        mIsFullScreen = isFullScreen;

        updateScaleButton();
        updateBackButton();
    }

    @OnClick(R.id.turn_button)
    public void onPlayPauseClicked(View view) {
        if (mPlayer != null) {
            doPauseResume();

            show(DEFAULT_TIMEOUT);
        }
    }

    @OnClick(R.id.scale_button)
    public void onScaleClicked(View view) {
        mIsFullScreen = !mIsFullScreen;

        updateScaleButton();
        updateBackButton();

        mPlayer.setFullScreen(mIsFullScreen);
    }

    @OnClick(R.id.back_btn)
    public void onBackClicked(View view) {
        if (mIsFullScreen) {
            mIsFullScreen = false;

            updateScaleButton();
            updateBackButton();

            mPlayer.setFullScreen(false);
        }
    }

    @OnClick(R.id.center_play_btn)
    public void onCenterPlayClicked(View view) {
        hideCenterView();

        mPlayer.start();
    }

    /**
     * Init all views
     *
     * @param context
     * @param attrs
     */
    private void init(Context context, AttributeSet attrs) {
        mContext = context;

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View viewRoot = inflater.inflate(R.layout.zhibo_player_controller, this);
        viewRoot.setOnTouchListener(mTouchListener);

        ButterKnife.bind(viewRoot);

        initControllerView(viewRoot);
    }

    /**
     * Init controller view
     *
     * @param v
     */
    private void initControllerView(View v) {
        mPlayPauseButton.requestFocus();

        mProgressBar.setOnSeekBarChangeListener(mSeekListener);
        mProgressBar.setMax(1000);

        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    }

    /**
     * Update scale button status
     */
    private void updateScaleButton() {
        if (mIsFullScreen) {
            mScaleButton.setImageResource(R.drawable.zhibo_star_zoom_in);
        } else {
            mScaleButton.setImageResource(R.drawable.zhibo_player_scale_btn);
        }
    }

    /**
     * Update back button status
     */
    private void updateBackButton() {
        mBackButton.setVisibility(mIsFullScreen ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Update play/pause button
     */
    private void updatePlayPauseButton() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayPauseButton.setImageResource(R.drawable.zhibo_stop_btn);
        } else {
            mPlayPauseButton.setImageResource(R.drawable.zhibo_player_player_btn);
        }
    }

    /**
     * Set and get progressbar's position
     */
    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }

        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mProgressBar != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                mProgressBar.setProgress((int) pos);
            }

            int percent = mPlayer.getBufferPercentage();
            mProgressBar.setSecondaryProgress(percent * 10);
        }

        mEndTime.setText(stringForTime(duration));
        mCurrentTime.setText(stringForTime(position));

        return position;
    }

    /**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaPlayerControlExt
     */
    private void disableUnsupportedButtons() {
        try {
            if (mPlayPauseButton != null && mPlayer != null && !mPlayer.canPause()) {
                mPlayPauseButton.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError e) {

        }
    }

    /**
     * Show center view
     *
     * @param resId
     */
    private void showCenterView(int resId) {
        switch (resId) {
            case R.id.loading_layout:
                mLoadingLayout.setVisibility(VISIBLE);
                mCenterPlayButton.setVisibility(GONE);
                mErrorLayout.setVisibility(GONE);
                break;

            case R.id.center_play_btn:
                mCenterPlayButton.setVisibility(VISIBLE);
                mLoadingLayout.setVisibility(GONE);
                mErrorLayout.setVisibility(GONE);
                break;

            case R.id.error_layout:
                mCenterPlayButton.setVisibility(GONE);
                mLoadingLayout.setVisibility(GONE);
                mErrorLayout.setVisibility(VISIBLE);
                break;
        }
    }

    /**
     * Hide center view
     */
    private void hideCenterView() {
        mLoadingLayout.setVisibility(GONE);
        mCenterPlayButton.setVisibility(GONE);
        mErrorLayout.setVisibility(GONE);
    }

    /**
     * Get time string
     *
     * @param timeMs
     * @return
     */
    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        }

        return mFormatter.format("%02d:%02d", minutes, seconds).toString();
    }

    /**
     * Pause or Start
     */
    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }

        updatePlayPauseButton();
    }

    /**
     * Media Player' control interface
     */
    public interface MediaPlayerControl {
        void start();

        void pause();

        int getDuration();

        int getCurrentPosition();

        void seekTo(int pos);

        boolean isPlaying();

        int getBufferPercentage();

        boolean canPause();

        boolean canSeekBackward();

        boolean canSeekForward();

        void closePlayer();

        void setFullScreen(boolean fullscreen);

        void setFullScreen(boolean fullscreen, int screenOrientation);
    }
}
