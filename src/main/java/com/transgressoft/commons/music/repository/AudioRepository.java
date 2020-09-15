package com.transgressoft.commons.music.repository;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public interface AudioRepository {

    ImmutableSet<AudioItem> audioCollection();
}
