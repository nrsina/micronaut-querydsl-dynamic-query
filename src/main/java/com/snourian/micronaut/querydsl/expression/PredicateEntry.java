package com.snourian.micronaut.querydsl.expression;


import com.snourian.micronaut.querydsl.expression.operator.PredicateOperator;

import java.util.Collections;
import java.util.List;

public class PredicateEntry {

    private PredicateOperator op;
    private List<PredicatePath> path;
    private String parentName;
    private Class<?> propertyType;
    private String property;
    private Object[] values;

    public PredicateEntry() {
    }

    public PredicateEntry(PredicateOperator op, List<PredicatePath> path, String parentName, Class<?> propertyType, String property, Object[] values) {
        this.op = op;
        this.path = Collections.unmodifiableList(path);
        this.parentName = parentName;
        this.propertyType = propertyType;
        this.property = property;
        this.values = values;
    }

    public PredicateOperator getOp() {
        return op;
    }

    public void setOp(PredicateOperator op) {
        this.op = op;
    }

    public List<PredicatePath> getPath() {
        return path;
    }

    public void setPath(List<PredicatePath> path) {
        this.path = path;
    }

    public String getParentName() {
        return parentName;
    }

    public String getProperty() {
        return property;
    }

    public Class<?> getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(Class<?> propertyType) {
        this.propertyType = propertyType;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public Object[] getValues() {
        return values;
    }

    public void setValues(Object[] values) {
        this.values = values;
    }
}
