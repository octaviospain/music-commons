package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.EntityAttribute

enum class AlbumAttribute : EntityAttribute<Album> {
    ALBUM;

    fun <E : AudioItem> nameEqualsTo(name: String?, ignoreCase: Boolean): BooleanQueryTerm<E> {
        return BooleanQueryTerm { audioItem -> name?.equals(audioItem.getAttribute(this@AlbumAttribute).name(), ignoreCase) ?: false }
    }
}