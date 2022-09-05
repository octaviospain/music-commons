package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.EntityAttribute

enum class ArtistAttribute : EntityAttribute<Artist> {
    ARTIST, ALBUM_ARTIST;

    fun <E : AudioItem> nameEqualsTo(name: String?, ignoreCase: Boolean): BooleanQueryTerm<E> {
        return BooleanQueryTerm { audioItem -> name?.equals(audioItem.getAttribute(this@ArtistAttribute).name(), ignoreCase) ?: false }
    }
}