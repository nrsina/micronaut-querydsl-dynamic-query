package com.snourian.micronaut.querydsl;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

// A Helper class to convert Micronaut Pageable and Sort to JPAQuery of QueryDSL
// Ideas taken from Spring Data JPA support for QueryDSL:
// https://github.com/spring-projects/spring-data-jpa/blob/main/src/main/java/org/springframework/data/jpa/repository/support/Querydsl.java
public class QuerydslHelper {

    private QuerydslHelper() {
    }

    static <T> JPAQuery<T> applyPagination(JPAQuery<T> query, Pageable pageable, EntityPath<T> path) {
        if (pageable.isUnpaged())
            return query;
        query.offset(pageable.getOffset());
        query.limit(pageable.getSize());
        return applySorting(query, pageable.getSort(), path);
    }

    static <T> JPAQuery<T> applySorting(JPAQuery<T> query, Sort sort, EntityPath<T> path) {
        if (!sort.isSorted())
            return query;
        sort.getOrderBy()
                .forEach(order -> query.orderBy(toOrderSpecifier(order, path)));
        return query;
    }

    static <T> OrderSpecifier<?> toOrderSpecifier(Sort.Order sortOrder, EntityPath<T> path) {
        final Order order = sortOrder.isAscending() ? Order.ASC : Order.DESC;
        return new OrderSpecifier(order, Expressions.path(path.getType(), path, sortOrder.getProperty()),
                OrderSpecifier.NullHandling.Default);
    }
}
