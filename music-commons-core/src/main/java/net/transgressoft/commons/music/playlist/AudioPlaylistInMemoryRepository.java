package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.InMemoryRepository;
import net.transgressoft.commons.query.QueryEntity;
import net.transgressoft.commons.query.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"unchecked", "UnstableApiUsage"})
public class AudioPlaylistInMemoryRepository<I extends AudioItem, N extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>>
        implements AudioPlaylistRepository<I, N, D> {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlaylistInMemoryRepository.class);

    private static final String FOUND_MORE_THAN_EXPECTED_BY_NAME_MESSAGE = "Found several playlists when searching single by name '%s': %s";

    private static final AtomicInteger idCounter = new AtomicInteger(0);

    private final InMemoryRepository<MutableAudioPlaylist<I>> playlists = new InMemoryRepository<>();
    private final InMemoryRepository<MutableAudioPlaylistDirectory<I>> directories = new InMemoryRepository<>();
    private final Multimap<MutableAudioPlaylistDirectory<I>, MutableAudioPlaylist<I>> playlistsMultiMap = MultimapBuilder.treeKeys().treeSetValues().build();

    public AudioPlaylistInMemoryRepository() {
        super();
    }

    @Override
    public void add(N... playlists) {
        Objects.requireNonNull(playlists);
        addAll(Set.of(playlists));
    }

    @Override
    public void addAll(Set<N> playlists) {
        Objects.requireNonNull(playlists);
        playlists.forEach(this::add);
    }

    private MutableAudioPlaylistDirectory<I> toMutableDirectory(D playlistDirectory) {
        return new MutablePlaylistDirectory<>(playlistDirectory.id(), playlistDirectory.getName(), playlistDirectory.audioItems(), playlistDirectory.descendantPlaylists());
    }

    private MutableAudioPlaylist<I> toMutablePlaylist(AudioPlaylist<I> playlistDirectory) {
        return new MutablePlaylist<>(playlistDirectory.id(), playlistDirectory.getName(), playlistDirectory.audioItems());
    }

    private Set<MutableAudioPlaylist<I>> toMutablePlaylist(Set<N> audioPlaylists) {
        return audioPlaylists.stream()
                .map(e -> {
                    if (e.isDirectory() && e instanceof AudioPlaylistDirectory dir) {
                        return new MutablePlaylistDirectory<I>(dir.id(), dir.getName(), dir.audioItems(), dir.descendantPlaylists());
                    } else {
                        return new MutablePlaylist<>(e.id(), e.getName(), e.audioItems());
                    }
                })
                .collect(Collectors.toSet());
    }

    private void add(N playlist) {
        if (playlist.isDirectory()) {
            if (directories.findById(playlist.id()).isEmpty()) {
                var mutableDirectory = toMutableDirectory((D) playlist);
                directories.add(mutableDirectory);
                addRecursive(mutableDirectory, mutableDirectory.descendantPlaylists());
            }
        } else {
            if (playlists.findById(playlist.id()).isEmpty()) {
                playlists.add(toMutablePlaylist(playlist));
            }
        }
    }

    private void addRecursive(MutableAudioPlaylistDirectory<I> parent, Set<MutableAudioPlaylist<I>> mutablePlaylistNodes) {
        for (MutableAudioPlaylist<I> playlist : mutablePlaylistNodes) {
            playlistsMultiMap.put(parent, playlist);
            add((N) playlist);
        }
    }

    private MutableAudioPlaylistDirectory<I> findDirectory(int id) {
        return directories.findById(id).orElseThrow(() -> new RuntimeException("Playlist Directory not found by id: " + id));
    }

    @Override
    public void remove(N... entities) {
        Objects.requireNonNull(entities);
        removeAll(Set.of(entities));
    }

    @Override
    public void removeAll(Set<N> entities) {
        Objects.requireNonNull(entities);
        removeRecursive(entities);
    }

    private void removeRecursive(Set<N> mutablePlaylistNodes) {
        for (N p : mutablePlaylistNodes) {
            playlistsMultiMap.asMap().remove(p);
            if (p.isDirectory()) {
                removeRecursive(findDirectory(p.id()).descendantPlaylists());
            }
            directories.findById(p.id()).ifPresent(directories::remove);
            playlists.findById(p.id()).ifPresent(playlists::remove);
        }
    }

    @Override
    public List<N> search(BooleanQueryTerm<N> booleanQueryTerm) {
        var foundPlaylists = playlists.search((BooleanQueryTerm<MutableAudioPlaylist<I>>) booleanQueryTerm).stream()
                .map(obj -> (N) obj)
                .toList();
        var foundDirectories = directories.search((BooleanQueryTerm<MutableAudioPlaylistDirectory<I>>) booleanQueryTerm).stream()
                .map(obj -> (N) obj)
                .toList();
        var result = new ArrayList<N>();
        result.addAll(foundPlaylists);
        result.addAll(foundDirectories);
        return result;
    }

    @Override
    public Optional<N> findById(int id) {
        var pId = playlists.findById(id).orElse(null);
        var dId = directories.findById(id).orElse(null);
        return selectExistingFromPair(pId, dId);
    }

    private Optional<N> selectExistingFromPair(MutableAudioPlaylist<I> pId, MutableAudioPlaylistDirectory<I> dId) {
        if (pId != null && dId != null)
            throw new IllegalStateException("Found 2 playlists with the same id: " + pId + ", " + dId);
        else
            return pId != null ? Optional.of((N) pId) : Optional.ofNullable((N) dId);
    }

    @Override
    public Optional<N> findByUniqueId(String id) {
        var pId = playlists.findByUniqueId(id).orElse(null);
        var dId = directories.findByUniqueId(id).orElse(null);
        return selectExistingFromPair(pId, dId);
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<N> findByAttribute(A a, V v) {
        var foundPlaylists = playlists.findByAttribute(a, v).stream()
                .map(obj -> (N) obj)
                .toList();
        var foundDirectories = directories.findByAttribute(a, v).stream()
                .map(obj -> (N) obj)
                .toList();
        foundPlaylists.addAll(foundDirectories);
        return foundPlaylists;
    }

    @Override
    public <A extends EntityAttribute<V>, V> Optional<N> findSingleByAttribute(A a, V v) throws RepositoryException {
        var pId = playlists.findSingleByAttribute(a, v).orElse(null);
        var dId = directories.findSingleByAttribute(a, v).orElse(null);
        return selectExistingFromPair(pId, dId);
    }

    @Override
    public <A extends EntityAttribute<V>, V, X extends QueryEntity> Optional<X> findSingleByAttribute(A a, V v, Class<X> clazz) throws RepositoryException {
        if (clazz.equals(MutablePlaylist.class))
            return playlists.findSingleByAttribute(a, v, clazz);
        else if (clazz.equals(MutablePlaylistDirectory.class))
            return directories.findSingleByAttribute(a, v, clazz);
        else return Optional.empty();
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
    public Iterator<N> iterator() {
        var setBuilder = ImmutableSet.<N>builderWithExpectedSize(playlists.size() + directories.size());
        playlists.forEach(p -> setBuilder.add(toImmutablePlaylist(p)));
        directories.forEach(d -> setBuilder.add(toImmutablePlaylistDirectory(d)));
        return setBuilder.build().iterator();
    }

    private N toImmutablePlaylist(MutableAudioPlaylist<I> mutablePlaylist) {
        return (N) new ImmutablePlaylist<>(mutablePlaylist.id(), mutablePlaylist.getName(), mutablePlaylist.audioItems());
    }

    private N toImmutablePlaylistDirectory(MutableAudioPlaylistDirectory<I> mutablePlaylistDirectory) {
        return (N) new ImmutablePlaylistDirectory<>(mutablePlaylistDirectory.id(), mutablePlaylistDirectory.getName(),
                                                    mutablePlaylistDirectory.audioItems(), mutablePlaylistDirectory.descendantPlaylists());
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
    public N createPlaylist(String name) {
        return createPlaylist(name, Collections.emptyList());
    }

    @Override
    public N createPlaylist(String name, List<I> audioItems) {
        return (N) playlists.search(PlaylistStringAttribute.NAME.equalsTo(name)).stream()
                .findAny()
                .orElseGet(() -> {
                    var playlist = new MutablePlaylist<>(idCounter.getAndIncrement(), name, audioItems);
                    playlists.add(playlist);
                    return playlist;
                });
    }

    @Override
    public D createPlaylistDirectory(String name) {
        return createPlaylistDirectory(name, Collections.emptyList());
    }

    @Override
    public D createPlaylistDirectory(String name, List<I> audioItems) {
        return (D) directories.search(PlaylistStringAttribute.NAME.equalsTo(name)).stream()
                .findAny()
                .orElseGet(() -> {
                    var playlistDirectory = new MutablePlaylistDirectory<>(idCounter.getAndIncrement(), name, audioItems);
                    directories.add(playlistDirectory);
                    return playlistDirectory;
                });
    }

    @Override
    public void addAudioItemsToPlaylist(List<I> audioItems, N playlist) {
        Objects.requireNonNull(audioItems);
        Objects.requireNonNull(playlist);

        playlists.findById(playlist.id())
                .ifPresent(p -> p.addAudioItems(audioItems));
    }

    @Override
    public void addPlaylistsToDirectory(Set<N> playlistsToAdd, D directory) {
        Objects.requireNonNull(playlistsToAdd);
        Objects.requireNonNull(directory);

        var mutablePlaylists = toMutablePlaylist(playlistsToAdd);

        directories.findById(directory.id())
                .ifPresent(d -> {
                    d.addAllPlaylists(mutablePlaylists);
                    playlistsMultiMap.putAll(d, mutablePlaylists);
                });
    }

    @Override
    public void movePlaylist(N playlistToMove, D destinationPlaylist) {
        Objects.requireNonNull(playlistToMove);
        Objects.requireNonNull(destinationPlaylist);

        findById(playlistToMove.id())
                .map(MutableAudioPlaylist.class::cast)
                .ifPresent(_playlistToMove -> {
                    directories.findById(destinationPlaylist.id()).ifPresent(_destinationPlaylist -> {
                        ancestor(playlistToMove).ifPresent(ancestor -> {
                            playlistsMultiMap.remove(ancestor, _playlistToMove);
                            ancestor.removePlaylist(_playlistToMove);
                        });
                        playlistsMultiMap.put(_destinationPlaylist, _playlistToMove);
                        _destinationPlaylist.addPlaylist(_playlistToMove);

                        LOG.info("Playlist '{}' moved to '{}'", playlistToMove.getName(), destinationPlaylist.getName());
                    });
                });
    }

    private Optional<MutableAudioPlaylistDirectory<I>> ancestor(N playlistNode) {
        if (playlistsMultiMap.containsValue(playlistNode)) {
            return playlistsMultiMap.entries().stream()
                    .filter(entry -> playlistNode.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<N> findAllByName(String name) {
        Objects.requireNonNull(name);
        return ImmutableList.<N>builder()
                .addAll((Iterable<? extends N>) playlists.findByAttribute(PlaylistStringAttribute.NAME, name))
                .addAll((Iterable<? extends N>) directories.findByAttribute(PlaylistStringAttribute.NAME, name))
                .build();
    }

    @Override
    public Optional<N> findSinglePlaylistByName(String name) {
        Objects.requireNonNull(name);

        var allByName = findAllByName(name);
        if (allByName.isEmpty()) {
            return Optional.empty();
        } else if (allByName.size() > 1) {
            throw new IllegalStateException(String.format(FOUND_MORE_THAN_EXPECTED_BY_NAME_MESSAGE, name, allByName));
        } else {
            return Optional.of(allByName.get(0));
        }
    }

    @Override
    public Optional<D> findSingleDirectoryByName(String name) {
        Objects.requireNonNull(name);

        var allByName = findAllByName(name);
        if (allByName.isEmpty()) {
            return Optional.empty();
        } else if (allByName.size() > 1) {
            throw new IllegalStateException(String.format(FOUND_MORE_THAN_EXPECTED_BY_NAME_MESSAGE, name, allByName));
        } else {
            return (Optional<D>) Optional.of(allByName.get(0));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        var that = (AudioPlaylistInMemoryRepository<I, N, D>) o;
        return Objects.equals(playlistsMultiMap, that.playlistsMultiMap) && Objects.equals(playlists, that.playlists) && Objects.equals(directories, that.directories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistsMultiMap, playlists, directories);
    }
}
