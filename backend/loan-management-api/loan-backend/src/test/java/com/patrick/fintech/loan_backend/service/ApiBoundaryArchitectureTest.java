package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.patrick.fintech.loan_backend.controller.LoanController;
import com.patrick.fintech.loan_backend.controller.BorrowerController;
import com.patrick.fintech.loan_backend.controller.PaymentController;
import com.patrick.fintech.loan_backend.controller.PaymentListController;
import com.patrick.fintech.loan_backend.controller.OrganizationController;
import com.patrick.fintech.loan_backend.dto.BorrowerResponse;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.dto.OrganizationResponse;
import com.patrick.fintech.loan_backend.dto.PaymentResponse;
import com.patrick.fintech.loan_backend.dto.UserResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.Set;

/**
 * API-boundary regression tests. These tests intentionally fail if a controller
 * starts exposing a JPA model through its response type again.
 */
class ApiBoundaryArchitectureTest {

    private static final Set<String> PROTECTED = Set.of(
            "com.patrick.fintech.loan_backend.model.Loan",
            "com.patrick.fintech.loan_backend.model.Borrower",
            "com.patrick.fintech.loan_backend.model.Payment",
            "com.patrick.fintech.loan_backend.model.User",
            "com.patrick.fintech.loan_backend.model.Organization");

    @Test
    void protectedDtosMustNotBeJpaEntities() {
        assertFalse(LoanResponse.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertFalse(BorrowerResponse.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertFalse(PaymentResponse.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertFalse(UserResponse.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertFalse(OrganizationResponse.class.isAnnotationPresent(jakarta.persistence.Entity.class));
    }

    @Test
    void protectedControllersUseDtoResponses() {
        for (Class<?> controller : Set.of(
                LoanController.class,
                BorrowerController.class,
                PaymentController.class,
                PaymentListController.class,
                OrganizationController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                assertNoProtectedEntity(method.getGenericReturnType(), method.toGenericString());
            }
        }
    }

    private static void assertNoProtectedEntity(Type type, String location) {
        if (type instanceof Class<?> c) {
            assertFalse(PROTECTED.contains(c.getName()), location);
            return;
        }
        if (type instanceof ParameterizedType p) {
            assertNoProtectedEntity(p.getRawType(), location);
            for (Type arg : p.getActualTypeArguments()) {
                assertNoProtectedEntity(arg, location);
            }
        }
        if (type instanceof GenericArrayType g) {
            assertNoProtectedEntity(g.getGenericComponentType(), location);
        }
    }
}
