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

package net.transgressoft.commons.music.testing

import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Asserts that an optional field in a hand-written map serializer round-trips correctly
 * for both the field-present case (new persisted data) and the field-absent case (older persisted data
 * written before the field existed).
 *
 * The field-present branch encodes [presentMap] and asserts that the wire JSON contains [fieldName]
 * as a key in at least one entity object using a rooted JsonPath (`"$['<id>'].<fieldName>"`),
 * then verifies encode→decode→re-encode idempotency.
 *
 * The field-absent branch strips [fieldName] from every entity object in the encoded JSON using
 * type-safe kotlinx JSON operations, then decodes the stripped JSON and asserts that every entity is
 * still recovered (the decoded map retains all [presentMap] entries) — confirming that entities
 * persisted before the field was added are still loadable and none are silently dropped.
 *
 * All hand-written `Map<Int, T>` serializers in this library follow the convention that every new
 * persisted field ships with a test that calls this helper, covering both cases. See
 * `lirpSerializerFor` in `RawConstruction.kt` for the full schema-change convention.
 *
 * @param serializer the map serializer under test
 * @param presentMap a map of entities where [fieldName] is present in the wire JSON
 * @param fieldName the JSON key of the optional field to verify
 * @param json the [Json] instance to use for encoding and decoding; defaults to [Json.Default]
 */
fun <T> assertOptionalFieldRoundTrips(
    serializer: KSerializer<Map<Int, T>>,
    presentMap: Map<Int, T>,
    fieldName: String,
    json: Json = Json
) {
    require(presentMap.isNotEmpty()) { "presentMap must contain at least one entity to verify the field is on the wire" }

    // Field-present: encode, verify the key is on the wire in at least one entity object,
    // then confirm idempotency. The numeric entity id is a JSON object key, so it is addressed with
    // bracket notation under the JsonPath root to stay valid for arbitrary integer ids.
    val encoded = json.encodeToString(serializer, presentMap)
    val root = json.parseToJsonElement(encoded).jsonObject
    val anyEntityId = root.keys.first()
    encoded.shouldContainJsonKey("\$['$anyEntityId'].$fieldName")
    val decoded = json.decodeFromString(serializer, encoded)
    json.encodeToString(serializer, decoded) shouldBe encoded

    // Field-absent: strip the field from every entity object, decode, and confirm every entity is
    // still recovered — guarding against a deserializer that silently drops entities when the
    // optional field is missing.
    val stripped = stripField(json, encoded, fieldName)
    val fieldAbsentDecoded = json.decodeFromString(serializer, stripped)
    fieldAbsentDecoded.size shouldBe presentMap.size
}

/**
 * Strips [fieldName] from every entity object value in a JSON object whose top-level keys are
 * integer IDs. Uses type-safe kotlinx JSON element operations — no raw string manipulation.
 */
private fun stripField(json: Json, jsonString: String, fieldName: String): String {
    val root = json.parseToJsonElement(jsonString).jsonObject
    val stripped =
        buildJsonObject {
            for ((key, value) in root.entries) {
                val entityObject = value.jsonObject
                put(
                    key,
                    JsonObject(entityObject.filterKeys { it != fieldName })
                )
            }
        }
    return json.encodeToString(JsonObject.serializer(), stripped)
}