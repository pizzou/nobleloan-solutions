package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.RoleRequest;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RoleService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleService roleService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    public RoleController(RoleService roleService, AuditService auditService, CurrentUserUtil currentUserUtil) {
        this.roleService = roleService;
        this.auditService = auditService;
        this.currentUserUtil = currentUserUtil;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody RoleRequest request) {
        Role role = new Role();
        role.setName(request.getName().trim().toUpperCase());
        role.setDescription(request.getDescription());
        Role saved = roleService.save(role);
        var user = currentUserUtil.getCurrentUser();
        auditService.log(user.getOrganization(), user, "ROLE_CREATED", "ROLE", String.valueOf(saved.getId()),
                "Created role " + saved.getName(), null, null, "Roles & Permissions");
        return ResponseEntity.ok(ApiResponse.safe("Role created", saved));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getAll() {
        return ResponseEntity.ok(ApiResponse.safe(roleService.getAll()));
    }
}
