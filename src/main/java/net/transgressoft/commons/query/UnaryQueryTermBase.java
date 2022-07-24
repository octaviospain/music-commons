package net.transgressoft.commons.query;

public abstract class UnaryQueryTermBase<E extends QueryEntity<A>, A extends EntityAttribute<?>, V> implements QueryTerm<E, A, V> {

    private final A attribute;

    public UnaryQueryTermBase(A attribute) {
        this.attribute = attribute;
    }

    @Override
    public A attribute() {
        return attribute;
    }

    @Override
    public Query<E, A, V> query() {
        return null;
    }
}
