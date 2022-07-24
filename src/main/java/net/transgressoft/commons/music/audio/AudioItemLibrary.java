package net.transgressoft.commons.music.audio;

import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Octavio Calleya
 */
public interface AudioItemLibrary extends Iterable<AudioItem> {

    Optional<AudioItem> findById(int id);

    List<AudioItem> findByPath(Path path);

    AudioItemLibrary add(AudioItem audioItem);

    AudioItemLibrary addAll(Set<AudioItem> audioItems);

    AudioItemLibrary delete(AudioItem audioItem);

    AudioItemLibrary deleteAll(Set<AudioItem> audioItems);

    ImmutableSet<AudioItem> randomSet();

    boolean isEmpty();
}