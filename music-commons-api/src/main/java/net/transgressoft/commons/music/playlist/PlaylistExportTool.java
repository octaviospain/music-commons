package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface PlaylistExportTool {

    <P extends MutablePlaylistNode<? extends AudioItem>> void exportPlaylistAsM3u(P playlistNode, Path path) throws ExportException;

    <P extends MutablePlaylistDirectory<? extends AudioItem>> void exportPlaylistDirectoryAsM3u(P playlistNode, Path path) throws ExportException;
}
