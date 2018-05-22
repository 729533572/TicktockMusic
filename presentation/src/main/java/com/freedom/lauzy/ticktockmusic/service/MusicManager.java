package com.freedom.lauzy.ticktockmusic.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.freedom.lauzy.ticktockmusic.TicktockApplication;
import com.freedom.lauzy.ticktockmusic.event.ClearQueueEvent;
import com.freedom.lauzy.ticktockmusic.function.DefaultDisposableObserver;
import com.freedom.lauzy.ticktockmusic.function.RxBus;
import com.freedom.lauzy.ticktockmusic.function.RxHelper;
import com.freedom.lauzy.ticktockmusic.model.SongEntity;
import com.lauzy.freedom.librarys.common.LogUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Desc : Manager
 * Author : Lauzy
 * Date : 2017/8/22
 * Blog : http://www.jianshu.com/u/e76853f863a9
 * Email : freedompaladin@gmail.com
 */
@SuppressWarnings("checkresult")
public class MusicManager {

    private static final String TAG = "MusicManager";
    private MusicService mMusicService;
    private MediaController mMediaController;
    private final Handler mProgressHandler = new Handler();
    private String[] mCurIds;

    private QueueManager mQueueManager;

    private MusicManager() {
        mQueueManager = new QueueManager();
    }

    private static class SingleTon {
        private static final MusicManager INSTANCE = new MusicManager();
    }

    public static MusicManager getInstance() {
        return SingleTon.INSTANCE;
    }

