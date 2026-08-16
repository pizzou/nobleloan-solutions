package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;

/** Detached, non-JPA fallback DTO for legacy API resources. */
public final class SafeEntityResponse {
    private final Map<String,Object> value;
    public SafeEntityResponse(Map<String,Object> value){this.value=value;}
    @JsonValue public Map<String,Object> json(){return value;}
}
