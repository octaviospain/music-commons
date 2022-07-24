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

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME;

public class AudioPlaylistInMemoryRepository<I extends AudioItem, N extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>>
        implements AudioPlaylistRepository<I, N, D> {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlaylistInMemoryRepository.class);

    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private static final Set<Integer> idSet = new HashSet<>();

    private final InMemoryRepository<MutableAudioPlaylist<I>> playlists = new InMemoryRepository<>();
    private final InMemoryRepository<MutableAudioPlaylistDirectory<I>> directories = new InMemoryRepository<>();
    private final Multimap<String, String> playlistsMultiMap = MultimapBuilder.treeKeys().treeSetValues().build();

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

    private void add(N playlist) {
        if (playlist.isDirectory()) {
            if (directories.findByAttribute(NAME, playlist.getName()).isEmpty()) {
                var mutableDirectory = toMutableDirectory((D) playlist);
                directories.add(mutableDirectory);
                addRecursive(mutableDirectory, mutableDirectory.descendantPlaylists());
            }
        } else {
            if (playlists.findByAttribute(NAME, playlist.getName()).isEmpty()) {
                playlists.add(toMutablePlaylists(playlist));
            }
        }
    }

    protected int getNewId() {
        int id;
        do {
            id = idCounter.getAndIncrement();
        } while (idSet.contains(id));
        idSet.add(id);
        return id;
    }

    private MutableAudioPlaylist<I> toMutablePlaylists(N playlistDirectory) {
        return new MutablePlaylist<>(getNewId(), playlistDirectory.getName(), playlistDirectory.audioItems());
    }

    private MutableAudioPlaylistDirectory<I> toMutableDirectory(D playlistDirectory) {
        return new MutablePlaylistDirectory<>(getNewId(), playlistDirectory.getName(), playlistDirectory.audioItems(), toMutablePlaylists(playlistDirectory.descendantPlaylists()));
    }

    private void addRecursive(MutableAudioPlaylistDirectory<I> parent, Set<MutableAudioPlaylist<I>> mutablePlaylistNodes) {
        for (MutableAudioPlaylist<I> playlist : mutablePlaylistNodes) {
            playlistsMultiMap.put(parent.getUniqueId(), playlist.getUniqueId());
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
            playlistsMultiMap.asMap().remove(p.getUniqueId());
            if (p.isDirectory()) {
                removeRecursive(findDirectory(p.id()).descendantPlaylists());
            }
            directories.findById(p.id()).ifPresent(directories::remove);
            playlists.findById(p.id()).ifPresent(playlists::remove);
        }
    }

    @Override
    public List<N> search(BooleanQueryTerm<N> booleanQueryTerm) {
        return ImmutableList.<N>builder()
                .addAll(playlists.search((BooleanQueryTerm<MutableAudioPlaylist<I>>) booleanQueryTerm).stream()
                                .map(this::toImmutablePlaylist)
                                .toList())
                .addAll(directories.search((BooleanQueryTerm<MutableAudioPlaylistDirectory<I>>) booleanQueryTerm).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .toList()).build();
    }

    @Override
    public Optional<N> findById(int id) {
        return findByIdInternal(id).map(this::toImmutablePlaylist);
    }

    private Optional<MutableAudioPlaylist<I>> findByIdInternal(int id) {
        return playlists.findById(id).or(() -> directories.findById(id));
    }

    @Override
    public Optional<N> findByUniqueId(String id) {
        return playlists.findByUniqueId(id)
                .map(this::toImmutablePlaylist)
                .or(() -> directories.findByUniqueId(id).map(this::toImmutablePlaylistDirectory));
    }

    @Override
    public <A extends EntityAttribute<V>, V> List<N> findByAttribute(A a, V v) {
        return ImmutableList.<N>builder()
                .addAll(playlists.findByAttribute(a, v).stream()
                                .map(this::toImmutablePlaylist)
                                .toList())
                .addAll(directories.findByAttribute(a, v).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .toList()).build();
    }

    @Override
    public <A extends EntityAttribute<V>, V> Optional<N> findSingleByAttribute(A a, V v) throws RepositoryException {
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
    public Iterator<N> iterator() {
        var setBuilder = ImmutableSet.<N>builderWithExpectedSize(playlists.size() + directories.size());
        playlists.forEach(p -> setBuilder.add(toImmutablePlaylist(p)));
        directories.forEach(d -> setBuilder.add(toImmutablePlaylistDirectory(d)));
        return setBuilder.build().iterator();
    }

    private N toImmutablePlaylist(MutableAudioPlaylist<I> audioPlaylist) {
        return (N) new ImmutablePlaylist<>(audioPlaylist.id(), audioPlaylist.getName(), audioPlaylist.audioItems());
    }

    private N toImmutablePlaylist(N audioPlaylist) {
        return (N) new ImmutablePlaylist<>(audioPlaylist.id(), audioPlaylist.getName(), audioPlaylist.audioItems());
    }

    private N toImmutablePlaylistDirectory(MutableAudioPlaylistDirectory<I> playlistDirectory) {
        return playlistDirectory == null ? null :
                (N) new ImmutablePlaylistDirectory<>(playlistDirectory.id(), playlistDirectory.getName(), playlistDirectory.audioItems(),
                                                     toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists()));
    }

    private N toImmutablePlaylistDirectory(D playlistDirectory) {
        return playlistDirectory == null ? null :
                (N) new ImmutablePlaylistDirectory<>(playlistDirectory.id(), playlistDirectory.getName(), playlistDirectory.audioItems(),
                                                     toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists()));
    }

    private Set<N> toImmutablePlaylistDirectories(Set<N> audioPlaylists) {
        return audioPlaylists.stream()
                .map(p -> p.isDirectory() ? toImmutablePlaylistDirectory((D) p) : toImmutablePlaylist(p))
                .collect(Collectors.toSet());
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
    public N createPlaylist(String name) throws RepositoryException {
        return createPlaylist(name, Collections.emptyList());
    }

    @Override
    public N createPlaylist(String name, List<I> audioItems) throws RepositoryException {
        var searchResult = playlists.search(NAME.equalsTo(name));
        if (searchResult.isEmpty()) {
            var playlist = new MutablePlaylist<>(getNewId(), name, audioItems);
            playlists.add(playlist);
            return toImmutablePlaylist(playlist);
        } else if (searchResult.size() == 1) {
            throw new RepositoryException("Playlist with name '" + name + "' already exists");
        } else {
            throw new IllegalStateException("Found more than 1 playlist by name '" + name + "': " + searchResult);
        }
    }

    @Override
    public D createPlaylistDirectory(String name) throws RepositoryException {
        return createPlaylistDirectory(name, Collections.emptyList());
    }

    @Override
    public D createPlaylistDirectory(String name, List<I> audioItems) throws RepositoryException {
        var searchResult = directories.search(NAME.equalsTo(name));
        if (searchResult.isEmpty()) {
            var playlistDirectory = new MutablePlaylistDirectory<>(getNewId(), name, audioItems);
            directories.add(playlistDirectory);
            return (D) toImmutablePlaylistDirectory(playlistDirectory);
        } else if (searchResult.size() == 1) {
            throw new RepositoryException("Playlist with name '" + name + "' already exists");
        } else {
            throw new IllegalStateException("Found more than 1 playlist by name '" + name + "': " + searchResult);
        }
    }

    @Override
    public void addAudioItemsToPlaylist(List<I> audioItems, N playlist) {
        Objects.requireNonNull(audioItems);
        Objects.requireNonNull(playlist);

        playlists.findById(playlist.id())
                .or(() -> directories.findById(playlist.id()))
                .ifPresent(p -> p.addAudioItems(audioItems));
    }

    @Override
    public void addPlaylistsToDirectory(Set<N> playlistsToAdd, D directory) {
        Objects.requireNonNull(playlistsToAdd);
        Objects.requireNonNull(directory);

        var mutablePlaylists = toMutablePlaylists(playlistsToAdd);

        directories.findById(directory.id())
                .ifPresent(d -> {
                    d.addAllPlaylists(mutablePlaylists);
                    playlistsMultiMap.putAll(d.getUniqueId(), mutablePlaylists.stream().map(MutableAudioPlaylist::getUniqueId).collect(Collectors.toSet()));
                });
    }

    private Set<MutableAudioPlaylist<I>> toMutablePlaylists(Set<N> audioPlaylists) {
        return audioPlaylists.stream()
                .map(e -> {
                    if (e.isDirectory()) {
                        AudioPlaylistDirectory<I> dir = (AudioPlaylistDirectory<I>) e;
                        return new MutablePlaylistDirectory<>(dir.id(), dir.getName(), dir.audioItems(), dir.descendantPlaylists());
                    } else {
                        return new MutablePlaylist<>(e.id(), e.getName(), e.audioItems());
                    }
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void movePlaylist(N playlistToMove, D destinationPlaylist) {
        Objects.requireNonNull(playlistToMove);
        Objects.requireNonNull(destinationPlaylist);

        findByIdInternal(playlistToMove.id())
                .ifPresent(playlist -> directories.findById(destinationPlaylist.id()).ifPresent(playlistDirectory -> {
                    ancestor(playlistToMove).ifPresent(ancestor -> {
                        playlistsMultiMap.remove(ancestor.getUniqueId(), playlist.getUniqueId());
                        ancestor.removePlaylist(playlist);
                    });
                    playlistsMultiMap.put(playlistDirectory.getUniqueId(), playlist.getUniqueId());
                    playlistDirectory.addPlaylist(playlist);

                    LOG.info("Playlist '{}' moved to '{}'", playlistToMove.getName(), destinationPlaylist.getName());
                }));
    }

    private Optional<MutableAudioPlaylistDirectory<I>> ancestor(N playlistNode) {
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
    public List<N> findAllByName(String name) {
        Objects.requireNonNull(name);
        return ImmutableList.<N>builder()
                .addAll(playlists.findByAttribute(NAME, name).stream()
                                .map(this::toImmutablePlaylist)
                                .collect(Collectors.toSet()))
                .addAll(directories.findByAttribute(NAME, name).stream()
                                .map(this::toImmutablePlaylistDirectory)
                                .collect(Collectors.toSet())).build();
    }

    @Override
    public Optional<N> findSinglePlaylistByName(String name) {
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
        var that = (AudioPlaylistInMemoryRepository<I, N, D>) o;
        return Objects.equals(playlistsMultiMap, that.playlistsMultiMap) && Objects.equals(playlists, that.playlists) && Objects.equals(directories, that.directories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistsMultiMap, playlists, directories);
    }
}
