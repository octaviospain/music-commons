package net.transgressoft.commons.query;

public abstract class BinaryQueryTermBase<E extends QueryEntity<A>, A extends EntityAttribute<?>, V, O>
        extends UnaryQueryTermBase<E, A, V>
        implements BinaryQueryTerm<E, A, V, O> {

    private final O operand;

    public BinaryQueryTermBase(A attribute, O operand) {
        super(attribute);
        this.operand = operand;
    }

    @Override
    public O operand() {
        return operand;
    }
}
