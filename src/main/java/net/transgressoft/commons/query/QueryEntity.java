package net.transgressoft.commons.query;

public interface QueryEntity {

    int id();

    String uniqueId();

    <A extends EntityAttribute<V>, V> V getAttribute(A attribute);
}
