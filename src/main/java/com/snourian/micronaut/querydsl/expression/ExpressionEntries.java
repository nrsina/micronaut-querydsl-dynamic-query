package com.snourian.micronaut.querydsl.expression;

import java.util.ArrayList;
import java.util.List;

public class ExpressionEntries {

    private final ExpressionType type;
    private final List<PredicateEntry> predicates;

    public ExpressionEntries(ExpressionType type, List<PredicateEntry> predicates) {
        this.type = type;
        this.predicates = predicates;
    }

    public ExpressionEntries() {
        this.type = ExpressionType.ALLOF;
        this.predicates = new ArrayList<>();
    }

    public ExpressionType getType() {
        return type;
    }

    public List<PredicateEntry> getPredicates() {
        return predicates;
    }
}
