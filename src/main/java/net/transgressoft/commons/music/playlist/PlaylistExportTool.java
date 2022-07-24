package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface PlaylistExportTool<T extends PlaylistNode<? extends AudioItem>> {

    void exportPlaylistAsM3u(T playlistNode, Path path) throws ExportException;

    void exportPlaylistDirectoryAsM3u(T playlistNode, Path path) throws ExportException;
}
