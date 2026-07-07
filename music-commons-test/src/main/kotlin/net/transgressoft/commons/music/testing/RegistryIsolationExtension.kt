/******************************************************************************
 *     Copyright (C) 2025  Octavio Calleya Garcia                             *
 *                                                                            *
 *     This program is free software: you can redistribute it and/or modify   *
 *     it under the terms of the GNU General Public License as published by   *
 *     the Free Software Foundation, either version 3 of the License, or      *
 *     (at your option) any later version.                                    *
 *                                                                            *
 *     This program is distributed in the hope that it will be useful,        *
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of         *
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          *
 *     GNU General Public License for more details.                           *
 *                                                                            *
 *     You should have received a copy of the GNU General Public License      *
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>. *
 ******************************************************************************/

package net.transgressoft.commons.music.testing

import io.kotest.core.extensions.SpecExtension
import io.kotest.core.spec.Spec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Process-wide mutex shared by all specs that touch the [net.transgressoft.lirp.persistence.RegistryBase]
// singleton. Serializes every registry-touching spec against every other spec doing the same,
// preventing "A repository for X is already registered." collision errors under parallel execution.
internal val registryMutex = Mutex()

/**
 * Kotest [SpecExtension] that serializes specs touching the process-wide
 * `RegistryBase` / `LirpContext.default` registry against each other.
 *
 * `RegistryBase.registerRepository` binds entity-type → repository on the singleton
 * `LirpContext.default`. When two specs both call `registerRepository` for the same entity type
 * without a preceding `deregisterRepository`, the second call hits the
 * `"A repository for X is already registered."` check error. Under `LimitedConcurrency(4)` in
 * `music-commons-core`, this collision is an active test-parallelism hazard whenever more than
 * one registry-touching spec runs simultaneously.
 *
 * This extension wraps the full spec body in [registryMutex] so registry-touching specs are
 * serialized against each other while all other specs remain free to run in parallel.
 *
 * **Residual production risk:** the collision described above only arises when multiple library
 * instances share `LirpContext.default` in one process. Normal production usage is a single
 * library instance per process, so the collision is not a practical production concern. The
 * `music-commons-fx` module already runs its specs sequentially (JavaFX toolkit is process-global),
 * providing an independent layer of protection there; the active surface is `music-commons-core`
 * at `LimitedConcurrency(4)`.
 *
 * **Durable fix:** the root cause is that `RegistryBase.registerRepository` has no instance-scoped
 * registration path — it is hard-wired to `LirpContext.default`. An instance-scoped registration
 * API in lirp (so registries can bind to an application-owned context rather than the process-wide
 * default) would remove the need for this serialization entirely. That enhancement is tracked as
 * an upstream lirp improvement.
 *
 * Register per-spec via the [registryIsolation] convenience factory:
 *
 *     class FooPlaylistTest : StringSpec({
 *         registryIsolation()
 *
 *         beforeEach {
 *             RegistryBase.deregisterRepository(AudioItem::class.java)
 *             RegistryBase.registerRepository(AudioItem::class.java, audioItemRepository)
 *         }
 *     })
 *
 * The per-spec `deregisterRepository`/`registerRepository` bracketing in `beforeEach`/`afterEach`
 * blocks should be kept — entity types differ per spec and the extension's job is serialization
 * only, not per-spec state setup.
 *
 * @see ReactiveScopeSerialization for the analogous extension serializing [net.transgressoft.lirp.event.ReactiveScope]-touching specs.
 */
object RegistryIsolationExtension : SpecExtension {

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        registryMutex.withLock { execute(spec) }
    }
}

/**
 * Convenience factory that registers [RegistryIsolationExtension] on the spec.
 *
 * Call once at the top of the [Spec] body, alongside other shared extensions:
 *
 *     class FooPlaylistTest : StringSpec({
 *         registryIsolation()
 *         val reactive = reactiveScope()
 *
 *         beforeEach {
 *             RegistryBase.deregisterRepository(AudioItem::class.java)
 *             RegistryBase.registerRepository(AudioItem::class.java, myRepo)
 *         }
 *     })
 */
fun Spec.registryIsolation() {
    extension(RegistryIsolationExtension)
}