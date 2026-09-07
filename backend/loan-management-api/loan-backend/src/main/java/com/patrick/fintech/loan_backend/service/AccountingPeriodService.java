package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.AccountingPeriod;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.AccountingPeriodRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountingPeriodService {
    private final AccountingPeriodRepository periodRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public AccountingPeriod open(Long orgId, int year, int month, String actor) {
        validateMonth(month);
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        AccountingPeriod p = periodRepository.findByOrganization_IdAndYearAndMonthForUpdate(orgId, year, month)
                .orElseGet(() -> AccountingPeriod.builder().organization(org).year(year).month(month).build());
        p.setStatus(AccountingPeriod.PeriodStatus.OPEN);
        p.setOpenedAt(LocalDateTime.now());
        p.setOpenedBy(actor);
        p.setClosedAt(null);
        p.setClosedBy(null);
        return periodRepository.save(p);
    }

    @Transactional
    public AccountingPeriod close(Long orgId, int year, int month, String actor) {
        validateMonth(month);
        AccountingPeriod p = periodRepository.findByOrganization_IdAndYearAndMonthForUpdate(orgId, year, month)
                .orElseThrow(() -> new IllegalStateException("Accounting period is not open because it does not exist"));
        if (p.getStatus() == AccountingPeriod.PeriodStatus.CLOSED) return p;
        if (year == LocalDate.now().getYear() && month == LocalDate.now().getMonthValue()) {
            throw new IllegalStateException("The current accounting period cannot be closed before month-end");
        }
        p.setStatus(AccountingPeriod.PeriodStatus.CLOSED);
        p.setClosedAt(LocalDateTime.now());
        p.setClosedBy(actor);
        return periodRepository.save(p);
    }

    @Transactional
    public void assertPostingAllowed(Long orgId, LocalDate date, String sourceType) {
        if (orgId == null || date == null) throw new IllegalArgumentException("Organization and entry date are required");
        int y = date.getYear(); int m = date.getMonthValue();
        AccountingPeriod p = periodRepository.findByOrganization_IdAndYearAndMonthForUpdate(orgId, y, m).orElse(null);
        if (p == null) {
            // Current period is automatically opened on first normal posting. Historical
            // periods must be explicitly opened; this prevents silent back-dating.
            LocalDate now = LocalDate.now();
            if (y == now.getYear() && m == now.getMonthValue()) {
                Organization org = organizationRepository.findById(orgId)
                        .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
                p = periodRepository.save(AccountingPeriod.builder().organization(org).year(y).month(m).status(AccountingPeriod.PeriodStatus.OPEN).openedBy("SYSTEM").build());
            } else if (sourceType != null && (sourceType.startsWith("LEGACY_") || sourceType.startsWith("MIGRATION_") || "OPENING_BALANCE".equals(sourceType))) {
                return;
            } else {
                throw new IllegalStateException("Accounting period " + y + "-" + String.format("%02d", m) + " is not open for posting");
            }
        }
        if (p.getStatus() == AccountingPeriod.PeriodStatus.CLOSED) {
            throw new IllegalStateException("Accounting period " + y + "-" + String.format("%02d", m) + " is closed. Post a controlled adjustment/reversal instead of back-dating into a closed period.");
        }
    }

    private void validateMonth(int month) { if (month < 1 || month > 12) throw new IllegalArgumentException("Month must be 1-12"); }
}
