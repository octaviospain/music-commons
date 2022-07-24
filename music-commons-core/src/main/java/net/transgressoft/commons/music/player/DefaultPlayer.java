package net.transgressoft.commons.music.player;

import net.transgressoft.commons.music.audio.AudioItem;

public class DefaultPlayer implements AudioItemPlayer {

    @Override
    public void play(AudioItem audioItem) {

    }

    @Override
    public void pause() {

    }

    @Override
    public void stop() {

    }

    @Override
    public PlayerStatus status() {
        return null;
    }

    @Override
    public void setVolume(double value) {

    }

    @Override
    public void seek(int seconds) {

    }

    @Override
    public void onFinish(Runnable value) {

    }

    @Override
    public String[] supportedAudioFileFormatTypes() {
        return new String[]{"wav", "m4a", "mp3"};
    }
}
