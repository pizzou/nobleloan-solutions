package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MailServiceOtpDeliveryTest {

    private MailService mailService;
    private MockRestServiceServer server;
    private User user;

    @BeforeEach
    void setUp() {
        mailService = new MailService();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(mailService, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(mailService, "mailEnabled", true);
        ReflectionTestUtils.setField(mailService, "brevoApiKey", "test-api-key");
        ReflectionTestUtils.setField(mailService, "from", "no-reply@example.com");
        ReflectionTestUtils.setField(mailService, "fromName", "Noble Loan Solutions");

        user = new User();
        user.setEmail("user@example.com");
        user.setName("Test User");
    }

    @Test
    void returnsTrueOnlyWhenProviderAcceptsOtpEmail() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andRespond(withSuccess("{\"messageId\":\"provider-test-id\"}", MediaType.APPLICATION_JSON));

        assertTrue(mailService.sendLoginOtp(user, "123456"));
        server.verify();
    }

    @Test
    void returnsFalseWhenProviderRejectsOtpEmail() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"unauthorized\"}"));

        assertFalse(mailService.sendLoginOtp(user, "123456"));
        server.verify();
    }

    @Test
    void returnsFalseWhenEmailDeliveryIsDisabled() {
        ReflectionTestUtils.setField(mailService, "mailEnabled", false);

        assertFalse(mailService.sendLoginOtp(user, "123456"));
        server.verify();
    }

    @Test
    void returnsFalseWhenRecipientIsMissing() {
        user.setEmail("  ");

        assertFalse(mailService.sendLoginOtp(user, "123456"));
        server.verify();
    }
}
