package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

        private final OrganizationRepository orgRepo;

        private final UserRepository userRepo;

        private final CurrentUserUtil currentUserUtil;

        private final AuditService auditService;

        private final ObjectMapper objectMapper;

        /*
         * ============================================================
         * CURRENT ORGANIZATION
         * ============================================================
         */

        @GetMapping("/me")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getMyOrg() {

                Organization org = getCurrentOrganization();

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "id",
                                org.getId());

                response.put(
                                "slug",
                                org.getSlug());

                response.put(
                                "name",
                                org.getName());

                response.put(
                                "industry",
                                org.getIndustry());

                response.put(
                                "country",
                                org.getCountry());

                response.put(
                                "defaultCurrency",
                                org.getDefaultCurrency());

                response.put(
                                "timezone",
                                org.getTimezone());

                response.put(
                                "locale",
                                org.getLocale());

                response.put(
                                "logoUrl",
                                org.getLogoUrl());

                response.put(
                                "primaryColor",
                                org.getPrimaryColor());

                response.put(
                                "accentColor",
                                org.getAccentColor());

                response.put(
                                "website",
                                org.getWebsite());

                response.put(
                                "contactEmail",
                                org.getContactEmail());

                response.put(
                                "contactPhone",
                                org.getContactPhone());

                response.put(
                                "address",
                                org.getAddress());

                response.put(
                                "registrationNumber",
                                org.getRegistrationNumber());

                response.put(
                                "tagline",
                                org.getTagline());

                response.put(
                                "mission",
                                org.getMission());

                response.put(
                                "vision",
                                org.getVision());

                response.put(
                                "foundedYear",
                                org.getFoundedYear());

                response.put(
                                "mapUrl",
                                org.getMapUrl());

                response.put(
                                "facebookUrl",
                                org.getFacebookUrl());

                response.put(
                                "instagramUrl",
                                org.getInstagramUrl());

                response.put(
                                "linkedinUrl",
                                org.getLinkedinUrl());

                response.put(
                                "twitterUrl",
                                org.getTwitterUrl());

                response.put(
                                "whatsappUrl",
                                org.getWhatsappUrl());

                Map<String, Object> hero = new LinkedHashMap<>();

                hero.put(
                                "headline",
                                nullToEmpty(
                                                org.getHeroHeadline()));

                hero.put(
                                "subtext",
                                nullToEmpty(
                                                org.getHeroSubtext()));

                response.put(
                                "hero",
                                hero);

                response.put(
                                "stats",
                                parseListOrEmpty(
                                                org.getStatsJson()));

                response.put(
                                "services",
                                parseListOrEmpty(
                                                org.getServicesJson()));

                response.put(
                                "testimonials",
                                parseListOrEmpty(
                                                org.getTestimonialsJson()));

                response.put(
                                "team",
                                parseListOrEmpty(
                                                org.getTeamJson()));

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                response));
        }

        /*
         * ============================================================
         * UPDATE CURRENT ORGANIZATION
         * ============================================================
         */

        @PutMapping("/me")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Organization>> updateMyOrg(
                        @RequestBody Map<String, Object> body) {

                Organization org = getCurrentOrganization();

                /*
                 * CORE ORGANIZATION
                 */

                setIfPresent(
                                body,
                                "name",
                                org::setName);

                setIfPresent(
                                body,
                                "contactEmail",
                                org::setContactEmail);

                setIfPresent(
                                body,
                                "contactPhone",
                                org::setContactPhone);

                setIfPresent(
                                body,
                                "address",
                                org::setAddress);

                setIfPresent(
                                body,
                                "defaultCurrency",
                                org::setDefaultCurrency);

                setIfPresent(
                                body,
                                "timezone",
                                org::setTimezone);

                setIfPresent(
                                body,
                                "locale",
                                org::setLocale);

                setIfPresent(
                                body,
                                "website",
                                org::setWebsite);

                setIfPresent(
                                body,
                                "logoUrl",
                                org::setLogoUrl);

                /*
                 * TENANT SLUG
                 */

                if (body.containsKey("slug")
                                && body.get("slug") != null) {

                        String normalizedSlug = normalizeSlug(
                                        body.get("slug").toString());

                        if (normalizedSlug.isBlank()) {

                                throw new IllegalArgumentException(
                                                "Tenant slug cannot be empty");
                        }

                        orgRepo
                                        .findBySlugIgnoreCase(
                                                        normalizedSlug)
                                        .ifPresent(
                                                        existing -> {

                                                                if (!existing.getId().equals(
                                                                                org.getId())) {

                                                                        throw new IllegalArgumentException(
                                                                                        "Tenant slug is already in use");
                                                                }
                                                        });

                        org.setSlug(
                                        normalizedSlug);
                }

                /*
                 * BRANDING
                 */

                setIfPresent(
                                body,
                                "primaryColor",
                                value -> org.setPrimaryColor(
                                                sanitizeColor(
                                                                value)));

                setIfPresent(
                                body,
                                "accentColor",
                                value -> org.setAccentColor(
                                                sanitizeColor(
                                                                value)));

                /*
                 * PUBLIC WEBSITE
                 */

                setIfPresent(
                                body,
                                "tagline",
                                org::setTagline);

                setIfPresent(
                                body,
                                "mission",
                                org::setMission);

                setIfPresent(
                                body,
                                "vision",
                                org::setVision);

                setIfPresent(
                                body,
                                "mapUrl",
                                org::setMapUrl);

                setIfPresent(
                                body,
                                "facebookUrl",
                                org::setFacebookUrl);

                setIfPresent(
                                body,
                                "instagramUrl",
                                org::setInstagramUrl);

                setIfPresent(
                                body,
                                "linkedinUrl",
                                org::setLinkedinUrl);

                setIfPresent(
                                body,
                                "twitterUrl",
                                org::setTwitterUrl);

                setIfPresent(
                                body,
                                "whatsappUrl",
                                org::setWhatsappUrl);

                /*
                 * FOUNDED YEAR
                 */

                if (body.containsKey("foundedYear")
                                && body.get("foundedYear") != null) {

                        try {

                                org.setFoundedYear(
                                                Integer.valueOf(
                                                                body.get(
                                                                                "foundedYear").toString().trim()));

                        } catch (NumberFormatException e) {

                                throw new IllegalArgumentException(
                                                "Founded year must be a valid number");
                        }
                }

                /*
                 * HERO
                 */

                if (body.containsKey("hero")
                                && body.get("hero") instanceof Map<?, ?> hero) {

                        if (hero.get("headline") != null) {

                                org.setHeroHeadline(
                                                hero.get("headline")
                                                                .toString()
                                                                .trim());
                        }

                        if (hero.get("subtext") != null) {

                                org.setHeroSubtext(
                                                hero.get("subtext")
                                                                .toString()
                                                                .trim());
                        }
                }

                /*
                 * CMS JSON
                 */

                setJsonIfPresent(
                                body,
                                "stats",
                                org::setStatsJson);

                setJsonIfPresent(
                                body,
                                "services",
                                org::setServicesJson);

                setJsonIfPresent(
                                body,
                                "testimonials",
                                org::setTestimonialsJson);

                setJsonIfPresent(
                                body,
                                "team",
                                org::setTeamJson);

                /*
                 * SAVE
                 */

                Organization saved = orgRepo.save(
                                org);

                auditService.log(
                                saved,
                                currentUserUtil.getCurrentUser(),
                                "ORGANIZATION_UPDATED",
                                "ORGANIZATION",
                                saved.getId().toString(),
                                "Organization and public website settings updated");

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Updated",
                                                saved));
        }

        /*
         * ============================================================
         * ORGANIZATION USERS
         * ============================================================
         */

        @GetMapping("/me/users")
        public ResponseEntity<ApiResponse<List<User>>> getUsers() {

                Organization org = getCurrentOrganization();

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                userRepo.findByOrganization(
                                                                org)));
        }

        /*
         * ============================================================
         * CURRENT ORGANIZATION
         * ============================================================
         */

        private Organization getCurrentOrganization() {

                if (currentUserUtil.getCurrentUser() == null
                                || currentUserUtil
                                                .getCurrentUser()
                                                .getOrganization() == null
                                || currentUserUtil
                                                .getCurrentUser()
                                                .getOrganization()
                                                .getId() == null) {

                        throw new IllegalStateException(
                                        "Current user is not associated with an organization");
                }

                Long organizationId = currentUserUtil
                                .getCurrentUser()
                                .getOrganization()
                                .getId();

                return orgRepo.findById(
                                organizationId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Organization not found"));
        }

        /*
         * ============================================================
         * JSON HELPERS
         * ============================================================
         */

        private String nullToEmpty(
                        String value) {

                return value == null
                                ? ""
                                : value;
        }

        private List<Map<String, Object>> parseListOrEmpty(
                        String json) {

                if (json == null
                                || json.isBlank()) {

                        return List.of();
                }

                try {

                        List<Map<String, Object>> parsed = objectMapper.readValue(
                                        json,
                                        new TypeReference<List<Map<String, Object>>>() {
                                        });

                        return parsed == null
                                        ? List.of()
                                        : parsed;

                } catch (Exception e) {

                        throw new IllegalStateException(
                                        "Stored public website content is invalid JSON",
                                        e);
                }
        }

        private void setIfPresent(
                        Map<String, Object> body,
                        String key,
                        Consumer<String> setter) {

                if (!body.containsKey(key)
                                || body.get(key) == null) {

                        return;
                }

                setter.accept(
                                body.get(key)
                                                .toString()
                                                .trim());
        }

        private void setJsonIfPresent(
                        Map<String, Object> body,
                        String key,
                        Consumer<String> setter) {

                if (!body.containsKey(key)
                                || body.get(key) == null) {

                        return;
                }

                try {

                        setter.accept(
                                        objectMapper.writeValueAsString(
                                                        body.get(key)));

                } catch (Exception e) {

                        throw new IllegalArgumentException(
                                        "Invalid " + key + " content",
                                        e);
                }
        }

        private String normalizeSlug(
                        String value) {

                String normalized = value == null
                                ? ""
                                : value
                                                .trim()
                                                .toLowerCase()
                                                .replaceAll(
                                                                "[^a-z0-9]+",
                                                                "-");

                normalized = normalized.replaceAll(
                                "^-+|-+$",
                                "");

                if (normalized.length() > 120) {

                        normalized = normalized.substring(
                                        0,
                                        120);
                }

                return normalized;
        }

        private String sanitizeColor(
                        String value) {

                if (value == null
                                || value.isBlank()) {

                        return null;
                }

                String color = value.trim();

                if (!color.matches(
                                "^#[0-9a-fA-F]{6}$")) {

                        throw new IllegalArgumentException(
                                        "Color must be a six-digit hexadecimal value such as #0F1B3D");
                }

                return color.toUpperCase();
        }
}