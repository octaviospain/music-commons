package net.transgressoft.commons.query;

public interface QueryTerm<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> {

    boolean apply(V attributeValue);

    A attribute();

    Query<E, A, V> query();
}
