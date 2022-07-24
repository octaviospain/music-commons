package net.transgressoft.commons.music.playlist.attribute;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.playlist.PlaylistNode;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.attribute.EntityAttribute;

import java.util.Objects;

public enum PlaylistNodeAttribute implements EntityAttribute<PlaylistNode<AudioItem>> {

    SELF,
    ANCESTOR;

    public BooleanQueryTerm<PlaylistNode<AudioItem>> hasNoAncestor() {
        return queryEntity -> Objects.equals(queryEntity.getAttribute(this).getAncestor(), AudioPlaylistDirectory.ROOT);
    }

    public BooleanQueryTerm<PlaylistNode<AudioItem>> isDirectory() {
        return queryEntity -> queryEntity.getAttribute(this).isDirectory();
    }

    public BooleanQueryTerm<PlaylistNode<AudioItem>> isNotDirectory() {
        return queryEntity -> ! queryEntity.getAttribute(this).isDirectory();
    }

    public BooleanQueryTerm<PlaylistNode<AudioItem>> audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAnyMatch(queryPredicate);
    }

    public BooleanQueryTerm<PlaylistNode<AudioItem>> audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAllMatch(queryPredicate);
    }
}
