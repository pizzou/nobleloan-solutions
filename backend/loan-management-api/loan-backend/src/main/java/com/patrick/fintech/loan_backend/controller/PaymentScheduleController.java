package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.service.PaymentScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public borrower payment schedule endpoint.
 *
 * IMPORTANT: schedules are never addressable by the internal Loan primary key.
 * A borrower must prove possession of both the public application reference
 * and the phone number registered against that application.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/payment-schedule")
public class PaymentScheduleController {

    private final PaymentScheduleService service;

    @GetMapping
    public List<PaymentScheduleResponse> getSchedule(
            @RequestParam String reference,
            @RequestParam String phone) {

        return service.getPublicSchedule(reference, phone);
    }
}
