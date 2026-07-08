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

package net.transgressoft.commons.music

import net.transgressoft.lirp.persistence.LirpContext
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository

/**
 * Registers [repository] as the owner of [entityClass] in `LirpContext.default`, failing fast
 * if the slot is already occupied by a live library instance.
 *
 * Only one live library instance per entity type is supported in this JVM. If an existing
 * instance already holds the slot, an [IllegalStateException] is thrown with a message naming the
 * entity type and prescribing the fix. The caller must close the existing library before
 * constructing a new one.
 *
 * Note: the occupancy read and the subsequent register are not performed atomically. If two threads
 * race and both pass the occupancy check, the second `registerRepository` call will still throw
 * `IllegalStateException` via lirp's own internal guard, so no silent corruption can occur — the
 * loser of the race receives an `IllegalStateException` from either this check or from lirp.
 *
 * Unlike the JavaFX facade's guard, which tolerates a re-registration of the same repository
 * reference (its builder pre-registers before `init` runs), the Core facade has no pre-`init`
 * registration path: any occupied slot means a distinct live instance, so this check fails fast on
 * an occupied slot without a same-reference exemption.
 */
internal fun guardedRegister(entityClass: Class<*>, repository: Repository<*, *>) {
    check(LirpContext.default.registryFor(entityClass) == null) {
        "A live music library for ${entityClass.simpleName} already exists in this JVM. " +
            "Only one live library instance is supported at a time; " +
            "call close() on the existing library before constructing another."
    }
    RegistryBase.registerRepository(entityClass, repository)
}

/**
 * Deregisters [entityClass] from `LirpContext.default` only if [repository] is its current
 * registered owner.
 *
 * This identity check prevents a `close()` call from detaching a slot that belongs to a different
 * library instance — for example, when a failed or concurrent construction left another instance
 * holding the slot. If the slot is owned by a different repository, this call is a no-op.
 */
internal fun conditionalDeregister(entityClass: Class<*>, repository: Repository<*, *>) {
    if (LirpContext.default.registryFor(entityClass) === repository) {
        RegistryBase.deregisterRepository(entityClass)
    }
}