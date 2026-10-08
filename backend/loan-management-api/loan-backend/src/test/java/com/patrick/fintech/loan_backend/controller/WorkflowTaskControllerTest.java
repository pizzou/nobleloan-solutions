package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskKpiResponse;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.WorkflowTaskService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkflowTaskControllerTest {

    @Mock
    WorkflowTaskService taskService;

    @Mock
    CurrentUserUtil currentUserUtil;

    private MockMvc mockMvc;
    private User actor;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(7L);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkflowTaskController(taskService, currentUserUtil))
                .setControllerAdvice(new com.patrick.fintech.loan_backend.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void mine_shouldExposeAssignedTasks() throws Exception {
        WorkflowTaskResponse response = new WorkflowTaskResponse(
                11L,
                7L,
                "Owner",
                "Disburse approved loan LN-001",
                "Complete controlled disbursement.",
                "LOAN_DISBURSEMENT",
                "LOAN",
                99L,
                "LN-001",
                "OPEN",
                "HIGH",
                LocalDateTime.now().plusHours(24),
                LocalDateTime.now(),
                null,
                null,
                false,
                "/dashboard/loans/99");

        when(currentUserUtil.getCurrentUser()).thenReturn(actor);
        when(taskService.getMine(actor, 25)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/tasks/mine")
                        .param("limit", "25")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].taskType").value("LOAN_DISBURSEMENT"));
    }

    @Test
    void mineKpi_shouldExposeActionableCounts() throws Exception {
        when(currentUserUtil.getCurrentUser()).thenReturn(actor);
        when(taskService.getMineKpi(actor)).thenReturn(new WorkflowTaskKpiResponse(3, 1, 2));

        mockMvc.perform(get("/api/tasks/mine/kpi")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openTasks").value(3))
                .andExpect(jsonPath("$.data.overdueTasks").value(1))
                .andExpect(jsonPath("$.data.urgentTasks").value(2));
    }
}
