package com.transgressoft.commons.music;

import com.google.common.collect.ImmutableSet;

import java.util.Optional;

/**
 * @author Octavio Calleya
 */
public interface AudioItemRepository {

    Optional<AudioItem> findById(int id);

    ImmutableSet<AudioItem> audioCollection();
}
