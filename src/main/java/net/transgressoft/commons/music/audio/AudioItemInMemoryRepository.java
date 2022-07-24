package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.InMemoryRepository;

import java.util.Collection;

/**
 * @author Octavio Calleya
 */
public class AudioItemInMemoryRepository extends InMemoryRepository<AudioItem> implements AudioItemRepository {

    public AudioItemInMemoryRepository(Collection<AudioItem> entities) {
        super(entities);
    }
}
