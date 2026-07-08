/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.fx.music

import net.transgressoft.lirp.persistence.LirpContext
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository

/**
 * Registers [repository] as the active repository for [entityClass] in the process-wide
 * [LirpContext], enforcing the single-live-instance contract for JavaFX facades.
 *
 * If the slot is already occupied by a **different** repository, an [IllegalStateException] is
 * thrown immediately, leaving the existing registration intact. If the slot is already occupied
 * by the **same** [repository] reference (i.e. the builder legitimately pre-registered it before
 * the facade's `init` block runs), the call is a no-op — registration is not repeated.
 *
 * Only one live JavaFX facade per entity type is supported within a single JVM process. To
 * construct a replacement facade, close the existing one first — its `close()` method
 * conditionally frees the slot via [conditionalDeregister].
 *
 * @param entityClass the entity type key used in [LirpContext]
 * @param repository the repository to register
 * @throws IllegalStateException if a different live repository already occupies the slot
 */
internal fun guardedRegister(entityClass: Class<*>, repository: Repository<*, *>) {
    val existing = LirpContext.default.registryFor(entityClass)
    check(existing == null || existing === repository) {
        "A live music library for ${entityClass.simpleName} already exists in this JVM. " +
            "Only one live library instance is supported; close() the existing one before constructing another."
    }
    if (existing === repository) return
    RegistryBase.registerRepository(entityClass, repository)
}

/**
 * Deregisters [repository] from the [LirpContext] slot for [entityClass] only if this facade
 * still owns the slot.
 *
 * The ownership check (`registryFor(entityClass) === repository`) ensures that closing one
 * facade never evicts a slot that belongs to a different facade, which could otherwise
 * corrupt aggregate resolution for the live facade.
 *
 * @param entityClass the entity type key used in [LirpContext]
 * @param repository the repository whose ownership is verified before deregistering
 */
internal fun conditionalDeregister(entityClass: Class<*>, repository: Repository<*, *>) {
    if (LirpContext.default.registryFor(entityClass) === repository) {
        RegistryBase.deregisterRepository(entityClass)
    }
}