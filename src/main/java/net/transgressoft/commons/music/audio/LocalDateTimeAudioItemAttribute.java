package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.LocalDateTimeAttribute;

import java.time.LocalDateTime;

public enum LocalDateTimeAudioItemAttribute implements AudioItemAttribute<LocalDateTime>, LocalDateTimeAttribute<AudioItem, AudioItemAttribute<?>, LocalDateTime> {

    DATE_OF_INCLUSION,
    LAST_DATE_MODIFIED
}
