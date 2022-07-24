package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.AudioItem;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface PlaylistExportTool<T extends PlaylistTree<? extends AudioItem>> {

    void exportPlaylistsAsM3u(T playlistTree, Path path) throws ExportException;
}
