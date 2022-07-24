package com.transgressoft.commons.music;

import com.google.common.collect.ImmutableSet;

/**
 * @author Octavio Calleya
 */
public interface AudioItemRepository {

    AudioItem findById(int id);

    ImmutableSet<AudioItem> audioCollection();
}
