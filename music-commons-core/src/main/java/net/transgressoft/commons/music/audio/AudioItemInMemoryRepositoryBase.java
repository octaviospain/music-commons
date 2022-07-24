package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.event.QueryEventDispatcher;
import net.transgressoft.commons.query.InMemoryRepository;

import java.util.Map;

import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioItemInMemoryRepositoryBase<I extends AudioItem> extends InMemoryRepository<I> implements AudioItemRepository<I> {

    protected AudioItemInMemoryRepositoryBase(Map<Integer, I> audioItems) {
        super(audioItems, null);
    }

    protected AudioItemInMemoryRepositoryBase(Map<Integer, I> audioItems, QueryEventDispatcher<I> eventDispatcher) {
        super(audioItems, eventDispatcher);
    }

    @Override
    public boolean containsAudioItemWithArtist(String artistName) {
        return search(ARTISTS_INVOLVED.contains(artistName)).isEmpty();
    }
}
