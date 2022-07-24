package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public interface MutablePlaylistNode<I extends AudioItem> extends QueryEntity {

    String getName();

    void setName(String name);

    MutablePlaylistDirectory<?> getAncestor();

    void setAncestor(MutablePlaylistDirectory<?> ancestor);

    boolean isDirectory();

    void addAudioItems(List<I> audioItems);

    int numberOfAudioItems();

    void removeAudioItems(Set<I> audioItems);

    ListIterator<I> audioItemsListIterator();

    void clearAudioItems();

    boolean isEmptyOfAudioItems();

    boolean audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate);

    boolean audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate);
}
