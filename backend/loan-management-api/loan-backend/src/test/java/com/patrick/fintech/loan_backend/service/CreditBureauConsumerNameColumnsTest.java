package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreditBureauConsumerNameColumnsTest {

    @Test
    void separatesThreeNamesAcrossDistinctTemplateColumns() {
        assertArrayEquals(new String[]{"KANYANA", "MARIE", "CLARE", null},
                CreditBureauRegulatoryExportService.splitConsumerNames("MARIE CLARE", "KANYANA"));
    }

    @Test
    void handlesAccidentallyCombinedSurnameWithoutMergingColumns() {
        assertArrayEquals(new String[]{"KANYANA", "MARIE", "CLARE", null},
                CreditBureauRegulatoryExportService.splitConsumerNames("MARIE", "CLARE KANYANA"));
    }

    @Test
    void preservesFourIndividualComponentsAndRejectsOverflow() {
        assertArrayEquals(new String[]{"D", "A", "B", "C"},
                CreditBureauRegulatoryExportService.splitConsumerNames("A B C", "D"));
        assertThrows(IllegalArgumentException.class,
                () -> CreditBureauRegulatoryExportService.splitConsumerNames("A B C D", "E"));
    }
}
