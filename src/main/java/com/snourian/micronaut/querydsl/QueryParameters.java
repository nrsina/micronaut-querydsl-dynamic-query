package com.snourian.micronaut.querydsl;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.QueryValue;

import java.util.Map;
import java.util.stream.Collectors;

@Introspected
public class QueryParameters {

    @QueryValue("values")
    private final Map<String, String> parameters;

    public QueryParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
