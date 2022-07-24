package net.transgressoft.commons.music;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Optional;

/**
 * @author Octavio Calleya
 */
public interface AudioItemRepository {

    Optional<AudioItem> findById(int id);

    void add(AudioItem audioItem);

    void addAll(Collection<AudioItem> audioItem);

    ImmutableSet<AudioItem> audioCollection();

    boolean isEmpty();
}
