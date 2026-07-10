/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.persistence.music

import net.transgressoft.lirp.entity.ReactiveEntity
import net.transgressoft.lirp.entity.ReactiveEntityBase
import net.transgressoft.lirp.persistence.LirpRawConstructor
import net.transgressoft.lirp.persistence.json.LirpEntitySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Builds a sample entity instance through its co-located `_LirpRawConstructor`, resolved
 * reflectively via `Class.forName` on the entity's binary name plus the `_LirpRawConstructor`
 * suffix — the same convention lirp's SQL loader uses.
 *
 * This is the construction-free seam the persistence modules use to obtain the single sample
 * instance that `lirpSerializer(sample, module)` needs: it reaches the entity's `internal`
 * constructor without exposing a public factory or making the constructor public. It is shared
 * across the core and FX persistence tiers, so it is exposed as part of the persistence module's
 * surface rather than kept module-private.
 *
 * @param entityClassName binary name of the reactive entity (e.g. the mutable audio item impl)
 * @param params constructor-parameter-keyed values matching the entity's `_LirpRawConstructor`
 * @since 1.0
 */
@Suppress("UNCHECKED_CAST")
public fun <K : Comparable<K>, E : ReactiveEntity<K, E>> rawConstruct(
    entityClassName: String,
    params: Map<String, Any?>
): ReactiveEntityBase<K, E> {
    val constructor =
        Class.forName("${entityClassName}_LirpRawConstructor")
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as LirpRawConstructor<ReactiveEntityBase<K, E>>
    return constructor.construct(params)
}

/**
 * Builds lirp's reflective entity [KSerializer] from a [sample] instance, using the sample's
 * runtime class rather than a reified static type. The public `lirpSerializer(...)` helper is
 * `inline reified`, so it resolves the wrong (abstract) class when the sample is held behind a
 * generic supertype — as happens when the sample is built construction-free through
 * [rawConstruct]. Invoking the [LirpEntitySerializer] constructor directly with `sample::class`
 * sidesteps that, keeping the serializer bound to the concrete internal entity type.
 *
 * **Schema-change convention:** these serializers are hand-written rather than generated because
 * lirp's reflective SPI reads primary-constructor parameters and reactive delegates — plain
 * `@Serializable` fields are not persisted, so generated serializers cannot honour the contract
 * without exposing internal constructors. Therefore any new persisted field MUST ship with a
 * round-trip test covering both the field-absent case (older persisted data written before the
 * field existed) and the field-present case (new data), using [assertOptionalFieldRoundTrips][net.transgressoft.commons.music.testing.assertOptionalFieldRoundTrips]
 * from `music-commons-test`.
 *
 * @param sample a single entity instance whose concrete class the serializer mirrors
 * @param module contextual serializers for the entity's non-`@Serializable` nested types
 * @since 1.0
 */
@Suppress("UNCHECKED_CAST")
public fun <K : Comparable<K>, E : ReactiveEntity<K, E>> lirpSerializerFor(
    sample: ReactiveEntityBase<K, E>,
    module: SerializersModule = EmptySerializersModule()
): KSerializer<E> =
    LirpEntitySerializer(sample::class as kotlin.reflect.KClass<ReactiveEntityBase<K, E>>, sample, module) as KSerializer<E>