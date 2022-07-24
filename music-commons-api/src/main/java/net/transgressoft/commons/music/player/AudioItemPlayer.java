package net.transgressoft.commons.music.player;

import net.transgressoft.commons.music.audio.AudioItem;

public interface AudioItemPlayer {

    enum Status {

        UNKNOWN, READY, PAUSED, PLAYING, STOPPED, STALLED, HALTED, DISPOSED
    }

    void play(AudioItem audioItem);

    void pause();

    void stop();

    Status status();

    void setVolume(double value);

    void seek(int seconds);

    void onFinish(Runnable value);
}