    private MusicService.MediaPlayerUpdateListener mUpdateListener = new MusicService.MediaPlayerUpdateListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            skipToNext();
        }

        @Override
        public void onBufferingUpdate(MediaPlayer mediaPlayer, int percent) {
            if (mMusicManageListener != null) {
                mMusicManageListener.onBufferingUpdate(mediaPlayer, percent);
            }
        }

        @Override
        public void onProgress(int progress, int duration) {
            if (mMusicManageListener != null) {
                mMusicManageListener.onProgress(progress, duration);
            }
        }

        @Override
        public void currentPlay(SongEntity songEntity) {
            if (mMusicManageListener != null) {
                mMusicManageListener.currentPlay(songEntity);
            }
            if (mPlayProgressListener != null) {
                mPlayProgressListener.currentPlay(songEntity);
            }
        }

        @Override
        public void startPlay() {
            if (mRecentUpdateListener != null) {
                mRecentUpdateListener.startPlay();
            }
            if (mPlayQueueListeners != null && mPlayQueueListeners.size() != 0) {
                for (PlayQueueListener playQueueListener : mPlayQueueListeners) {
                    playQueueListener.playSuccess();
                }
            }
        }

        @Override
        public void currentPauseSong(SongEntity songEntity) {
            if (mMusicManageListener != null) {
                mMusicManageListener.currentPauseSong(songEntity);
            }
        }
    };

    // MediaController 的回调接口，可根据状态处理逻辑
    // （本应用由于在 Service 和 UI 中添加了管理类，所以主要用于开发中 Log 打印及 UI 的接口回调）
    private MediaController.Callback mMediaControllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            super.onPlaybackStateChanged(state);
            switch (state.getState()) {
                case PlaybackState.STATE_NONE:
                    break;
                case PlaybackState.STATE_PLAYING:
                    if (mMusicManageListener != null) {
                        mMusicManageListener.onPlayerResume();
                    }
                    if (mPlayProgressListener != null) {
                        mPlayProgressListener.onPlayerResume();
                    }
                    break;
                case PlaybackState.STATE_PAUSED:
                    LogUtil.i(TAG, "STATE_PAUSED");
                    if (mMusicManageListener != null) {
                        mMusicManageListener.onPlayerPause();
                    }
                    if (mPlayProgressListener != null) {
                        mPlayProgressListener.onPlayerPause();
                    }
                    break;
                case PlaybackState.STATE_BUFFERING:
                    break;
                case PlaybackState.STATE_CONNECTING:
                    break;
                case PlaybackState.STATE_ERROR:
                    break;
                case PlaybackState.STATE_FAST_FORWARDING:
                    break;
                case PlaybackState.STATE_REWINDING:
                    break;
                case PlaybackState.STATE_SKIPPING_TO_NEXT:
                    LogUtil.i(TAG, "SKIP_TO_NEXT");
                    break;
                case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                    LogUtil.i(TAG, "SKIP_TO_PREVIOUS");
                    break;
                case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                    break;
                case PlaybackState.STATE_STOPPED:
                    LogUtil.i(TAG, "STATE_STOP");
                    break;
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMusicService = ((MusicService.ServiceBinder) service).getService();
            //实例化 MediaController
            mMediaController = new MediaController(TicktockApplication.getInstance(),
                    mMusicService.getMediaSessionToken());
            //设置 Service 接口，便于管理 Progress、onCompletion 等
            mMusicService.setUpdateListener(mUpdateListener);
            //注册 MediaController 回调接口
            mMediaController.registerCallback(mMediaControllerCallback);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMusicService.setUpdateListener(null);
            mMusicService = null;
            /*bindPlayService();
            startService();*/
        }
    };

    public void bindPlayService() {
        LogUtil.i(TAG, "bindService");
        TicktockApplication context = TicktockApplication.getInstance();
        context.bindService(new Intent(context, MusicService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindService() {
        if (null != mConnection) {
            TicktockApplication context = TicktockApplication.getInstance();
            context.unbindService(mConnection);
        }
    }

    public void startService() {
        LogUtil.i(TAG, "startService");
        TicktockApplication context = TicktockApplication.getInstance();
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(MusicService.ACTION_START);
        context.startService(intent);
    }

    private void stopService() {
        TicktockApplication context = TicktockApplication.getInstance();
        context.stopService(new Intent(context, MusicService.class));
    }

    public void playMusic(List<SongEntity> songEntities, String[] ids) {
        this.playMusic(songEntities, ids, 0);
    }

    /**
     * 播放当前列表音乐，并将当前列表添加至播放队列
     *
     * @param songEntities 列表
     * @param ids          id
     * @param position     当前位置
     */
    public void playMusic(List<SongEntity> songEntities, String[] ids, int position) {
        mCurIds = ids;//id赋值给当前ID，以供队列列表使用
        mQueueManager.playQueueObservable(ids).subscribe(playQueue -> {
            if (songEntities.equals(playQueue)) {
                mMusicService.setSongData(playQueue);
                LogUtil.d(TAG, "--- data exists ---");
                open(position, playQueue.get(position), DATA_EXITS);
            } else {
                mQueueManager.addGetQueueObservable(ids, songEntities)
                        .compose(RxHelper.ioMain())
                        .subscribeWith(new DefaultDisposableObserver<List<SongEntity>>() {
                            @Override
                            public void onNext(@io.reactivex.annotations.NonNull List<SongEntity> songData) {
                                super.onNext(songData);
                                LogUtil.d(TAG, "--- new data ---");
                                mMusicService.setSongData(songData);
                                open(position, songData.get(position), NEW_DATA);
                            }
                        });
            }
        });
    }

    /**
     * 打开指定列表位置的音乐
     *
     * @param position 当前位置
     */
    private void open(int position, SongEntity entity, @QueueMode int mode) {
        if (mMusicService == null) {
            return;
        }
        switch (mode) {
            case NEW_DATA:
                mMusicService.setCurrentPosition(position);
                play();
                break;
            case DATA_EXITS:
                if (position == 0
                        || position != mMusicService.getCurrentPosition()
                        || !entity.equals(mMusicService.getCurrentSong())
                        || getMusicState() == PlaybackState.STATE_STOPPED) {
                    if (!entity.equals(mMusicService.getCurrentSong())) {
                        mMusicService.setCurrentPosition(position);
                        play();
                    } else {
                        resume();
                    }
                } else if (getMusicState() == PlaybackState.STATE_PAUSED) {
                    //当前为同一首歌曲，并且为暂停状态时继续播放
                    resume();
                }
                break;
        }
    }

    /**
     * 设置播放队列数据
     *
     * @param ids      ids
     * @param position 列表位置
     */
    public void setMusicServiceData(String[] ids, int position) {
        mQueueManager.playQueueObservable(ids)
                .subscribe(songData -> {
                    mMusicService.setSongData(songData);
                    updatePosition(position);
                });
    }

    /**
     * 播放队列中特定歌曲时更新位置信息
     *
     * @param position 列表位置
     */
    private void updatePosition(int position) {
        if (getCurPosition() > position) {
            mMusicService.setCurrentPosition(getCurPosition() - 1);
        } else if (getCurPosition() == position) {
            if (position == mMusicService.getSongData().size()) {
                mMusicService.setCurrentPosition(position - 1);
            } else {
                mMusicService.setCurrentPosition(position);
            }
            play();
        }
        if (mPlayProgressListener != null) {
            mPlayProgressListener.updateQueue(position);
        }
    }

    public void setCurPlayPosition(int position) {
        getMusicService().setCurrentPosition(position);
        play();
    }


    /**
     * 清空播放队列
     */
    public void clearPlayData() {
        mMusicService.setSongData(Collections.emptyList());
        mProgressHandler.removeCallbacks(mProgressRunnable);
//        mMusicService.stopPlayer();
        mMediaController.getTransportControls().stop();
        RxBus.INSTANCE.post(new ClearQueueEvent());
    }

    public void quit() {
        mProgressHandler.removeCallbacks(mProgressRunnable);
        mMusicService.quit();
        unbindService();
        stopService();
    }

    /**
     * 播放音乐
     */
    private void play() {
        mMusicService.setQueueManager(mQueueManager);
        mMediaController.getTransportControls().play();
        mProgressHandler.post(mProgressRunnable);
    }

    /**
     * 开始播放（多用于暂停后开始）
     */
    public void start() {
        if (mMusicService != null) {
            mMusicService.start();
            mProgressHandler.post(mProgressRunnable);
        }
    }

    /**
     * 暂停播放
     */
    public void pause() {
        mMediaController.getTransportControls().pause();
        mProgressHandler.removeCallbacks(mProgressRunnable);
    }

    public void pauseProgress() {
        mProgressHandler.removeCallbacks(mProgressRunnable);
    }

    public void resumeProgress() {
        mProgressHandler.post(mProgressRunnable);
    }

    /**
     * 继续播放
     */
    private void resume() {
        if (mMusicManageListener != null) {
            mMusicManageListener.onPlayerResume();
            mMusicService.start();
            mProgressHandler.post(mProgressRunnable);
        }
    }

    /**
     * 播放下一首，进度条重置
     */
    public void skipToNext() {
        if (mMediaController != null) {
            mMediaController.getTransportControls().skipToNext();
            mProgressHandler.post(mProgressRunnable);
        }
    }

    /**
     * 播放上一首，进度条重置
     */
    public void skipToPrevious() {
        if (mMediaController != null) {
            mMediaController.getTransportControls().skipToPrevious();
            mProgressHandler.post(mProgressRunnable);
        }
    }

    public void seekTo(long position) {
        if (mMediaController != null) {
            mMediaController.getTransportControls().seekTo(position);
        }
    }

    /**
     * 获取当前音乐的播放状态
     *
     * @return 播放状态
     */
    private int getMusicState() {
        return mMusicService.getPlaybackState().getState();
    }

    private Runnable mProgressRunnable = new Runnable() {
        @Override
        public void run() {
            boolean isUpdate = getMusicState() == PlaybackState.STATE_PLAYING ||
                    getMusicState() == PlaybackState.STATE_SKIPPING_TO_NEXT ||
                    getMusicState() == PlaybackState.STATE_SKIPPING_TO_PREVIOUS;
            if (isUpdate && mUpdateListener != null && getCurrentSong() != null) {
                mUpdateListener.onProgress((int) mMusicService.getCurrentProgress(),
                        mMusicService.getDuration());
            }
            if (isUpdate && mPlayProgressListener != null && getCurrentSong() != null) {
                mPlayProgressListener.onProgress((int) mMusicService.getCurrentProgress(),
                        mMusicService.getDuration());
            }
            mProgressHandler.postDelayed(this, 100);
        }
    };

    /* ------- getter , setter and so on-------- */

    public MusicService getMusicService() {
        return mMusicService;
    }

    public long getCurrentProgress() {
        return mMusicService != null ? mMusicService.getCurrentProgress() : 0;
    }

    public int getDuration() {
        return mMusicService != null ? mMusicService.getMediaPlayer() != null
                ? mMusicService.getDuration() : 0 : 0;
    }

    public List<SongEntity> getSongData() {
        return getMusicService().getSongData();
    }

    /**
     * 获取当前正在播放的音乐
     *
     * @return 当前正在播放的音乐
     */
    public SongEntity getCurrentSong() {
        if (mMusicService != null && mMusicService.getCurrentSong() != null) {
            return mMusicService.getCurrentSong();
        }
        return null;
    }

    public boolean isPlaying() {
        return mMusicService != null
                && mMusicService.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
    }

    private static final int NEW_DATA = 0x0015;
    private static final int DATA_EXITS = 0x0017;

    @IntDef({NEW_DATA, DATA_EXITS})
    @Retention(RetentionPolicy.SOURCE)
    @interface QueueMode {
    }

    /**
     * 获取当前队列ID
     *
     * @return 当前列表的ID
     */
    public String[] getCurIds() {
        return mCurIds != null ? mCurIds : new String[]{};
    }

    public int getCurPosition() {
        if (mMusicService != null) return mMusicService.getCurrentPosition();
        return 0;
    }

    private MusicManageListener mMusicManageListener;
    private PlayProgressListener mPlayProgressListener;
    private RecentUpdateListener mRecentUpdateListener;
    private List<PlayQueueListener> mPlayQueueListeners;

    public void addPlayQueueListener(PlayQueueListener playQueueListener) {
        if (mPlayQueueListeners == null) {
            mPlayQueueListeners = new ArrayList<>();
        }
        mPlayQueueListeners.add(playQueueListener);
    }

    public void setManageListener(MusicManageListener updateListener) {
        mMusicManageListener = updateListener;
    }

    public void setPlayProgressListener(PlayProgressListener playProgressListener) {
        mPlayProgressListener = playProgressListener;
    }

    public void setRecentUpdateListener(RecentUpdateListener recentUpdateListener) {
        mRecentUpdateListener = recentUpdateListener;
    }

    /**
     * 音乐管理回调接口，便于 Activity 等控制 UI 变化(主要用于MainActivity)
     */
    public interface MusicManageListener {

        void onBufferingUpdate(MediaPlayer mediaPlayer, int percent);

        void onProgress(int progress, int duration);

        void currentPlay(SongEntity songEntity);

        void onPlayerPause();

        void onPlayerResume();//继续播放

        void currentPauseSong(SongEntity songEntity);
    }

    /**
     * 音乐管理回调接口（用于PlayActivity）
     */
    public interface PlayProgressListener {

        void currentPlay(SongEntity songEntity);

        void onProgress(int progress, int duration);

        void onPlayerPause();

        void onPlayerResume();

        void updateQueue(int position);
    }

    /**
     * 最近播放列表刷新接口
     */
    public interface RecentUpdateListener {
        void startPlay();
    }

    /**
     * 播放队列状态刷新列表
     */
    public interface PlayQueueListener {
        void playSuccess();
    }

}
