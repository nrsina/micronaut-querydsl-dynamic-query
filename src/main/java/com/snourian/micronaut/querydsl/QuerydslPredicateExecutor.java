package com.snourian.micronaut.querydsl;

import com.querydsl.core.types.CollectionExpression;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.util.StringUtils;
import com.querydsl.jpa.impl.JPAQuery;
import com.snourian.micronaut.querydsl.expression.PredicatePath;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface QuerydslPredicateExecutor<T> {

    Logger logger = LoggerFactory.getLogger(QuerydslPredicateExecutor.class);

    default Optional<T> findOne(QueryParameters params) {
        return findOne(params, Collections.emptyMap());
    }

    default Optional<T> findOne(Predicate predicate) {
        return findOne(predicate, Collections.emptyMap());
    }

    default Optional<T> findOne(Predicate predicate, Map<String, Object> hints) {
        JPAQuery<T> query = createQuery(predicate, hints);
        return Optional.ofNullable(query.fetchOne());
    }

    default Optional<T> findOne(QueryParameters params, Map<String, Object> hints) {
        JPAQuery<T> query = createQuery(params, hints);
        return Optional.ofNullable(query.fetchOne());
    }

    default List<T> findAll(QueryParameters params) {
        return findAll(params, Collections.emptyMap());
    }

    default List<T> findAll(Predicate predicate) {
        return findAll(predicate, Collections.emptyMap());
    }

    default List<T> findAll(Predicate predicate, Map<String, Object> hints) {
        JPAQuery<T> query = createQuery(predicate, hints);
        return query.fetch();
    }

    default List<T> findAll(QueryParameters params, Map<String, Object> hints) {
        JPAQuery<T> query = createQuery(params, hints);
        return query.fetch();
    }

    default List<T> findAll(QueryParameters params, Sort sort) {
        return findAll(params, sort, Collections.emptyMap());
    }

    default List<T> findAll(Predicate predicate, Sort sort) {
        return findAll(predicate, sort, Collections.emptyMap());
    }

    default List<T> findAll(QueryParameters params, Sort sort, Map<String, Object> hints) {
        JPAQuery<T> query = QuerydslHelper.applySorting(createQuery(params, hints), sort, getEntityPath());
        return query.fetch();
    }

    default List<T> findAll(Predicate predicate, Sort sort, Map<String, Object> hints) {
        JPAQuery<T> query = QuerydslHelper.applySorting(createQuery(predicate, hints), sort, getEntityPath());
        return query.fetch();
    }

    default Page<T> findAll(QueryParameters params, Pageable pageable) {
        return findAll(params, pageable, Collections.emptyMap());
    }

    default Page<T> findAll(Predicate predicate, Pageable pageable) {
        return findAll(predicate, pageable, Collections.emptyMap());
    }

    default Page<T> findAll(QueryParameters params, Pageable pageable, Map<String, Object> hints) {
        JPAQuery<T> query = createQuery(params, hints);
        JPAQuery<T> paginatedQuery = QuerydslHelper.applyPagination(createQuery(params, hints), pageable, getEntityPath());
        return Page.of(paginatedQuery.fetch(), pageable, query.fetchCount());
    }

    default Page<T> findAll(Predicate predicate, Pageable pageable, Map<String, Object> hints) {
        JPAQuery<T> query = createQuery(predicate, hints);
        JPAQuery<T> paginatedQuery = QuerydslHelper.applyPagination(createQuery(predicate, hints), pageable, getEntityPath());
        return Page.of(paginatedQuery.fetch(), pageable, query.fetchCount());
    }

    private JPAQuery<T> createQuery(QueryParameters params, Map<String, Object> hints) {
        JPAQuery<T> query = initJPAQuery(hints);
        QuerydslPredicateBuilder<T> builder = new QuerydslPredicateBuilder<>(getEntityPath().getType(), params);
        Predicate predicate = builder.toPredicate();
        for (QuerydslPredicateBuilder.JoinsData joins : builder.getJoins()) {
            if (joins.getRelationType() == PredicatePath.RelationType.Collection)
                query = query.join((CollectionExpression) joins.getExpr(), joins.getAlias());
            else if (joins.getRelationType() == PredicatePath.RelationType.Single)
                query = query.join((EntityPath) joins.getExpr(), joins.getAlias());
            // Implement Map relation join
        }
        customize(predicate);
        query = query.where(predicate);
        logger.info(query.toString());
        return query;
    }

    private JPAQuery<T> createQuery(Predicate predicate, Map<String, Object> hints) {
        JPAQuery<T> query = initJPAQuery(hints);
        customize(predicate);
        if (predicate != null)
            query = query.where(predicate);
        for (Map.Entry<String, Object> hint : hints.entrySet()) {
            query.setHint(hint.getKey(), hint.getValue());
        }
        return query;
    }

    private JPAQuery<T> initJPAQuery(Map<String, Object> hints) {
        JPAQuery<T> query = new JPAQuery<>(getEntityManager());
        EntityPath<T> entityPath = getEntityPath();
        query.select(entityPath).from(entityPath);
        for (Map.Entry<String, Object> hint : hints.entrySet()) {
            query.setHint(hint.getKey(), hint.getValue());
        }
        return query;
    }

    default Predicate customize(Predicate predicate) {
        return predicate;
    }

    EntityManager getEntityManager();

    Class<T> getEntityClass();

    private EntityPath<T> getEntityPath() {
        Class<T> clazz = getEntityClass();
        return new PathBuilder<>(clazz, StringUtils.uncapitalize(clazz.getSimpleName()));
    }
}
