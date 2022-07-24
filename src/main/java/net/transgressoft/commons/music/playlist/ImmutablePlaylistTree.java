package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Octavio Calleya
 */
public class ImmutablePlaylistTree extends PlaylistTreeBase<AudioItem> {

    public ImmutablePlaylistTree(String name, Set<PlaylistTree<AudioItem>> includedPlaylistTrees, Set<AudioPlaylist<AudioItem>> audioPlaylists) {
        super(name, includedPlaylistTrees, audioPlaylists);
    }

    public ImmutablePlaylistTree(String name) {
        super(name, Collections.emptySet(), Collections.emptySet());
    }

    @Override
    public PlaylistItem<AudioItem> name(String name) {
        return new ImmutablePlaylistTree(name, includedPlaylistTrees(), audioPlaylists());
    }

    @Override
    public PlaylistTree<AudioItem> addPlaylist(AudioPlaylist<AudioItem> playlist) {
        Set<AudioPlaylist<AudioItem>> set = new HashSet<>(audioPlaylists());
        set.add(playlist);
        return new ImmutablePlaylistTree(name(), includedPlaylistTrees(), set);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <P extends PlaylistItem<AudioItem>> P removeAudioItems(Set<AudioItem> audioItems) {
        Set<PlaylistTree<AudioItem>> newIncludedPlaylistTrees = new HashSet<>();
        for (PlaylistTree<AudioItem> subPlaylistTree : includedPlaylistTrees()) {
            newIncludedPlaylistTrees.add(subPlaylistTree.removeAudioItems(audioItems));
        }

        Set<AudioPlaylist<AudioItem>> newAudioPlaylists = new HashSet<>();
        for (AudioPlaylist<AudioItem> audioPlaylist : audioPlaylists()) {
            newAudioPlaylists.add(audioPlaylist.removeAudioItems(audioItems));
        }
        return (P) new ImmutablePlaylistTree(name(), newIncludedPlaylistTrees, newAudioPlaylists);
    }

    @Override
    public PlaylistTree<AudioItem> removeAudioPlaylist(AudioPlaylist<AudioItem> playlist) {
        Set<AudioPlaylist<AudioItem>> set = new HashSet<>(audioPlaylists());
        set.remove(playlist);
        return new ImmutablePlaylistTree(name(), includedPlaylistTrees(), set);
    }

    @Override
    public PlaylistTree<AudioItem> addPlaylistTree(PlaylistTree<AudioItem> playlistTree) {
        Set<PlaylistTree<AudioItem>> set = new HashSet<>(includedPlaylistTrees());
        set.add(playlistTree);
        return new ImmutablePlaylistTree(name(), set, audioPlaylists());
    }

    @Override
    public PlaylistTree<AudioItem> removePlaylistTree(PlaylistTree<AudioItem> playlistTree) {
        Set<PlaylistTree<AudioItem>> set = new HashSet<>(includedPlaylistTrees());
        set.remove(playlistTree);
        return new ImmutablePlaylistTree(name(), set, audioPlaylists());
    }

    @Override
    public PlaylistTree<AudioItem> clearIncludedPlaylistTrees() {
        return new ImmutablePlaylistTree(name(), Collections.emptySet(), audioPlaylists());
    }

    @Override
    public PlaylistTree<AudioItem> clearPlaylists() {
        return new ImmutablePlaylistTree(name(), includedPlaylistTrees(), Collections.emptySet());
    }
}
