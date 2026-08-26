package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebsiteContentService {

        private final OrganizationRepository organizationRepository;

        @Transactional(readOnly = true)
        public Map<String, Object> getWebsiteContent(String slug) {

                if (slug == null || slug.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Tenant slug is required");
                }

                Organization organization = organizationRepository
                                .findBySlugIgnoreCase(slug.trim())
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Tenant not found"));

                return buildWebsiteContent(organization);
        }

        private Map<String, Object> buildWebsiteContent(
                        Organization organization) {

                String country = blankTo(
                                organization.getCountry(),
                                "—");

                String organizationName = blankTo(
                                organization.getName(),
                                "Financial Services");

                Map<String, Object> content = new LinkedHashMap<>();

                /*
                 * ============================================================
                 * HERO
                 * ============================================================
                 */

                content.put(
                                "heroBadge",
                                "Licensed & regulated financial institution");

                content.put(
                                "heroTitle",
                                blankTo(
                                                organization.getHeroHeadline(),
                                                "Need Financial Support?"));

                content.put(
                                "heroText",
                                blankTo(
                                                organization.getHeroSubtext(),
                                                "Transparent lending solutions designed around your financial needs."));

                /*
                 * ============================================================
                 * TRUST ITEMS
                 * ============================================================
                 */

                content.put(
                                "trustItems",
                                List.of(
                                                "Clear lending terms",
                                                "Secure customer information",
                                                "Online application",
                                                "Dedicated customer support"));

                /*
                 * ============================================================
                 * WHY CHOOSE US
                 * ============================================================
                 */

                List<Map<String, Object>> pillars = new ArrayList<>();

                pillars.add(
                                Map.of(
                                                "icon",
                                                "⚡",
                                                "title",
                                                "Fast Decisions",
                                                "description",
                                                "Applications are reviewed through a structured credit process with clear next steps."));

                pillars.add(
                                Map.of(
                                                "icon",
                                                "🛡️",
                                                "title",
                                                "Secure & Compliant",
                                                "description",
                                                "Customer information is handled through controlled access and secure platform processes."));

                pillars.add(
                                Map.of(
                                                "icon",
                                                "🤝",
                                                "title",
                                                "Transparent Terms",
                                                "description",
                                                "Applicable rates, fees, repayment obligations and loan conditions are communicated clearly."));

                pillars.add(
                                Map.of(
                                                "icon",
                                                "🎧",
                                                "title",
                                                "Dedicated Support",
                                                "description",
                                                organizationName
                                                                + " supports customers throughout application, approval, disbursement and repayment."));

                content.put(
                                "pillars",
                                pillars);

                /*
                 * ============================================================
                 * PROCESS
                 * ============================================================
                 */

                List<Map<String, Object>> process = new ArrayList<>();

                process.add(
                                Map.of(
                                                "step",
                                                "1",
                                                "title",
                                                "Apply Online",
                                                "description",
                                                "Complete your application using the online application process."));

                process.add(
                                Map.of(
                                                "step",
                                                "2",
                                                "title",
                                                "Submit Documents",
                                                "description",
                                                "Provide the documents required for identity, income and credit verification."));

                process.add(
                                Map.of(
                                                "step",
                                                "3",
                                                "title",
                                                "Credit Assessment",
                                                "description",
                                                "The application is assessed against the organization's lending and risk requirements."));

                process.add(
                                Map.of(
                                                "step",
                                                "4",
                                                "title",
                                                "Receive Funds",
                                                "description",
                                                "Approved financing is disbursed through the available approved payment channel."));

                content.put(
                                "processSteps",
                                process);

                /*
                 * ============================================================
                 * ABOUT
                 * ============================================================
                 */

                content.put(
                                "aboutTitle",
                                "About " + organizationName);

                content.put(
                                "aboutIntro",
                                blankTo(
                                                organization.getMission(),
                                                "We provide transparent financial services designed to help customers achieve their financial goals."));

                content.put(
                                "aboutMission",
                                blankTo(
                                                organization.getMission(),
                                                "To provide accessible, responsible and transparent financial services."));

                content.put(
                                "aboutVision",
                                blankTo(
                                                organization.getVision(),
                                                "To become a trusted financial partner for the communities we serve."));

                content.put(
                                "aboutValues",
                                "Integrity · Transparency · Responsibility · Inclusion · Excellence");

                List<Map<String, Object>> aboutReasons = new ArrayList<>();

                aboutReasons.add(
                                Map.of(
                                                "icon",
                                                "⚡",
                                                "title",
                                                "Structured Credit Decisions",
                                                "description",
                                                "Applications are reviewed through defined lending and risk processes."));

                aboutReasons.add(
                                Map.of(
                                                "icon",
                                                "📱",
                                                "title",
                                                "Apply from Anywhere",
                                                "description",
                                                "The online platform allows customers to begin their application remotely."));

                aboutReasons.add(
                                Map.of(
                                                "icon",
                                                "🔒",
                                                "title",
                                                "Secure Information",
                                                "description",
                                                "Customer information is handled through controlled systems and access policies."));

                aboutReasons.add(
                                Map.of(
                                                "icon",
                                                "💬",
                                                "title",
                                                "Customer Support",
                                                "description",
                                                "Customers can communicate with the organization throughout their loan journey."));

                aboutReasons.add(
                                Map.of(
                                                "icon",
                                                "🤝",
                                                "title",
                                                "Transparent Terms",
                                                "description",
                                                "Applicable financial obligations are disclosed as part of the lending process."));

                aboutReasons.add(
                                Map.of(
                                                "icon",
                                                "🌍",
                                                "title",
                                                "Local Understanding",
                                                "description",
                                                "Products and processes are designed around the needs of the markets served by the organization."));

                content.put(
                                "aboutReasons",
                                aboutReasons);

                content.put(
                                "aboutCtaTitle",
                                "Ready to Take the Next Step?");

                content.put(
                                "aboutCtaText",
                                "Review the available financial products and start your application when you are ready.");

                /*
                 * ============================================================
                 * SERVICES
                 * ============================================================
                 */

                content.put(
                                "servicesTitle",
                                "Our Financial Services");

                content.put(
                                "servicesText",
                                "Explore the financial products currently offered by "
                                                + organizationName
                                                + ".");

                content.put(
                                "requirementsTitle",
                                "Typical Requirements");

                content.put(
                                "requirements",
                                List.of(
                                                "Valid identification document",
                                                "Proof of income or business activity",
                                                "Recent financial statement where applicable",
                                                "Additional supporting documentation where required",
                                                "Completed loan application"));

                content.put(
                                "applicationLabel",
                                "Processing");

                content.put(
                                "applicationText",
                                "Processing time depends on document completeness, verification and credit assessment.");

                content.put(
                                "servicesCtaTitle",
                                "Need Help Choosing a Product?");

                content.put(
                                "servicesCtaText",
                                "Contact our team to discuss the financial product that best matches your needs.");

                /*
                 * ============================================================
                 * CONTACT
                 * ============================================================
                 */

                content.put(
                                "contactTitle",
                                "Get in Touch");

                content.put(
                                "contactText",
                                "Contact "
                                                + organizationName
                                                + " through the available communication channels.");

                content.put(
                                "contactInformationTitle",
                                "Contact Information");

                content.put(
                                "messageTitle",
                                "Send Us a Message");

                content.put(
                                "messageSuccessTitle",
                                "Message Received!");

                content.put(
                                "messageSuccessText",
                                "Thank you for contacting "
                                                + organizationName
                                                + ". We have received your message.");

                /*
                 * ============================================================
                 * FOOTER
                 * ============================================================
                 */

                content.put(
                                "footerSecurityText",
                                "Customer information is handled through controlled systems and applicable security procedures.");

                content.put(
                                "footerCountry",
                                country);

                return content;
        }

        private String blankTo(
                        String value,
                        String fallback) {

                if (value == null || value.isBlank()) {
                        return fallback;
                }

                return value.trim();
        }
}