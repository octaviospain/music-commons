package net.transgressoft.commons.music.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.transgressoft.commons.event.EntityEvent
import net.transgressoft.commons.query.JsonFileRepository
import java.util.*
import java.util.concurrent.Flow
import java.util.function.BiFunction
import java.util.function.Predicate

@Serializable
abstract class AudioItemJsonFileRepositoryBase<I : AudioItemBase> protected constructor(
    @Transient private val audioReader: AudioItemMetadataReader<I>? = null,
    @Transient private val audioItemUpdateFunction: BiFunction<I, AudioItemMetadataChange, I>? = null
) : JsonFileRepository<I>(), AudioItemRepository<I> by AudioItemInMemoryRepositoryLogic(
    audioReader = audioReader!!, audioItemUpdateFunction = audioItemUpdateFunction!!) {

    override fun add(entity: I): Boolean {
        return super.add(entity)
    }

    override fun addOrReplace(entity: I): Boolean {
        return super.addOrReplace(entity)
    }

    override fun addOrReplaceAll(entities: Set<I>): Boolean {
        return super.addOrReplaceAll(entities)
    }

    override fun remove(entity: I): Boolean {
        return super.remove(entity)
    }

    override fun removeAll(entities: Set<I>): Boolean {
        return super.removeAll(entities)
    }

    override fun clear() {
        super.clear()
    }

    override fun contains(id: Int): Boolean {
        return super.contains(id)
    }

    override fun contains(predicate: Predicate<I>): Boolean {
        return super.contains(predicate)
    }

    override fun findById(id: Int): Optional<I> {
        return super.findById(id)
    }

    override fun findByUniqueId(uniqueId: String): Optional<I> {
        return super.findByUniqueId(uniqueId)
    }

    override fun iterator(): Iterator<I> {
        return super.iterator()
    }

    override fun search(predicate: Predicate<I>): List<I> {
        return super.search(predicate)
    }

    override fun size(): Int {
        return super.size()
    }

    override fun subscribe(subscriber: Flow.Subscriber<in EntityEvent<out I>>) {
        super.subscribe(subscriber)
    }

    override val isEmpty: Boolean
        get() = super.isEmpty
}