package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import net.transgressoft.commons.music.audio.AudioItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

class ImmutablePlaylistDirectory<I extends AudioItem> extends ImmutablePlaylist<I> implements AudioPlaylistDirectory<I> {

    private static final Logger LOG = LoggerFactory.getLogger(ImmutablePlaylistDirectory.class);

    private final Set<AudioPlaylist<I>> descendantPlaylists;

    protected <N extends AudioPlaylist<I>> ImmutablePlaylistDirectory(int id, String name, List<I> audioItems, Set<N> playlists) {
        super(id, name, audioItems);
        descendantPlaylists = new ConcurrentSkipListSet<>(playlists);
    }

    @Override
    public <N extends AudioPlaylist<I>> boolean containsPlaylist(N playlist) {
        return descendantPlaylists.contains(playlist);
    }

    protected <N extends AudioPlaylist<I>> void addAll(Set<N> playlists) {
        if (descendantPlaylists.addAll(playlists)) {
            LOG.debug("Added playlists to playlist directory '{}': {}", getName(), playlists);
        }
    }

    protected <N extends AudioPlaylist<I>> void removeAll(Set<N> playlists) {
        if (descendantPlaylists.removeAll(playlists))
            LOG.debug("Playlists removed from playlist directory '{}': {}", getName(), playlists);
    }

    @Override
    public <N extends AudioPlaylist<I>> Set<N> descendantPlaylists() {
        return (Set<N>) descendantPlaylists;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public void exportToM3uFile(Path destinationPath) throws IOException {
        if (destinationPath.toFile().exists()) {
            LOG.debug("Destination file already exists: {}", destinationPath);
        } else {
            Files.createFile(destinationPath);
            printDescendantPlaylistsToM3uFile(destinationPath);

            if (!descendantPlaylists.isEmpty()) {

                Path playlistDirectoryPath = destinationPath.resolve(getName());
                File playlistDirectoryFile = playlistDirectoryPath.toFile();

                if (!playlistDirectoryPath.toFile().exists()) {
                    Files.createDirectory(playlistDirectoryFile.toPath());
                    for (AudioPlaylist<I> playlist : descendantPlaylists) {
                        playlist.exportToM3uFile(playlistDirectoryPath.resolve(playlist.getName()));
                    }
                }
            }
        }
    }

    private void printDescendantPlaylistsToM3uFile(Path playlistFolderPath) throws IOException {
        try (PrintWriter printWriter = new PrintWriter(playlistFolderPath.toString(), StandardCharsets.UTF_8.name())) {
            printWriter.println("#EXTM3U");
            descendantPlaylists.forEach(playlist -> {
                Path descendantPlaylistPath = playlistFolderPath.getParent().relativize(playlistFolderPath.resolve(playlist.getName()));
                printWriter.println(descendantPlaylistPath);
                super.printPlaylist(printWriter, playlistFolderPath);
            });
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (ImmutablePlaylistDirectory<I>) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getId());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", getId())
                .add("name", getName())
                .add("descendantPlaylists", descendantPlaylists.size())
                .add("audioItems", audioItems().size())
                .toString();
    }
}
