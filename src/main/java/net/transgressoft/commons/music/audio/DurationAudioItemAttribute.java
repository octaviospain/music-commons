package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.DurationAttribute;

import java.time.Duration;

public enum DurationAudioItemAttribute implements AudioItemAttribute<Duration>, DurationAttribute<AudioItem, AudioItemAttribute<?>, Duration> {

    DURATION
}
