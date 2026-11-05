
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.BorrowerDocumentRequirements;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enforces document readiness before the existing staff loan
 * creation endpoint executes.
 *
 * Public loan application endpoints remain unaffected.
 *
 * This class reuses the existing borrower-document rules and
 * storage services. No additional database tables or documents
 * are created by this validation.
 */
@RestControllerAdvice(assignableTypes = LoanController.class)
@RequiredArgsConstructor
public class LoanCreationDocumentGuard
                extends RequestBodyAdviceAdapter {

        private final BorrowerRepository borrowerRepository;

        private final BorrowerFileService borrowerFileService;

        private final CurrentUserUtil currentUserUtil;

        /**
         * Applies only to the existing LoanController.createLoan()
         * request body, not other loan actions.
         */
        @Override
        public boolean supports(
                        MethodParameter methodParameter,
                        Type targetType,
                        Class<? extends HttpMessageConverter<?>> converterType) {

                if (methodParameter.getMethod() == null) {
                        return false;
                }

                if (!"createLoan".equals(
                                methodParameter.getMethod().getName())) {
                        return false;
                }

                return LoanRequest.class.isAssignableFrom(
                                methodParameter.getParameterType());
        }

        /**
         * Spring invokes this after parsing the JSON request but
         * before the loan creation controller method executes.
         */
        @Override
        public Object afterBodyRead(
                        Object body,
                        HttpInputMessage inputMessage,
                        MethodParameter parameter,
                        Type targetType,
                        Class<? extends HttpMessageConverter<?>> converterType) {

                if (!(body instanceof LoanRequest request)) {
                        return body;
                }

                validateRequiredDocuments(request);

                return body;
        }

        public void validateRequiredDocuments(
                        LoanRequest request) {

                if (request.getBorrowerId() == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Borrower ID is required.");
                }

                User user = currentUserUtil.getCurrentUser();

                if (user == null ||
                                user.getOrganization() == null ||
                                user.getOrganization().getId() == null) {

                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "An authenticated organization is required.");
                }

                Long organizationId = user.getOrganization().getId();

                Borrower borrower = borrowerRepository
                                .findById(request.getBorrowerId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Borrower not found."));

                if (borrower.getOrganization() == null ||
                                borrower.getOrganization().getId() == null ||
                                !organizationId.equals(
                                                borrower.getOrganization().getId())) {

                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Borrower does not belong to your organization.");
                }

                // Same shared document requirements as internal
                // approval and the public application process.
                List<DocumentType> required = BorrowerDocumentRequirements.required(
                                borrower.getMaritalStatus(),
                                borrower.getEmploymentType());

                // A document can only be reused when the latest
                // borrower-level copy is acceptable under the
                // existing document reuse policy.
                List<DocumentType> missing = required.stream()
                                .filter(type -> !borrowerFileService
                                                .hasReusableBorrowerDocument(
                                                                borrower.getId(),
                                                                type))
                                .toList();

                if (!missing.isEmpty()) {
                        String missingNames = missing.stream()
                                        .map(type -> type.name().replace('_', ' '))
                                        .collect(Collectors.joining(", "));

                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Required documents must be uploaded or updated " +
                                                        "before sending this loan for approval: " +
                                                        missingNames);
                }
        }
}
