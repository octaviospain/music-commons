package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.InMemoryRepository;
import net.transgressoft.commons.query.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME;

@SuppressWarnings("unchecked")
public abstract class AudioPlaylistInMemoryRepositoryBase<I extends AudioItem, P extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>,
        MP extends MutableAudioPlaylist<I>, MD extends MutableAudioPlaylistDirectory<I>> implements AudioPlaylistRepository<I, P, D> {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlaylistInMemoryRepositoryBase.class);

    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Set<Integer> idSet = new HashSet<>();

    private final InMemoryRepository<MP> playlists;
    private final InMemoryRepository<MD> directories;
    private final Multimap<String, String> playlistsMultiMap = MultimapBuilder.treeKeys().treeSetValues().build();

    protected AudioPlaylistInMemoryRepositoryBase(Map<Integer, MP> playlistsById, Map<Integer, MD> directoriesById) {
        playlists = new InMemoryRepository<>(playlistsById, null);
        directories = new InMemoryRepository<>(directoriesById, null);
    }

    protected int getNewId() {
        int id;
        do {
            id = idCounter.getAndIncrement();
        } while (idSet.contains(id));
        idSet.add(id);
        return id;
    }

    @Override
    public boolean add(P playlist) {
        Objects.requireNonNull(playlist);
        return addAll(Collections.singleton(playlist));
    }

    @Override
    public boolean addAll(Set<? extends P> playlists) {
        Objects.requireNonNull(playlists);
        AtomicBoolean added = new AtomicBoolean(false);
        playlists.forEach(playlist -> added.set(added.get() || addInternal(playlist)));
        return added.get();
    }

    private boolean addInternal(P playlist) {
        boolean added = false;
        if (playlist.isDirectory()) {
            if (directories.findByAttribute(NAME, playlist.getName()).isEmpty()) {
                var mutableDirectory = toMutableDirectory((D) playlist);
                added = directories.add(mutableDirectory);
                added |= addRecursive(mutableDirectory, mutableDirectory.descendantPlaylists());
            }
        } else {
            if (playlists.findByAttribute(NAME, playlist.getName()).isEmpty()) {
                added = playlists.add(toMutablePlaylist(playlist));
            }
        }
        return added;
    }

    private boolean addRecursive(MD parent, Set<MP> mutablePlaylistNodes) {
        boolean result = false;
        for (MP playlist : mutablePlaylistNodes) {
            playlistsMultiMap.put(parent.getUniqueId(), playlist.getUniqueId());
            result |= addInternal((P) playlist);
        }
        return result;
    }

    protected abstract MP toMutablePlaylist(P playlistDirectory);

    protected abstract Set<MutableAudioPlaylist<I>> toMutablePlaylists(Set<P> audioPlaylists);

    protected abstract MD toMutableDirectory(D playlistDirectory);

    protected abstract P toImmutablePlaylist(MutableAudioPlaylist<I> audioPlaylist);

    protected abstract P toImmutablePlaylist(P audioPlaylist);

    protected abstract P toImmutablePlaylistDirectory(MutableAudioPlaylistDirectory<I> playlistDirectory);

    protected abstract Set<P> toImmutablePlaylistDirectories(Set<P> audioPlaylists);

    @Override
    public boolean remove(P entity) {
        Objects.requireNonNull(entity);
        return removeAll(Set.of(entity));
    }

    @Override
    public boolean removeAll(Set<? extends P> entities) {
        Objects.requireNonNull(entities);
        return removeRecursive(entities);
    }

    private boolean removeRecursive(Set<? extends P> mutablePlaylistNodes) {
        AtomicBoolean result = new AtomicBoolean(false);
        for (P p : mutablePlaylistNodes) {
            playlistsMultiMap.asMap().remove(p.getUniqueId());
            if (p.isDirectory()) {
                result.set(removeRecursive(findDirectory(p.getId()).descendantPlaylists()) || result.get() );
            }
            directories.findById(p.getId()).ifPresent(entity -> result.set(directories.remove(entity) || result.get()));
            playlists.findById(p.getId()).ifPresent(entity -> result.set(playlists.remove(entity) || result.get()));
        }
        return result.get();
    }

    private MD findDirectory(int id) {
        return directories.findById(id).orElseThrow(() -> new RuntimeException("Playlist Directory not found by id: " + id));
    }

    @Override
    public void clear() {
        playlists.clear();
        directories.clear();
        playlistsMultiMap.clear();
    }

    @Override
    public List<P> search(BooleanQueryTerm<P> booleanQueryTerm) {
        return ImmutableList.<P>builder()
                .addAll(playlists.search((BooleanQueryTerm<MP>) booleanQueryTerm).stream()
                                .map(this::toImmutablePlaylist)
                                .toList())
                .addAll(directories.search((BooleanQueryTerm<MD>) booleanQueryTerm).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .toList()).build();
    }

    @Override
    public Optional<P> findById(Integer id) {
        return findByIdInternal(id).map(this::toImmutablePlaylist);
    }

    private Optional<MP> findByIdInternal(int id) {
        return playlists.findById(id).or(() -> (Optional<? extends MP>) directories.findById(id));
    }

    @Override
    public Optional<P> findByUniqueId(String id) {
        return playlists.findByUniqueId(id)
                .map(this::toImmutablePlaylist)
                .or(() -> directories.findByUniqueId(id).map(this::toImmutablePlaylistDirectory));
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<P> findByAttribute(A a, V v) {
        return ImmutableList.<P>builder()
                .addAll(playlists.findByAttribute(a, v).stream()
                                .map(this::toImmutablePlaylist)
                                .toList())
                .addAll(directories.findByAttribute(a, v).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .toList()).build();
    }

    @Override
    public <A extends EntityAttribute<V>, V> Optional<P> findSingleByAttribute(A a, V v) throws RepositoryException {
        var foundPlaylist = playlists.findSingleByAttribute(a, v).orElse(null);
        var foundDirectory = directories.findSingleByAttribute(a, v).orElse(null);
        if (foundPlaylist != null && foundDirectory != null)
            throw new RepositoryException("Found 2 entities with the same attribute: [" + foundPlaylist + ", " + foundDirectory + "]");

        return foundPlaylist != null ?
                Optional.of(toImmutablePlaylist(foundPlaylist)) : Optional.ofNullable(toImmutablePlaylistDirectory(foundDirectory));
    }

    @Override
    public int size() {
        return playlists.size() + directories.size();
    }

    @Override
    public boolean isEmpty() {
        return playlists.isEmpty() && directories.isEmpty();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public Iterator<P> iterator() {
        var setBuilder = ImmutableSet.<P>builderWithExpectedSize(playlists.size() + directories.size());
        playlists.forEach(p -> setBuilder.add(toImmutablePlaylist(p)));
        directories.forEach(d -> setBuilder.add(toImmutablePlaylistDirectory(d)));
        return setBuilder.build().iterator();
    }

    @Override
    public int numberOfPlaylists() {
        return playlists.size();
    }

    @Override
    public int numberOfPlaylistDirectories() {
        return directories.size();
    }

    @Override
    public P createPlaylist(String name) throws RepositoryException {
        return createPlaylist(name, Collections.emptyList());
    }

    @Override
    public P createPlaylist(String name, List<I> audioItems) throws RepositoryException {
        var searchResult = findSinglePlaylistByName(name);
        if (searchResult.isEmpty()) {
            var playlist = (MP) new MutablePlaylist<>(getNewId(), name, audioItems);
            playlists.add(playlist);
            return toImmutablePlaylist(playlist);
        } else {
            throw new RepositoryException("Playlist with name '" + name + "' already exists");
        }
    }

    @Override
    public D createPlaylistDirectory(String name) throws RepositoryException {
        return createPlaylistDirectory(name, Collections.emptyList());
    }

    @Override
    public D createPlaylistDirectory(String name, List<I> audioItems) throws RepositoryException {
        var searchResult = findSingleDirectoryByName(name);
        if (searchResult.isEmpty()) {
            var playlistDirectory = (MD) new MutablePlaylistDirectory<>(getNewId(), name, audioItems);
            directories.add(playlistDirectory);
            return (D) toImmutablePlaylistDirectory(playlistDirectory);
        } else {
            throw new RepositoryException("Playlist with name '" + name + "' already exists");
        }
    }

    @Override
    public void addAudioItemsToPlaylist(Collection<I> audioItems, P playlist) {
        Objects.requireNonNull(audioItems);
        Objects.requireNonNull(playlist);

        playlists.findById(playlist.getId())
                .or(() -> (Optional<? extends MP>) directories.findById(playlist.getId()))
                .ifPresent(p -> p.addAudioItems(audioItems));
    }

    @Override
    public void addPlaylistsToDirectory(Set<P> playlistsToAdd, D directory) {
        Objects.requireNonNull(playlistsToAdd);
        Objects.requireNonNull(directory);

        var mutablePlaylists = toMutablePlaylists(playlistsToAdd);

        directories.findById(directory.getId())
                .ifPresent(d -> {
                    d.addPlaylists(mutablePlaylists);
                    playlistsMultiMap.putAll(d.getUniqueId(), mutablePlaylists.stream().map(MutableAudioPlaylist::getUniqueId).collect(Collectors.toSet()));
                });
    }

    @Override
    public void movePlaylist(P playlistToMove, D destinationPlaylist) {
        Objects.requireNonNull(playlistToMove);
        Objects.requireNonNull(destinationPlaylist);

        findByIdInternal(playlistToMove.getId())
                .ifPresent(playlist -> directories.findById(destinationPlaylist.getId()).ifPresent(playlistDirectory -> {
                    ancestor(playlistToMove).ifPresent(ancestor -> {
                        playlistsMultiMap.remove(ancestor.getUniqueId(), playlist.getUniqueId());
                        ancestor.removePlaylists(playlist);
                    });
                    playlistsMultiMap.put(playlistDirectory.getUniqueId(), playlist.getUniqueId());
                    playlistDirectory.addPlaylists(playlist);

                    LOG.debug("Playlist '{}' moved to '{}'", playlistToMove.getName(), destinationPlaylist.getName());
                }));
    }

    private Optional<MD> ancestor(P playlistNode) {
        if (playlistsMultiMap.containsValue(playlistNode.getUniqueId())) {
            return playlistsMultiMap.entries().stream()
                    .filter(entry -> playlistNode.getUniqueId().equals(entry.getValue()))
                    .map(entry -> directories.findByUniqueId(entry.getKey()).get())
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void removeAudioItemsFromPlaylist(Collection<I> audioItems, P playlist) {
        findByIdInternal(playlist.getId()).ifPresent(p -> p.removeAudioItems(audioItems));
    }

    @Override
    public void removeAudioItems(Collection<I> audioItems) {
        playlists.forEach(p -> p.removeAudioItems(audioItems));
        directories.forEach(d -> d.removeAudioItems(audioItems));
    }

    @Override
    public List<P> findAllByName(String name) {
        Objects.requireNonNull(name);
        return ImmutableList.<P>builder()
                .addAll(playlists.findByAttribute(NAME, name).stream()
                                .map(this::toImmutablePlaylist)
                                .collect(Collectors.toSet()))
                .addAll(directories.findByAttribute(NAME, name).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .collect(Collectors.toSet())).build();
    }

    @Override
    public Optional<P> findSinglePlaylistByName(String name) {
        Objects.requireNonNull(name);
        try {
            return playlists.findSingleByAttribute(NAME, name).map(this::toImmutablePlaylist);
        }
        catch (RepositoryException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public Optional<D> findSingleDirectoryByName(String name) {
        Objects.requireNonNull(name);
        try {
            return directories.findSingleByAttribute(NAME, name).map(playlistDirectory -> (D) toImmutablePlaylistDirectory(playlistDirectory));
        }
        catch (RepositoryException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        var that = (AudioPlaylistInMemoryRepositoryBase<I, P, D, MP, MD>) o;
        return Objects.equals(playlistsMultiMap, that.playlistsMultiMap) && Objects.equals(playlists, that.playlists) && Objects.equals(directories, that.directories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistsMultiMap, playlists, directories);
    }
}
