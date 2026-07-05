package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestAttributes
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.util.function.Consumer

/**
 * Java-friendly factory methods for observable audio item test fixtures.
 *
 * This object delegates to the Kotlin fxAudioItem generator so Java tests can reuse the
 * same mocked [ObservableAudioItem] construction path as Kotlin tests.
 */
object FxAudioItemTestFactory {

    /**
     * Creates an observable audio item with caller-supplied test attributes.
     *
     * @param attributes action that mutates the generated attributes before the item is built
     * @return observable audio item backed by music-commons-fx-test mocks
     */
    @JvmStatic
    fun createFxAudioItem(attributes: Consumer<AudioItemTestAttributes>): ObservableAudioItem =
        Arb.fxAudioItem(attributes::accept).next()
}