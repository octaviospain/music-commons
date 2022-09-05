package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.EntityAttribute

interface AudioItemAttributes {
    operator fun <V : Any> get(attribute: EntityAttribute<V>): V
    operator fun <V : Any> set(attribute: EntityAttribute<V>, value: V)
    fun <V : Any> putIfAbsent(attribute: EntityAttribute<V>, value: V)
    fun <V : Any> modifiedCopy(a1: EntityAttribute<V>, v1: V): AudioItemAttributes
}