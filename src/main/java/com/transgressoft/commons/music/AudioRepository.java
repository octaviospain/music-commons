package com.transgressoft.commons.music;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public interface AudioRepository {

    ImmutableSet<AudioItem> audioCollection();
}
