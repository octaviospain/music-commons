package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.event.QueryEventDispatcher;
import net.transgressoft.commons.query.InMemoryRepository;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.ARTISTS_INVOLVED;

/**
 * @author Octavio Calleya
 */
public abstract class AudioItemInMemoryRepositoryBase<I extends AudioItem> extends InMemoryRepository<I> implements AudioItemRepository<I> {

    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Map<Artist, Set<Album>> albumsByArtist;

    protected AudioItemInMemoryRepositoryBase(Map<Integer, I> audioItems) {
        this(audioItems, null);
    }

    protected AudioItemInMemoryRepositoryBase(Map<Integer, I> audioItems, QueryEventDispatcher<I> eventDispatcher) {
        super(audioItems, eventDispatcher);
        albumsByArtist = audioItems.values().stream().collect(groupingBy(AudioItem::artist, mapping(AudioItem::album, toSet())));
    }

    protected int getNewId() {
        int id;
        do {
            id = idCounter.getAndIncrement();
        } while (contains(id));
        return id;
    }

    @Override
    public boolean add(@NotNull I audioItem) {
        var added = super.add(audioItem);
        addOrReplaceAlbumByArtist(audioItem, added);
        return added;
    }

    private void addOrReplaceAlbumByArtist(AudioItem audioItem, boolean added) {
        var artist = audioItem.artist();
        var album = audioItem.album();
        if (added) {
            if (albumsByArtist.containsKey(artist)) {
                var mappedAlbums = albumsByArtist.get(artist);
                if (! albumsByArtist.get(artist).contains(album)) {
                    mappedAlbums.add(album);
                }
            } else {
                var newSet = new HashSet<Album>();
                newSet.add(album);
                albumsByArtist.put(artist, newSet);
            }
        }
    }

    @Override
    public boolean addOrReplace(@NotNull I audioItem) {
        var addedOrReplaced =  super.addOrReplace(audioItem);
        addOrReplaceAlbumByArtist(audioItem, addedOrReplaced);
        return addedOrReplaced;
    }

    @Override
    public boolean addOrReplaceAll(@NotNull Set<? extends I> audioItems) {
        return super.addOrReplaceAll(audioItems);   // TODO creating protected method in super `addOrReplaceAllInternal` returning the map
    }

    @Override
    public boolean remove(@NotNull I audioItem) {
        var removed =  super.remove(audioItem);
        removeAlbumByArtistInternal(audioItem);
        return removed;
    }

    private void removeAlbumByArtistInternal(AudioItem audioItem) {
        var artist = audioItem.artist();
        if (albumsByArtist.containsKey(artist)) {
            var albums = albumsByArtist.get(audioItem.artist());
            albums = albums.stream().filter(album -> ! album.audioItems().isEmpty()).collect(toSet());
            if (albums.isEmpty()) {
                albumsByArtist.remove(artist);
            } else {
                albumsByArtist.put(artist, albums);
            }
        }
    }

    @Override
    public boolean removeAll(@NotNull Set<? extends I> audioItems) {
        var removed = super.removeAll(audioItems);
        audioItems.forEach(this::removeAlbumByArtistInternal);
        return removed;
    }

    @Override
    public boolean containsAudioItemWithArtist(String artistName) {
        return contains(ARTISTS_INVOLVED.contains(artistName));
    }

    @Override
    public Set<Album> artistAlbums(Artist artist) {
        return albumsByArtist.get(artist);
    }
}
