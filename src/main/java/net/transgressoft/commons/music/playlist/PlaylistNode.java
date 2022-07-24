package net.transgressoft.commons.music.playlist;

import com.google.common.collect.UnmodifiableListIterator;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

import java.util.List;
import java.util.Set;

public interface PlaylistNode<I extends AudioItem> extends QueryEntity {

    interface Builder<P extends PlaylistNode<? extends AudioItem>> {

        P build();
    }

    String getName();

    void setName(String name);

    PlaylistNode<I> getAncestor();

    void setAncestor(PlaylistNode<I> ancestor);

    boolean isDirectory();

    void addPlaylist(PlaylistNode<I> playlist);

    void removePlaylist(PlaylistNode<I> playlist);

    UnmodifiableListIterator<PlaylistNode<I>> descendantPlaylistsIterator();

    void clearDescendantPlaylists();

    boolean isEmptyOfPlaylists();

    void addAudioItems(List<I> audioItems);

    void removeAudioItems(Set<I> audioItems);

    void removeAudioItems(I... audioItems);

    UnmodifiableListIterator<I> audioItemsListIterator();

    void clearAudioItems();

    void clearAudioItemsFromPlaylists();

    boolean isEmptyOfAudioItems();

    boolean audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate);

    boolean audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate);
}
