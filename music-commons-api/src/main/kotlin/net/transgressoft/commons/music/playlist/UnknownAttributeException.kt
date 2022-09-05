package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.query.EntityAttribute
import net.transgressoft.commons.query.QueryEntity

class UnknownAttributeException(entityAttribute: EntityAttribute<*>, queryEntityClass: Class<out QueryEntity>) : RuntimeException(
    String.format("Unknown attribute %s provided for %s", entityAttribute, queryEntityClass.name)
)