package net.transgressoft.commons.query;

import net.transgressoft.commons.query.attribute.EntityAttribute;

public interface QueryEntity extends Comparable<QueryEntity> {

    int id();

    String getUniqueId();

    <A extends EntityAttribute<V>, V> V getAttribute(A attribute);
}
