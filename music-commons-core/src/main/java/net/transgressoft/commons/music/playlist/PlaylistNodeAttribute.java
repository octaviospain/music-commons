package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.EntityAttribute;

public enum PlaylistNodeAttribute implements EntityAttribute<MutablePlaylist<AudioItem>> {

    SELF;

    public BooleanQueryTerm<AudioPlaylist<AudioItem>> isDirectory() {
        return queryEntity -> queryEntity.getAttribute(this).isDirectory();
    }

    public BooleanQueryTerm<AudioPlaylist<AudioItem>> isNotDirectory() {
        return queryEntity -> ! queryEntity.getAttribute(this).isDirectory();
    }

    public BooleanQueryTerm<AudioPlaylist<AudioItem>> audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAnyMatch(queryPredicate);
    }

    public BooleanQueryTerm<AudioPlaylist<AudioItem>> audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate) {
        return queryEntity -> queryEntity.getAttribute(SELF).audioItemsAllMatch(queryPredicate);
    }
}
