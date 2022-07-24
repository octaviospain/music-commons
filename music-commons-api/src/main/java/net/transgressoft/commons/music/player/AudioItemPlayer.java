package net.transgressoft.commons.music.player;

import net.transgressoft.commons.music.audio.AudioItem;

public interface AudioItemPlayer {

    void play(AudioItem audioItem);

    void pause();

    void stop();

    PlayerStatus status();

    void setVolume(double value);

    void seek(int seconds);

    void onFinish(Runnable value);

    String[] supportedAudioFileFormatTypes();
}

enum PlayerStatus {

    UNKNOWN, READY, PAUSED, PLAYING, STOPPED;
}