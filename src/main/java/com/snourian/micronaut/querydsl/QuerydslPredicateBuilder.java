package com.snourian.micronaut.querydsl;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.util.StringUtils;
import com.snourian.micronaut.querydsl.expression.ExpressionEntries;
import com.snourian.micronaut.querydsl.expression.ExpressionFactory;
import com.snourian.micronaut.querydsl.expression.ExpressionType;
import com.snourian.micronaut.querydsl.expression.PredicatePath;
import com.snourian.micronaut.querydsl.expression.operator.OpType;

import java.util.*;
import java.util.stream.Stream;

public class QuerydslPredicateBuilder<T> {

    // storing join expressions. Will be used in QuerydslPredicateExecutor.createQuery()
    private final Map<String, JoinsData> joins = new LinkedHashMap<>();
    // a cache for parent paths in order to avoid creating them multiple times
    private final Map<String, Path<?>> pathCache = new HashMap<>();
    // storing predicates to be used in query after 'where' clause
    private final List<BooleanOperation> predicates = new ArrayList<>();
    private final Class<? extends T> entityType;
    private final QueryParameters params;

    public QuerydslPredicateBuilder(Class<? extends T> entityType, QueryParameters params) {
        this.entityType = entityType;
        this.params = params;
    }

    public Predicate toPredicate() {
        // extract property values, types, predicate operation and their path from the root entity from http params
        ExpressionEntries exprMetadata = ExpressionFactory.createFromParams(entityType, params.getParameters());
        final String parentName = StringUtils.uncapitalize(entityType.getSimpleName());
        final SimplePath<T> parentPath = Expressions.path(entityType, parentName);
        pathCache.put(parentName, parentPath);
        exprMetadata.getPredicates()
                .forEach(predicatePath -> {
                    Expression<?>[] exprs;
                    // First step -> creating constants for building our query
                    // for some operations we need to put our constants in Expression.list(). e.g. 'IN'
                    if (predicatePath.getOp().getOpType() == OpType.LIST) {
                        exprs = new Expression<?>[2];
                        exprs[1] = Expressions.list(
                                Stream.of(predicatePath.getValues())
                                        .map(Expressions::constant)
                                        .distinct()
                                        .toArray(Expression<?>[]::new));
                    } else {
                        exprs = new Expression<?>[predicatePath.getValues().length + 1];
                        for (int i = 0; i < predicatePath.getValues().length; i++)
                            exprs[i + 1] = Expressions.constant(predicatePath.getValues()[i]);
                    }
                    Path<?> lastPath = parentPath;
                    // building the predicate according to the path
                    // example: department[Root Entity].employee[Next Entity].score[Simple Property]
                    for (PredicatePath path : predicatePath.getPath()) {
                        // If the path data exists, retrieve it from the cache. If not, create a path and PathMetadata
                        final String alias = path.getFullPath();
                        Path<?> prev = pathCache.get(path.getPath());
                        Path<?> next;
                        if (path.getRelationType() == PredicatePath.RelationType.Collection) {
                            next = pathCache.computeIfAbsent(alias, key -> Expressions.path(path.getType(), key));
                            joins.computeIfAbsent(alias,
                                    fp -> JoinsData.of(Expressions.collectionPath(path.getType(), Expressions.path(path.getType(), path.getProperty()).getClass(), PathMetadataFactory.forProperty(prev, path.getProperty())), next, path.getRelationType()));
                        } else if (path.getRelationType() == PredicatePath.RelationType.Single) {
                            next = pathCache.computeIfAbsent(alias, key -> Expressions.path(path.getType(), key));
                            joins.computeIfAbsent(alias,
                                    fp -> JoinsData.of(new PathBuilder<Object>(path.getType(), PathMetadataFactory.forProperty(prev, path.getProperty())), next, path.getRelationType()));
                        } else
                            next = pathCache.computeIfAbsent(alias, key -> Expressions.path(path.getType(), PathMetadataFactory.forProperty(prev, path.getProperty())));
                        lastPath = next;
                        // TODO: 11/3/2021 Support Map [Expressions.mapPath()] and ElementCollection relations
                    }
                    // the final element in path (and the final element is a simple property, like String)
                    // this is the property that we want to create predicate for it. (e.g. score = 50)
                    exprs[0] = Expressions.path(predicatePath.getPropertyType(), lastPath, predicatePath.getProperty());
                    // creating the final predicate with the specified operator [e.g. score eq(50)]
                    predicates.add(Expressions.predicate(predicatePath.getOp().getOperator(), exprs));
                });
        BooleanExpression[] booleanExprs = predicates.toArray(new BooleanOperation[0]);
        // allOf: AND all the predicates | anyOf: OR all the predicates
        if (exprMetadata.getType() == ExpressionType.ANYOF)
            return Expressions.anyOf(booleanExprs);
        else
            return Expressions.allOf(booleanExprs);
    }

    public Collection<JoinsData> getJoins() {
        return joins.values();
    }

    public static class JoinsData {
        private final Expression<?> expr;
        private final Path<?> alias;
        private final PredicatePath.RelationType relationType;

        private JoinsData(Expression<?> expr, Path<?> alias, PredicatePath.RelationType relationType) {
            this.expr = expr;
            this.alias = alias;
            this.relationType = relationType;
        }

        private static JoinsData of(Expression<?> expr, Path<?> alias, PredicatePath.RelationType relationType) {
            return new JoinsData(expr, alias, relationType);
        }

        public Expression<?> getExpr() {
            return expr;
        }

        public Path<?> getAlias() {
            return alias;
        }

        public PredicatePath.RelationType getRelationType() {
            return relationType;
        }
    }
}
