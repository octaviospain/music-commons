package com.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableCollection;
import com.transgressoft.commons.music.AudioItem;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * @author Octavio Calleya  
 */
public abstract class PlaylistExportToolBase<P extends AudioPlaylist> implements PlaylistExportTool<P> {

    private final Logger LOG = LoggerFactory.getLogger(getClass().getName());

    @SuppressWarnings("unchecked")
    @Override
    public void exportPlaylistsAsM3u(Collection<P> playlists, Path folder) throws ExportException {
        for (P playlist : playlists) {
            String playlistFileName = getPlaylistM3uName(playlist.name());
            try {
                if (! playlist.childPlaylists().isEmpty()) {
                    Path createdDirectory = folder.resolve(playlist.name());
                    File folderFile = createdDirectory.toFile();

                    if (folderFile.exists())
                        LOG.debug("Folder {} already exists", folderFile.getAbsolutePath());
                    else
                        createdDirectory = Files.createDirectory(folderFile.toPath());

                    if (! createdDirectory.toFile().exists())
                        throw new ExportException("Folder was not created");
                    else {
                        Path playlistFolderName = folder.resolve(playlistFileName);
                        printContainedPlaylistsInFolderPlaylist((Collection<P>) playlist.childPlaylists(), createdDirectory, playlistFolderName);
                        exportPlaylistsAsM3u((Collection<P>) playlist.childPlaylists(), folderFile.toPath());
                    }

                } else {
                    LOG.info("Exporting playlist {} to {}", playlist.name(), folder);

                    if (folder.resolve(playlistFileName).toFile().exists()) {
                        LOG.debug("Playlist {} already exists", playlistFileName);
                    } else {
                        printPlaylist(playlist, Files.createFile(folder.resolve(playlistFileName)));
                    }
                }
            }
            catch (IOException | ExportException exception) {
                throw new ExportException("Error exporting playlist " + playlist.toString(), exception);
            }
        }
    }

    protected String getPlaylistM3uName(String playlistName) {
        return playlistName + ".m3u";
    }

    private void printContainedPlaylistsInFolderPlaylist(Collection<P> playlists, Path folder, Path playlistFolderPath) throws IOException {
        try (PrintWriter printWriter = new PrintWriter(playlistFolderPath.toString(), StandardCharsets.UTF_8.name())) {
            printWriter.println("#EXTM3U");
            playlists.forEach(playlist -> {
                String playlistM3uName = getPlaylistM3uName(playlist.name());
                Path innerPlaylistRelativePath = playlistFolderPath.getParent().relativize(folder.resolve(playlistM3uName));
                printWriter.println(innerPlaylistRelativePath);
            });
        }
    }

    protected void printPlaylist(P audioPlaylist, Path playlistsPath) throws IOException {
        ImmutableCollection<AudioItem> tracks = audioPlaylist.audioItems();
        try (PrintWriter printWriter = new PrintWriter(playlistsPath.toFile(), StandardCharsets.UTF_8.name())) {
            LOG.info("Creating playlist folder {}", playlistsPath);
            printWriter.println("#EXTM3U");
            for (AudioItem audioItem : tracks) {
                printWriter.println("#EXTALB:" + audioItem.album());
                printWriter.println("#EXTART:" + audioItem.artist());
                printWriter.print("#EXTINF:" + audioItem.duration().getSeconds());
                printWriter.println("," + audioItem.name());

                Path parent = playlistsPath.getParent();
                Path trackPath = parent.relativize(audioItem.path());

                printWriter.println(trackPath.toString());
            }
        }
    }
}
