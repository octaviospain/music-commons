package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.PathAttribute;

import java.nio.file.Path;

public enum PathAudioItemAttribute implements AudioItemAttribute<Path>, PathAttribute<AudioItem, AudioItemAttribute<?>, Path> {

    PATH
}
