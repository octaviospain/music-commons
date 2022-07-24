package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.ShortAttribute;

public enum ShortAudioItemAttribute implements AudioItemAttribute<Short>, ShortAttribute<AudioItem, AudioItemAttribute<?>, Short> {

    YEAR,
    TRACK_NUMBER,
    DISC_NUMBER,
    PLAY_COUNT;
}
