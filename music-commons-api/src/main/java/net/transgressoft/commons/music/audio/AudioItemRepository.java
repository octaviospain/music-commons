package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.Repository;

import java.nio.file.Path;
import java.util.Set;

public interface AudioItemRepository<I extends AudioItem> extends Repository<I> {

    AudioItem createFromFile(Path path) throws AudioItemManipulationException;

    boolean containsAudioItemWithArtist(String artistName);

    Set<Album> artistAlbums(Artist artist);
}
