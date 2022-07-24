package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableCollection;
import net.transgressoft.commons.music.audio.AudioItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * @author Octavio Calleya
 */
public abstract class PlaylistExportToolBase<T extends PlaylistTree<? extends AudioItem>> implements PlaylistExportTool<T> {

    private final Logger LOG = LoggerFactory.getLogger(getClass().getName());

    @SuppressWarnings ("unchecked")
    @Override
    public void exportPlaylistsAsM3u(T playlistTree, Path path) throws ExportException {
        for (AudioPlaylist<? extends AudioItem> playlist : playlistTree.audioPlaylists()) {
            String playlistFileName = getPlaylistM3uName(playlist.name());
            LOG.info("Exporting playlist {} to {}", playlist.name(), path);

            try {
                if (path.resolve(playlistFileName).toFile().exists()) {
                    LOG.debug("Playlist {} already exists", playlistFileName);
                } else {
                    printPlaylist(playlist, Files.createFile(path.resolve(playlistFileName)));
                }
            }
            catch (IOException exception) {
                throw new ExportException("Error exporting playlist " + playlist.toString(), exception);
            }
        }

        for (PlaylistTree<? extends AudioItem> subPlaylistTree : playlistTree.includedPlaylistTrees()) {
            String playlistFolderName = subPlaylistTree.name();
            Path createdDirectory = path.resolve(playlistFolderName);
            File folderFile = createdDirectory.toFile();

            try {
                if (folderFile.exists())
                    LOG.debug("Folder {} already exists", folderFile.getAbsolutePath());
                else
                    createdDirectory = Files.createDirectory(folderFile.toPath());

                if (! createdDirectory.toFile().exists())
                    throw new ExportException("Folder was not created");
                else {
                    Path playlistFolderPath = path.resolve(playlistFolderName);
                    printContainedPlaylistsInFolderPlaylist(subPlaylistTree.audioPlaylists(), createdDirectory, playlistFolderPath);
                    exportPlaylistsAsM3u((T) subPlaylistTree, folderFile.toPath());
                }
            }
            catch (IOException | ExportException exception) {
                throw new ExportException("Error exporting playlist " + playlistFolderName, exception);
            }
        }
    }

    protected String getPlaylistM3uName(String playlistName) {
        return playlistName + ".m3u";
    }

    private void printContainedPlaylistsInFolderPlaylist(Set<? extends AudioPlaylist<? extends AudioItem>> playlists, Path folder, Path playlistFolderPath) throws IOException {
        try (PrintWriter printWriter = new PrintWriter(playlistFolderPath.toString(), StandardCharsets.UTF_8.name())) {
            printWriter.println("#EXTM3U");
            playlists.forEach(playlist -> {
                String playlistM3uName = getPlaylistM3uName(playlist.name());
                Path innerPlaylistRelativePath = playlistFolderPath.getParent().relativize(folder.resolve(playlistM3uName));
                printWriter.println(innerPlaylistRelativePath);
            });
        }
    }

    protected void printPlaylist(AudioPlaylist<? extends AudioItem> playlist, Path playlistPath) throws IOException {
        ImmutableCollection<? extends AudioItem> tracks = playlist.audioItems();
        try (PrintWriter printWriter = new PrintWriter(playlistPath.toFile(), StandardCharsets.UTF_8.name())) {
            LOG.info("Creating playlist folder {}", playlistPath);
            printWriter.println("#EXTM3U");
            for (AudioItem audioItem : tracks) {
                printWriter.println("#EXTALB:" + audioItem.album());
                printWriter.println("#EXTART:" + audioItem.artist());
                printWriter.print("#EXTINF:" + audioItem.duration().getSeconds());
                printWriter.println("," + audioItem.title());

                Path parent = playlistPath.getParent();
                Path trackPath = parent.relativize(audioItem.path());

                printWriter.println(trackPath.toString());
            }
        }
    }
}
