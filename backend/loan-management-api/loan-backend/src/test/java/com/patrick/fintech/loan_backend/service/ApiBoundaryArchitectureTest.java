package com.patrick.fintech.loan_backend.service;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiBoundaryArchitectureTest {

    private static final String BASE_PACKAGE = "com.patrick.fintech.loan_backend";

    @Test
    void noRestControllerMayReturnJpaEntity() {

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                false);

        scanner.addIncludeFilter(
                new AnnotationTypeFilter(
                        RestController.class));

        scanner.findCandidateComponents(
                BASE_PACKAGE)
                .forEach(beanDefinition -> {

                    try {

                        Class<?> controller = Class.forName(
                                beanDefinition
                                        .getBeanClassName());

                        for (Method method : controller.getDeclaredMethods()) {

                            assertNoEntity(
                                    method.getGenericReturnType(),
                                    controller.getName()
                                            + "#"
                                            + method.getName());
                        }

                    } catch (ClassNotFoundException exception) {

                        throw new AssertionError(
                                "Could not load controller "
                                        + beanDefinition
                                                .getBeanClassName(),
                                exception);
                    }
                });
    }

    private static void assertNoEntity(
            Type type,
            String location) {

        if (type instanceof Class<?> clazz) {

            assertFalse(
                    clazz.isAnnotationPresent(
                            Entity.class),
                    "JPA entity exposed by API: "
                            + location
                            + " -> "
                            + clazz.getName());

            return;
        }

        if (type instanceof ParameterizedType parameterized) {

            assertNoEntity(
                    parameterized.getRawType(),
                    location);

            for (Type argument : parameterized.getActualTypeArguments()) {

                assertNoEntity(
                        argument,
                        location);
            }

            return;
        }

        if (type instanceof GenericArrayType array) {

            assertNoEntity(
                    array.getGenericComponentType(),
                    location);
        }
    }
}