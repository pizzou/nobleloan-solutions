package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.RoleRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.AccountingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

        private final OrganizationRepository orgRepo;

        private final UserRepository userRepo;

        private final RoleRepository roleRepo;

        private final PasswordEncoder encoder;

        private final AccountingService accountingService;

        private final LoanProductRepository loanProductRepo;

        /*
         * ============================================================
         * NOBLE INITIAL FINANCIAL RULES
         * ============================================================
         */

        private static final BigDecimal INTEREST_RATE = new BigDecimal("5.00");

        private static final String INTEREST_RATE_TYPE = "MONTHLY";

        private static final BigDecimal MANAGEMENT_FEE_RATE = new BigDecimal("5.00");

        private static final BigDecimal PROCESSING_FEE_RATE = new BigDecimal("2.00");

        private static final BigDecimal PENALTY_RATE = new BigDecimal("15.00");

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final int MIN_TERM_MONTHS = 1;

        private static final int MAX_TERM_MONTHS = 6;

        private static final String DEFAULT_STATS_JSON = "["
                        + "{"
                        + "\"icon\":\"👥\","
                        + "\"value\":\"5,000+\","
                        + "\"label\":\"Happy Clients\""
                        + "},"
                        + "{"
                        + "\"icon\":\"💰\","
                        + "\"value\":\"RWF 2B+\","
                        + "\"label\":\"Loans Disbursed\""
                        + "},"
                        + "{"
                        + "\"icon\":\"⚡\","
                        + "\"value\":\"24 hrs\","
                        + "\"label\":\"Average Approval\""
                        + "},"
                        + "{"
                        + "\"icon\":\"⭐\","
                        + "\"value\":\"98%\","
                        + "\"label\":\"Client Satisfaction\""
                        + "}"
                        + "]";

        private static final String DEFAULT_SERVICES_JSON = "["
                        + "{"
                        + "\"title\":\"Personal Loan\","
                        + "\"icon\":\"👤\","
                        + "\"rate\":\"5% / month\","
                        + "\"managementFee\":\"5% / month\","
                        + "\"maxAmount\":\"Unlimited\","
                        + "\"term\":\"1 to 6 months\","
                        + "\"description\":\"Personal financing for approved household and individual needs.\""
                        + "},"
                        + "{"
                        + "\"title\":\"Business Finance\","
                        + "\"icon\":\"🏢\","
                        + "\"rate\":\"5% / month\","
                        + "\"managementFee\":\"5% / month\","
                        + "\"maxAmount\":\"Unlimited\","
                        + "\"term\":\"1 to 6 months\","
                        + "\"description\":\"Working capital and business expansion financing.\""
                        + "},"
                        + "{"
                        + "\"title\":\"Vehicle Finance\","
                        + "\"icon\":\"🚗\","
                        + "\"rate\":\"5% / month\","
                        + "\"managementFee\":\"5% / month\","
                        + "\"maxAmount\":\"Unlimited\","
                        + "\"term\":\"1 to 6 months\","
                        + "\"description\":\"Financing for approved vehicle purchases.\""
                        + "},"
                        + "{"
                        + "\"title\":\"Salary Advance Loan\","
                        + "\"icon\":\"💵\","
                        + "\"rate\":\"5% / month\","
                        + "\"managementFee\":\"5% / month\","
                        + "\"maxAmount\":\"Unlimited\","
                        + "\"term\":\"1 to 6 months\","
                        + "\"description\":\"Short-term financing against verified salary income.\""
                        + "},"
                        + "{"
                        + "\"title\":\"Agriculture Loan\","
                        + "\"icon\":\"🌾\","
                        + "\"rate\":\"5% / month\","
                        + "\"managementFee\":\"5% / month\","
                        + "\"maxAmount\":\"Unlimited\","
                        + "\"term\":\"1 to 6 months\","
                        + "\"description\":\"Financing for approved agricultural and agribusiness activities.\""
                        + "}"
                        + "]";

        private static final String DEFAULT_TESTIMONIALS_JSON = "["
                        + "{"
                        + "\"name\":\"Joseph G.\","
                        + "\"role\":\"Small Business Owner\","
                        + "\"rating\":5,"
                        + "\"text\":\"Noble Loan Solutions helped me expand my shop with a business loan.\""
                        + "},"
                        + "{"
                        + "\"name\":\"Olivier M.\","
                        + "\"role\":\"Farmer\","
                        + "\"rating\":5,"
                        + "\"text\":\"I received agricultural financing to expand my farming operation.\""
                        + "},"
                        + "{"
                        + "\"name\":\"Grace U.\","
                        + "\"role\":\"Teacher\","
                        + "\"rating\":5,"
                        + "\"text\":\"The salary advance process was simple and convenient.\""
                        + "}"
                        + "]";

        private static final String DEFAULT_TEAM_JSON = "["
                        + "{"
                        + "\"name\":\"Emmanuel R.\","
                        + "\"role\":\"Chief Executive Officer\","
                        + "\"initials\":\"ER\""
                        + "},"
                        + "{"
                        + "\"name\":\"Alice U.\","
                        + "\"role\":\"Chief Finance Officer\","
                        + "\"initials\":\"AU\""
                        + "},"
                        + "{"
                        + "\"name\":\"Patrick M.\","
                        + "\"role\":\"Head of Credit\","
                        + "\"initials\":\"PM\""
                        + "},"
                        + "{"
                        + "\"name\":\"Alice K.\","
                        + "\"role\":\"Head of Operations\","
                        + "\"initials\":\"AK\""
                        + "}"
                        + "]";

        /*
         * ============================================================
         * STARTUP
         * ============================================================
         */

        @Override
        @Transactional
        public void run(String... args) {

                log.info(
                                "Starting production-safe Loan SaaS bootstrap...");

                Role adminRole = ensureRole(
                                "ADMIN",
                                "Full platform access");

                ensureRole(
                                "LOAN_OFFICER",
                                "Approve and disburse loans");

                ensureRole(
                                "MANAGER",
                                "Branch/portfolio management");

                List<Organization> organizations = orgRepo.findAll();

                /*
                 * ========================================================
                 * BRAND-NEW DATABASE
                 * ========================================================
                 */

                if (organizations.isEmpty()) {

                        Organization noble = createNobleOrganization();

                        createBootstrapAdmin(
                                        noble,
                                        adminRole);

                        accountingService.ensureChartOfAccounts(
                                        noble);

                        ensureNobleLoanProducts(
                                        noble);

                        log.info(
                                        "Initial Noble tenant bootstrap completed successfully.");

                        logBootstrapInformation(
                                        noble);

                        return;
                }

                for (Organization organization : organizations) {

                        if (organization == null
                                        || organization.getId() == null) {
                                continue;
                        }

                        try {

                                accountingService.ensureChartOfAccounts(
                                                organization);

                        } catch (Exception e) {

                                log.error(
                                                "Failed to validate accounting chart for organization {}",
                                                organization.getId(),
                                                e);

                                throw e;
                        }
                }

                Organization noble = findBootstrapNobleTenant(
                                organizations);

                if (noble != null) {

                        seedMissingNobleWebsiteContent(
                                        noble);

                        ensureNobleLoanProducts(
                                        noble);
                }

                log.info(
                                "Production-safe Loan SaaS bootstrap validation completed.");
        }

        /*
         * ============================================================
         * NOBLE ORGANIZATION
         * ============================================================
         */

        private Organization createNobleOrganization() {

                LocalDateTime now = LocalDateTime.now();

                Organization organization = Organization.builder()

                                .name(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_NAME",
                                                                "Noble Loan Solutions Ltd"))

                                .slug(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_SLUG",
                                                                "nobleloansolutions"))

                                .industry(
                                                "Microfinance")

                                .country(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_COUNTRY",
                                                                "RW"))

                                .defaultCurrency(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_CURRENCY",
                                                                "RWF"))

                                .timezone(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_TIMEZONE",
                                                                "Africa/Kigali"))

                                .locale(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_LOCALE",
                                                                "en-RW"))

                                .primaryColor(
                                                "#0F1B3D")

                                .accentColor(
                                                "#C9A227")

                                .website(
                                                "https://nobleloansolutions.rw")

                                .contactEmail(
                                                "info@nobleloansolutions.rw")

                                .contactPhone(
                                                "+250 788 000 000")

                                .address(
                                                "KG 7 Ave, Kigali, Rwanda")

                                .registrationNumber(
                                                "REG-NLS-004")

                                .tagline(
                                                "Your Trusted Partner in Financial Support")

                                .mission(
                                                "To provide honest, fairly-priced credit to individuals and businesses across Rwanda, delivered with integrity, transparency, and respect for every client.")

                                .vision(
                                                "To be Rwanda's most trusted name in lending — synonymous with fairness, transparency, and financial dignity for every client we serve.")

                                .heroHeadline(
                                                "Need Cash Fast? We've Got You Covered!")

                                .heroSubtext(
                                                "Your trusted partner in financial support — personal, business, vehicle, salary advance, and agriculture loans, backed by a secure, fully compliant lending platform.")

                                .statsJson(
                                                DEFAULT_STATS_JSON)

                                .servicesJson(
                                                DEFAULT_SERVICES_JSON)

                                .testimonialsJson(
                                                DEFAULT_TESTIMONIALS_JSON)

                                .teamJson(
                                                DEFAULT_TEAM_JSON)

                                .foundedYear(
                                                2020)

                                .facebookUrl(
                                                "https://facebook.com/nobleloansolutionsrw")

                                .instagramUrl(
                                                "https://instagram.com/nobleloansolutionsrw")

                                .linkedinUrl(
                                                "https://linkedin.com/company/nobleloansolutionsrw")

                                .twitterUrl(
                                                "https://twitter.com/nobleloansolutionsrw")

                                .whatsappUrl(
                                                "https://wa.me/250788000000")

                                .mapUrl(
                                                "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d63800.15641867!2d30.0644!3d-1.9536!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x19dca75a929d959f%3A0x0!2sKigali!5e0!3m2!1sen!2srw!4v1690000000000")

                                .subscriptionTier(
                                                Organization.SubscriptionTier.PROFESSIONAL)

                                .status(
                                                Organization.OrgStatus.ACTIVE)

                                .maxUsers(
                                                100)

                                .maxActiveLoans(
                                                10000)

                                .minLoanAmount(
                                                MIN_LOAN_AMOUNT)

                                /*
                                 * NULL = unlimited.
                                 */
                                .maxLoanAmount(
                                                null)

                                .subscribedAt(
                                                now)

                                .subscriptionExpiresAt(
                                                now.plusYears(1))

                                .build();

                return orgRepo.save(
                                organization);
        }

        /*
         * ============================================================
         * FIND NOBLE TENANT
         * ============================================================
         */

        private Organization findBootstrapNobleTenant(
                        List<Organization> organizations) {

                String configuredName = envOrDefault(
                                "BOOTSTRAP_ORG_NAME",
                                "Noble Loan Solutions Ltd");

                String configuredRegistration = envOrDefault(
                                "BOOTSTRAP_ORG_REGISTRATION",
                                "REG-NLS-004");

                String expectedName = normalizeKey(
                                configuredName);

                String expectedRegistration = normalizeKey(
                                configuredRegistration);

                return organizations
                                .stream()
                                .filter(
                                                organization -> organization != null)
                                .filter(
                                                organization -> {

                                                        String name = normalizeKey(
                                                                        organization.getName());

                                                        String registration = normalizeKey(
                                                                        organization.getRegistrationNumber());

                                                        return expectedName.equals(name)
                                                                        || (!expectedRegistration.isBlank()
                                                                                        && expectedRegistration.equals(
                                                                                                        registration));
                                                })
                                .findFirst()
                                .orElse(null);
        }

        /*
         * ============================================================
         * WEBSITE CONTENT
         * ============================================================
         */

        private void seedMissingNobleWebsiteContent(
                        Organization organization) {

                boolean changed = false;

                if (isBlank(
                                organization.getTagline())) {

                        organization.setTagline(
                                        "Your Trusted Partner in Financial Support");

                        changed = true;
                }

                if (isBlank(
                                organization.getMission())) {

                        organization.setMission(
                                        "To provide honest, fairly-priced credit to individuals and businesses across Rwanda, delivered with integrity, transparency, and respect for every client.");

                        changed = true;
                }

                if (isBlank(
                                organization.getVision())) {

                        organization.setVision(
                                        "To be Rwanda's most trusted name in lending — synonymous with fairness, transparency, and financial dignity for every client we serve.");

                        changed = true;
                }

                if (isBlank(
                                organization.getHeroHeadline())) {

                        organization.setHeroHeadline(
                                        "Need Cash Fast? We've Got You Covered!");

                        changed = true;
                }

                if (isBlank(
                                organization.getHeroSubtext())) {

                        organization.setHeroSubtext(
                                        "Your trusted partner in financial support — personal, business, vehicle, salary advance, and agriculture loans, backed by a secure, fully compliant lending platform.");

                        changed = true;
                }

                if (isBlank(
                                organization.getStatsJson())) {

                        organization.setStatsJson(
                                        DEFAULT_STATS_JSON);

                        changed = true;
                }

                if (isBlank(
                                organization.getServicesJson())) {

                        organization.setServicesJson(
                                        DEFAULT_SERVICES_JSON);

                        changed = true;
                }

                if (isBlank(
                                organization.getTestimonialsJson())) {

                        organization.setTestimonialsJson(
                                        DEFAULT_TESTIMONIALS_JSON);

                        changed = true;
                }

                if (isBlank(
                                organization.getTeamJson())) {

                        organization.setTeamJson(
                                        DEFAULT_TEAM_JSON);

                        changed = true;
                }

                if (organization.getFoundedYear() == null) {

                        organization.setFoundedYear(
                                        2020);

                        changed = true;
                }

                if (isBlank(
                                organization.getPrimaryColor())) {

                        organization.setPrimaryColor(
                                        "#0F1B3D");

                        changed = true;
                }

                if (isBlank(
                                organization.getAccentColor())) {

                        organization.setAccentColor(
                                        "#C9A227");

                        changed = true;
                }

                if (changed) {

                        orgRepo.save(
                                        organization);

                        log.info(
                                        "Filled missing Noble website bootstrap content for organization {}.",
                                        organization.getId());
                }
        }

        /*
         * ============================================================
         * BOOTSTRAP ADMIN
         * ============================================================
         */

        private void createBootstrapAdmin(
                        Organization organization,
                        Role adminRole) {

                String email = System.getenv(
                                "BOOTSTRAP_ADMIN_EMAIL");

                String password = System.getenv(
                                "BOOTSTRAP_ADMIN_PASSWORD");

                String configuredName = System.getenv(
                                "BOOTSTRAP_ADMIN_NAME");

                if (email == null
                                || email.isBlank()
                                || password == null
                                || password.isBlank()) {

                        throw new IllegalStateException(
                                        "BOOTSTRAP_ADMIN_EMAIL and "
                                                        + "BOOTSTRAP_ADMIN_PASSWORD must both be set.");
                }

                String normalizedEmail = email.trim()
                                .toLowerCase(
                                                Locale.ROOT);

                if (userRepo.findByEmail(
                                normalizedEmail).isPresent()) {

                        log.info(
                                        "Bootstrap admin {} already exists — skipping.",
                                        normalizedEmail);

                        return;
                }

                String name = configuredName == null
                                || configuredName.isBlank()
                                                ? "Admin"
                                                : configuredName.trim();

                User user = makeUser(
                                name,
                                normalizedEmail,
                                password,
                                adminRole,
                                organization);

                userRepo.save(
                                user);

                log.info(
                                "Bootstrap administrator created: {}",
                                normalizedEmail);
        }

        /*
         * ============================================================
         * ROLES
         * ============================================================
         */

        private Role ensureRole(
                        String name,
                        String description) {

                return roleRepo
                                .findByName(
                                                name)
                                .orElseGet(
                                                () -> roleRepo.save(
                                                                new Role(
                                                                                null,
                                                                                name,
                                                                                description)));
        }

        /*
         * ============================================================
         * USER
         * ============================================================
         */

        private User makeUser(
                        String name,
                        String email,
                        String password,
                        Role role,
                        Organization organization) {

                User user = new User();

                user.setName(
                                name);

                user.setEmail(
                                email);

                user.setPassword(
                                encoder.encode(
                                                password));

                user.setRole(
                                role);

                user.setOrganization(
                                organization);

                user.setStatus(
                                User.UserStatus.ACTIVE);

                return user;
        }

        /*
         * ============================================================
         * NOBLE LOAN PRODUCTS
         * ============================================================
         *
         * These are bootstrap products.
         *
         * Existing product records are preserved.
         *
         * Missing Noble products are created.
         */

        private void ensureNobleLoanProducts(
                        Organization organization) {

                ensureProduct(
                                organization,
                                "Personal Loan",
                                "👤",
                                Loan.LoanType.PERSONAL,
                                "Personal financing for approved household and individual needs.",
                                1);

                ensureProduct(
                                organization,
                                "Business Finance",
                                "🏢",
                                Loan.LoanType.BUSINESS,
                                "Working capital and business expansion financing.",
                                2);

                ensureProduct(
                                organization,
                                "Vehicle Finance",
                                "🚗",
                                Loan.LoanType.AUTO,
                                "Financing for approved vehicle purchases.",
                                3);

                ensureProduct(
                                organization,
                                "Salary Advance Loan",
                                "💵",
                                Loan.LoanType.SALARY_ADVANCE,
                                "Short-term financing against verified salary income.",
                                4);

                ensureProduct(
                                organization,
                                "Agriculture Loan",
                                "🌾",
                                Loan.LoanType.AGRICULTURAL,
                                "Financing for approved agricultural and agribusiness activities.",
                                5);
        }

        private void ensureProduct(
                        Organization organization,
                        String name,
                        String icon,
                        Loan.LoanType type,
                        String description,
                        int displayOrder) {

                LoanProduct product = loanProductRepo
                                .findByOrganization_IdAndLoanType(
                                                organization.getId(),
                                                type)
                                .orElseGet(
                                                LoanProduct::new);

                boolean isNew = product.getId() == null;

                product.setOrganization(
                                organization);

                if (isNew) {

                        product.setName(
                                        name);

                        product.setIcon(
                                        icon);

                        product.setLoanType(
                                        type);

                        product.setDescription(
                                        description);

                        /*
                         * 5% INTEREST PER MONTH.
                         */
                        product.setInterestRate(
                                        INTEREST_RATE);

                        product.setInterestRateType(
                                        INTEREST_RATE_TYPE);

                        product.setMinAmount(
                                        MIN_LOAN_AMOUNT);

                        /*
                         * NULL = UNLIMITED.
                         */
                        product.setMaxAmount(
                                        null);

                        product.setMinTermMonths(
                                        MIN_TERM_MONTHS);

                        product.setMaxTermMonths(
                                        MAX_TERM_MONTHS);

                        /*
                         * 2% ONE-TIME PROCESSING FEE.
                         */
                        product.setProcessingFeePercent(
                                        PROCESSING_FEE_RATE);

                        /*
                         * 5% MANAGEMENT FEE PER MONTH.
                         */
                        product.setManagementFeePercent(
                                        MANAGEMENT_FEE_RATE);

                        product.setPenaltyPercent(
                                        PENALTY_RATE);

                        product.setActive(
                                        true);

                        product.setDisplayOrder(
                                        displayOrder);

                        loanProductRepo.save(
                                        product);

                        log.info(
                                        "Created missing Noble loan product '{}' for organization {}.",
                                        name,
                                        organization.getId());

                        return;
                }

                /*
                 * EXISTING PRODUCT:
                 *
                 * Preserve tenant-managed financial configuration.
                 *
                 * We only repair a missing display order.
                 */
                if (product.getDisplayOrder() == null) {

                        product.setDisplayOrder(
                                        displayOrder);

                        loanProductRepo.save(
                                        product);
                }
        }

        /*
         * ============================================================
         * LOG
         * ============================================================
         */

        private void logBootstrapInformation(
                        Organization organization) {

                log.info("");
                log.info(
                                "==============================================================");
                log.info(
                                "LOANSAAS PRO — BOOTSTRAP COMPLETE");
                log.info(
                                "Organization : {}",
                                organization.getName());
                log.info(
                                "Currency     : {}",
                                organization.getDefaultCurrency());
                log.info(
                                "Minimum loan : {}",
                                MIN_LOAN_AMOUNT);
                log.info(
                                "Maximum loan : UNLIMITED");
                log.info(
                                "Interest     : {}% MONTHLY",
                                INTEREST_RATE);
                log.info(
                                "Management   : {}% MONTHLY",
                                MANAGEMENT_FEE_RATE);
                log.info(
                                "Combined     : {}% MONTHLY",
                                INTEREST_RATE.add(
                                                MANAGEMENT_FEE_RATE));
                log.info(
                                "Processing   : {}% ONE-TIME",
                                PROCESSING_FEE_RATE);
                log.info(
                                "Term         : {}-{} MONTHS",
                                MIN_TERM_MONTHS,
                                MAX_TERM_MONTHS);
                log.info(
                                "==============================================================");
                log.info("");
        }

        /*
         * ============================================================
         * HELPERS
         * ============================================================
         */

        private static boolean isBlank(
                        String value) {

                return value == null
                                || value.isBlank();
        }

        private static String normalizeKey(
                        String value) {

                if (value == null) {
                        return "";
                }

                return value
                                .trim()
                                .toLowerCase(
                                                Locale.ROOT)
                                .replaceAll(
                                                "[^a-z0-9]+",
                                                "");
        }

        private static String envOrDefault(
                        String key,
                        String fallback) {

                String value = System.getenv(
                                key);

                return value == null
                                || value.isBlank()
                                                ? fallback
                                                : value.trim();
        }
}