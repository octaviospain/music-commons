package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.StringAttribute;

public enum AudioItemStringAttribute implements AudioItemAttribute<String>, StringAttribute<AudioItem, AudioItemAttribute<?>, String>  {

    TITLE,
    ARTIST,
    ARTISTS_INVOLVED,
    ALBUM,
    LABEL,
    GENRE,
    COMMENTS,
    ENCODER,
    ENCODING;
}
