package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.EntityAttribute
import java.nio.file.Paths
import java.time.Duration

class SimpleAudioItemAttributes (private val attributes: MutableMap<EntityAttribute<*>, Any> = emptyAttributes()): AudioItemAttributes {

    override fun <V : Any> get(attribute: EntityAttribute<V>): V {
        return attributes[attribute] as V
    }

    override fun <V : Any> set(attribute: EntityAttribute<V>, value: V) {
        attributes[attribute] = value
    }

    override fun <V : Any> putIfAbsent(attribute: EntityAttribute<V>, value: V) {
        attributes.putIfAbsent(attribute, value)
    }

    override fun <V : Any> modifiedCopy(a1: EntityAttribute<V>, v1: V): AudioItemAttributes {
        val map = HashMap(attributes);
        map[a1] = v1
        return SimpleAudioItemAttributes(map)
    }
}

private fun emptyAttributes(): MutableMap<EntityAttribute<*>, Any> {
    val map = HashMap<EntityAttribute<*>, Any>()
    map[AudioItemPathAttribute.PATH] = Paths.get("")
    map[AudioItemDurationAttribute.DURATION] = Duration.ofSeconds(0)
    map[AudioItemStringAttribute.TITLE] = ""
    map[AudioItemStringAttribute.GENRE_NAME] = Genre.UNDEFINED.name
    map[AudioItemStringAttribute.COMMENTS] = ""
    map[AudioItemShortAttribute.TRACK_NUMBER] = (-1).toShort()
    map[AudioItemShortAttribute.DISC_NUMBER] = (-1).toShort()
    map[AudioItemFloatAttribute.BPM] = -1f
    map[AudioItemIntegerAttribute.BITRATE] = -1
    map[AudioItemStringAttribute.ENCODER] = ""
    map[AudioItemStringAttribute.ENCODING] = ""
    return map
}

val emptyAttributes = SimpleAudioItemAttributes(emptyAttributes())