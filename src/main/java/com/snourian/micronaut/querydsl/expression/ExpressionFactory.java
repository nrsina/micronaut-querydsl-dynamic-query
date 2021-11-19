package com.snourian.micronaut.querydsl.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snourian.micronaut.querydsl.expression.operator.PredicateOperator;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.util.StringUtils;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionFactory {

    public static ExpressionEntries createFromParams(Class<?> entity, Map<String, String> params) {
        if (params.isEmpty())
            return new ExpressionEntries();
        Map<String, String> searchParams = new HashMap<>(params);
        final ExpressionType exprType = extractExpressionType(searchParams);
        List<PredicateEntry> predicates = searchParams.entrySet()
                .stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue()))
                .map(entry -> create(entity, entry.getKey(), entry.getValue()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return new ExpressionEntries(exprType, predicates);
    }

    private static Optional<PredicateEntry> create(Class<?> entity, String key, String value) {
        Iterator<String> keyParts = StringUtils.splitOmitEmptyStringsIterator(key, '.');
        if (!keyParts.hasNext())
            return Optional.empty();
        // get entities BeanIntrospection which is built during compile time.
        // Will be used to extract entity's property types without using Reflection
        /* TODO Optimization idea: only call getIntrospection once for the parent entity since most of the properties
            are Basic types and we don't need to call getIntrospection for each of them, and only call getIntrospection
            for related entities to get their property types */
        BeanIntrospection<?> beanIntro = BeanIntrospection.getIntrospection(entity);
        final List<PredicatePath> paths = new ArrayList<>();
        String property = null;
        Class<?> propertyType = null;
        final StringBuilder pathBuilder =
                new StringBuilder(com.querydsl.core.util.StringUtils.uncapitalize(entity.getSimpleName()));
        while (keyParts.hasNext()) {
            String part = keyParts.next();
            Optional<? extends BeanProperty<?, Object>> prop = beanIntro.getProperty(part);
            if (prop.isEmpty())
                return Optional.empty();
            propertyType = prop.get().getType();
            // If the property is a collection, it should be a OneToMany or ManyToMany relation and the generic value must be an entity
            // To support @ElementCollection in the future, this part should be refactored. Any contribution would be greatly appreciated.
            if (Iterable.class.isAssignableFrom(propertyType)) {
                // get the generic type of collection from BeanProperty
                Class<?> genericType = Arrays.stream(prop.get().asArgument().getTypeParameters())
                        .findAny()
                        .orElseThrow(() -> new RuntimeException("Cannot get generic type of collection '" + prop.get().getName() + "'"))
                        .getType();
                if (classHasAnnotation(genericType, Entity.class)) {
                    beanIntro = updatePathAndGetNextBeanIntro(paths, genericType, part, pathBuilder, PredicatePath.RelationType.Collection);
                } else // TODO: 11/2/2021 Support @ElementCollection and Map fields
                    throw new RuntimeException("Non-Entity collection properties are not yet supported! field:" + part);
                // If the property is an Entity. Which means it's a OneToOne or ManyToOne relation type.
            } else if (classHasAnnotation(propertyType, Entity.class))
                beanIntro = updatePathAndGetNextBeanIntro(paths, propertyType, part, pathBuilder, PredicatePath.RelationType.Single);
            else if (classHasAnnotation(propertyType, Embeddable.class))
                beanIntro = updatePathAndGetNextBeanIntro(paths, propertyType, part, pathBuilder, PredicatePath.RelationType.Embedded);
            else { // If it's a basic property, e.g. String, Long, ...
                final StringBuilder sb = new StringBuilder();
                sb.append(part);
                keyParts.forEachRemaining(str -> sb.append('.').append(str));
                property = sb.toString();
            }
        }
        if (property == null)
            throw new RuntimeException("The final property must be a simple field: " + key);
        final OperatorAndValues opAndVals = extractOpAndValues(propertyType, value);
        return Optional.of(new PredicateEntry(opAndVals.op, paths,
                com.querydsl.core.util.StringUtils.uncapitalize(entity.getSimpleName()),
                propertyType, property, opAndVals.values));
    }

    private static BeanIntrospection<?> updatePathAndGetNextBeanIntro(List<PredicatePath> paths, Class<?> propertyType,
                                                                      String propName, StringBuilder pathBuilder,
                                                                      PredicatePath.RelationType relationType) {
        paths.add(PredicatePath.of(propertyType, propName, pathBuilder.toString(), relationType));
        pathBuilder.append('_').append(propName);
        return BeanIntrospection.getIntrospection(propertyType);
    }

    //Check to see if the class has the specified annotation or not
    private static boolean classHasAnnotation(Class<?> propertyType, Class<? extends Annotation> annotation) {
        try {
            BeanIntrospection<?> intro = BeanIntrospection.getIntrospection(propertyType);
            if (!intro.hasAnnotation(annotation))
                throw new NotEntityException();
            return true;
        } catch (IntrospectionException | NotEntityException ie) { // The field is not an entity
            return false;
        }
    }

    // Extract operator and constants from parameter's value.
    // e,g in(a,b,c) => {op = PredicateOperator.IN, values = {"a","b","c"}}
    // TODO Optimization idea: Use regular expressions for a cleaner code and maybe, better performance
    private static OperatorAndValues extractOpAndValues(Class<?> propertyType, String value) {
        int openParenthesesIdx = value.indexOf("(");
        if (openParenthesesIdx <= 0)
            throw new RuntimeException("Invalid predicate | cannot find operator: " + value);
        if (!value.endsWith(")"))
            throw new RuntimeException("Invalid predicate | no closing parentheses at the end: " + value);
        String opStr = value.substring(0, openParenthesesIdx).toUpperCase(); //not_null -> NOT_NULL
        PredicateOperator op;
        try {
            op = PredicateOperator.valueOf(opStr);
        } catch (Exception e) {
            throw new RuntimeException("Invalid operator '" + opStr + "' for predicate: " + value);
        }
        int lastIdx = value.length() - 1;
        String[] strValues;
        if (lastIdx - openParenthesesIdx > 1) // if there are values inside the parentheses, e.g. IN(a,b,c)
            strValues = value.substring(openParenthesesIdx + 1, lastIdx).split(",");
        else
            strValues = new String[0]; // no arguments inside parentheses, e.g. NOT_NULL()
        return OperatorAndValues.of(op, toTypedValues(propertyType, strValues));
    }

    /* values should be converted from String to the appropriate type according to their property in entity.
    an if/else clause have been used for mostly used types. But at the end, if none of the conditions are true,
    the Jackson's ObjectMapper.convertValue() will be used which is a two-step conversion and will support almost
    all other types. For example, we can't simply convert Enum types or composite classes used in entities
    to their exact class. Therefore, the Jackson's conversion will be used for Enums. */
    private static Object[] toTypedValues(final Class<?> type, final String[] values) {
        if (type == String.class)
            return values;
        else if (type == Long.class)
            return convertValues(values, Long::valueOf);
        else if (type == Integer.class)
            return convertValues(values, Integer::valueOf);
        else if (type == BigDecimal.class)
            return convertValues(values, BigDecimal::new);
        else if (type == Boolean.class)
            return convertValues(values, Boolean::parseBoolean);
        else if (type == Double.class)
            return convertValues(values, Double::valueOf);
        else if (type == LocalDate.class)
            return convertValues(values, LocalDate::parse);
        else if (type == Instant.class)
            return convertValues(values, Instant::parse);
        else if (type == Float.class)
            return convertValues(values, Float::valueOf);
        else if (type == Short.class)
            return convertValues(values, Short::valueOf);
        else if (type == Byte.class)
            return convertValues(values, Byte::valueOf);
        else if (type == Character.class)
            return convertValues(values, str -> str.charAt(0));
        else
            return convertValues(values, type);
    }

    private static Object[] convertValues(String[] values, Function<String, Object> mapper) {
        return Stream.of(values)
                .map(mapper)
                .distinct()
                .toArray(Object[]::new);
    }

    private static Object[] convertValues(String[] values, Class<?> type) {
        return Stream.of(values)
                .map(str -> new ObjectMapper().convertValue(str, type))
                .distinct()
                .toArray(Object[]::new);
    }

    // check to see if 'EXPR_TYPE' param exists. if not, set it to ALLOF for a AND predicate
    private static ExpressionType extractExpressionType(Map<String, String> params) {
        ExpressionType exprType = ExpressionType.ALLOF; // default
        String type = params.remove(ExpressionType.TypeKey);
        if (StringUtils.hasText(type)) {
            try {
                exprType = ExpressionType.valueOf(type.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        return exprType;
    }

    private static class OperatorAndValues {
        private final PredicateOperator op;
        private final Object[] values;

        private OperatorAndValues(PredicateOperator op, Object[] values) {
            this.op = op;
            this.values = values;
        }

        public static OperatorAndValues of(PredicateOperator op, Object[] values) {
            return new OperatorAndValues(op, values);
        }
    }
}
