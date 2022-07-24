package net.transgressoft.commons.query;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class QueryPredicate<E extends QueryEntity> implements BooleanQueryTerm<E> {

    private final Set<BooleanQueryTerm<E>> terms;

    public static <X extends QueryEntity> QueryPredicate<X> of(BooleanQueryTerm<X> queryTerm) {
        return new QueryPredicate<>(queryTerm);
    }

    public QueryPredicate(BooleanQueryTerm<E> queryTerm) {
        terms = new HashSet<>();
        terms.add(queryTerm);
    }

    private QueryPredicate(Set<BooleanQueryTerm<E>> terms) {
        this.terms = terms;
    }

    @Override
    public Boolean apply(E queryEntity) {
        Objects.requireNonNull(queryEntity);
        return terms.stream().allMatch(queryTerm -> queryTerm.apply(queryEntity));
    }

    public QueryPredicate<E> and(BooleanQueryTerm<E> queryTerm) {
        Objects.requireNonNull(queryTerm);
        Set<BooleanQueryTerm<E>> set = new HashSet<>(terms);
        set.add(queryTerm);
        return new QueryPredicate<>(set);
    }
}
