package net.transgressoft.commons.music.playlist

import com.google.common.base.Objects
import net.transgressoft.commons.music.audio.AudioItem

/**
 * Base implementation of a `PlaylistNode`. All attributes are mutable but intend to be thread-safe, <tt>id</tt> is inmutable.
 *
 * @param <I> The type of the entities listed in the playlist node.
</I> */
internal class MutablePlaylist<I : AudioItem> (id: Int, theName: String, audioItems: List<I>? = null) :
    ImmutablePlaylist<I>(id, theName, audioItems), MutableAudioPlaylist<I> {

    override var name: String
        set(value) {
            super.setNameInternal(value)
        }
        get() = super.name

    override fun addAudioItems(audioItems: Collection<I>) {
        super.addAll(audioItems)
    }

    override fun removeAudioItems(audioItems: Collection<I>) {
        super.removeAll(audioItems)
    }

    override fun clearAudioItems() {
        super.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as MutablePlaylist<*>
        return Objects.equal(name, that.name) && Objects.equal(id, that.id)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(name, id)
    }
}