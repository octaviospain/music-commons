package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * @author Octavio Calleya
 */
class PlaylistExportToolBase implements PlaylistExportTool {

    private static final Logger LOG = LoggerFactory.getLogger(PlaylistExportToolBase.class);

    @Override
    public <P extends MutablePlaylistNode<? extends AudioItem>> void exportPlaylistAsM3u(P playlist, Path path) throws ExportException {
        String playlistFileName = getPlaylistM3uName(playlist.getName());
        LOG.info("Exporting playlist {} to {}", playlist.getName(), path);

        try {
            if (path.resolve(playlistFileName).toFile().exists()) {
                LOG.debug("Playlist {} already exists", playlistFileName);
            } else {
                printPlaylist(playlist, Files.createFile(path.resolve(playlistFileName)));
            }
        }
        catch (IOException exception) {
            throw new ExportException("Error exporting playlist " + playlist, exception);
        }
    }

    @Override
    public <D extends MutablePlaylistDirectory<? extends AudioItem>> void exportPlaylistDirectoryAsM3u(D playlistNode, Path path) throws ExportException {
        exportPlaylistAsM3u(playlistNode, path);

        var playlistIterator = playlistNode.descendantPlaylistsIterator();
        while (playlistIterator.hasNext()) {
            var playlist = playlistIterator.next();
            if (playlist.isDirectory()) {
                exportPlaylistDirectory((MutablePlaylistDirectory<?>) playlist, path);
            } else {
                exportPlaylistAsM3u(playlist, path);
            }
        }
    }

    private void exportPlaylistDirectory(MutablePlaylistDirectory<?> playlistDirectory, Path path) throws ExportException {
        String playlistFolderName = playlistDirectory.getName();
        Path createdDirectory = path.resolve(playlistFolderName);
        File folderFile = createdDirectory.toFile();

        try {
            if (folderFile.exists())
                LOG.debug("Folder {} already exists", folderFile.getAbsolutePath());
            else
                createdDirectory = Files.createDirectory(folderFile.toPath());

            if (!createdDirectory.toFile().exists())
                throw new ExportException("Folder was not created");
            else {
                Path playlistFolderPath = path.resolve(playlistFolderName);

                printDescendantPlaylists(playlistDirectory.descendantPlaylistsIterator(), createdDirectory, playlistFolderPath);
                exportPlaylistAsM3u(playlistDirectory, folderFile.toPath());
            }
        }
        catch (IOException | ExportException exception) {
            throw new ExportException("Error exporting playlist " + playlistFolderName, exception);
        }
    }

    protected String getPlaylistM3uName(String playlistName) {
        return playlistName + ".m3u";
    }

    protected void printPlaylist(MutablePlaylistNode<? extends AudioItem> playlist, Path playlistPath) throws IOException {
        try (PrintWriter printWriter = new PrintWriter(playlistPath.toFile(), StandardCharsets.UTF_8.name())) {
            LOG.info("Creating playlist folder {}", playlistPath);
            printWriter.println("#EXTM3U");
            playlist.audioItemsListIterator().forEachRemaining(audioItem -> {
                printWriter.println("#EXTALB:" + audioItem.album());
                printWriter.println("#EXTART:" + audioItem.artist());
                printWriter.print("#EXTINF:" + audioItem.duration().getSeconds());
                printWriter.println("," + audioItem.title());

                Path parent = playlistPath.getParent();
                Path trackPath = parent.relativize(audioItem.path());

                printWriter.println(trackPath);
            });
        }
    }

    private void printDescendantPlaylists(Iterator<? extends MutablePlaylistNode<? extends AudioItem>> playlistsIterator, Path folder, Path playlistFolderPath) throws IOException {
        try (PrintWriter printWriter = new PrintWriter(playlistFolderPath.toString(), StandardCharsets.UTF_8.name())) {
            printWriter.println("#EXTM3U");
            playlistsIterator.forEachRemaining(playlist -> {
                String playlistM3uName = getPlaylistM3uName(playlist.getName());
                Path innerPlaylistRelativePath = playlistFolderPath.getParent().relativize(folder.resolve(playlistM3uName));
                printWriter.println(innerPlaylistRelativePath);
            });
        }
    }
}
