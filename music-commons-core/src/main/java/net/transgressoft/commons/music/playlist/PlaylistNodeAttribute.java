package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;

import java.util.Objects;

public enum PlaylistNodeAttribute implements EntityAttribute<MutablePlaylistNode<AudioItem>> {

    SELF,
    ANCESTOR;

    public BooleanQueryTerm<MutablePlaylistNode<AudioItem>> hasNoAncestor() {
        return queryEntity -> Objects.equals(queryEntity.getAttribute(this).getAncestor(), RootAudioPlaylistNode.INSTANCE);
    }

    public BooleanQueryTerm<MutablePlaylistNode<AudioItem>> isDirectory() {
        return queryEntity -> queryEntity.getAttribute(this).isDirectory();
    }

    public BooleanQueryTerm<MutablePlaylistNode<AudioItem>> isNotDirectory() {
        return queryEntity -> ! queryEntity.getAttribute(this).isDirectory();
    }

    public BooleanQueryTerm<MutablePlaylistNode<AudioItem>> audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAnyMatch(queryPredicate);
    }

    public BooleanQueryTerm<MutablePlaylistNode<AudioItem>> audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAllMatch(queryPredicate);
    }

    public BooleanQueryTerm<MutablePlaylistNode<AudioItem>> audioItemsAllMatchingTitle(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAllMatch(queryPredicate);
    }
}
