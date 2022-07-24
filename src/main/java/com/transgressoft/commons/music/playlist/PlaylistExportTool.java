package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

import java.nio.file.Path;
import java.util.Collection;

/**
 * @author Octavio Calleya
 */
public interface PlaylistExportTool<P extends AudioPlaylist<? extends AudioItem>> {

    void exportPlaylistsAsM3u(Collection<P> playlists, Path folder) throws ExportException;
}
