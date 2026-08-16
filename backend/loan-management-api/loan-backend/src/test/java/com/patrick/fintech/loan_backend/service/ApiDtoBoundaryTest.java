package com.patrick.fintech.loan_backend.service;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.fail;

class ApiDtoBoundaryTest {

    private static final String DTO_PACKAGE = "com.patrick.fintech.loan_backend.dto";

    @Test
    void dashboardStatsMustNotContainJpaEntities() {

        assertNoJpaEntityField(
                com.patrick.fintech.loan_backend.dto.DashboardStats.class);
    }

    private static void assertNoJpaEntityField(
            Class<?> dtoClass) {

        for (Field field : dtoClass.getDeclaredFields()) {

            if (field.isSynthetic()) {
                continue;
            }

            assertTypeDoesNotContainEntity(
                    field.getGenericType(),
                    dtoClass.getName() + "." + field.getName());
        }
    }

    private static void assertTypeDoesNotContainEntity(
            Type type,
            String location) {

        if (type instanceof Class<?> clazz) {

            if (clazz.isAnnotationPresent(Entity.class)) {

                fail(
                        "JPA entity exposed through DTO: "
                                + location
                                + " -> "
                                + clazz.getName());
            }

            if (Collection.class.isAssignableFrom(clazz)) {
                return;
            }

            return;
        }

        if (type instanceof ParameterizedType parameterizedType) {

            assertTypeDoesNotContainEntity(
                    parameterizedType.getRawType(),
                    location);

            for (Type argument : parameterizedType.getActualTypeArguments()) {

                assertTypeDoesNotContainEntity(
                        argument,
                        location);
            }
        }
    }
}