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
         * CURRENT BUSINESS RULES
         * ============================================================
         */

        private static final BigDecimal INTEREST_RATE = new BigDecimal("5.00");

        private static final String INTEREST_RATE_TYPE = "MONTHLY";

        private static final BigDecimal APPLICATION_FEE = new BigDecimal("2.00");

        private static final BigDecimal MANAGEMENT_FEE = new BigDecimal("5.00");

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final int MIN_TERM_MONTHS = 1;

        private static final int MAX_TERM_MONTHS = 6;

        @Override
        @Transactional
        public void run(String... args) {

                log.info("Starting Loan SaaS bootstrap validation...");

                Role adminRole = ensureRole(
                                "ADMIN",
                                "Full platform access");

                ensureRole(
                                "LOAN_OFFICER",
                                "Approve and disburse loans");

                ensureRole(
                                "MANAGER",
                                "Branch/portfolio management");

                ensureRole(
                                "BUSINESS_OWNER",
                                "Business owner — full financial and business-performance visibility");

                List<Organization> organizations = orgRepo.findAll();

                if (organizations.isEmpty()) {

                        Organization organization = createDefaultOrganization();

                        createBootstrapAdmin(
                                        organization,
                                        adminRole);

                        accountingService.ensureChartOfAccounts(
                                        organization);

                        ensureLoanProducts(
                                        organization);

                        log.info(
                                        "Initial organization and loan products created successfully.");

                        logBootstrapInformation(
                                        organization);

                        return;
                }

                for (Organization organization : organizations) {

                        if (organization == null
                                        || organization.getId() == null) {
                                continue;
                        }

                        log.info(
                                        "Validating loan-product configuration for organization {} ({})",
                                        organization.getId(),
                                        organization.getName());

                        ensureProductionOrganizationIdentity(organization);

                        ensureLoanProducts(
                                        organization);

                        accountingService.ensureChartOfAccounts(
                                        organization);
                }

                /*
                 * Existing production databases do not enter the
                 * organizations.isEmpty() branch. Keep the configured
                 * bootstrap administrator's login phone synchronized on
                 * every startup so ADMIN email+SMS OTP works after a
                 * deployment without recreating or resetting the account.
                 */
                ensureConfiguredAdminPhone();

                log.info(
                                "Loan SaaS bootstrap validation completed successfully.");
        }

        /*
         * ============================================================
         * DEFAULT ORGANIZATION
         * ============================================================
         */

        private Organization createDefaultOrganization() {

                Organization organization = Organization.builder()

                                /*
                                 * Organization identity is deployment-configured.
                                 */
                                .name(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_NAME",
                                                                "Noble Loan Solutions Ltd"))

                                .slug(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_SLUG",
                                                                "nobleloansolutions"))

                                .publicDomain(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_PUBLIC_DOMAIN",
                                                                "nobleloansolutions.rw"))

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
                                                envOrDefault("BOOTSTRAP_ORG_PRIMARY_COLOR", "#0F1B3D"))

                                .accentColor(
                                                envOrDefault("BOOTSTRAP_ORG_ACCENT_COLOR", "#C9A227"))

                                .website(
                                                envOrDefault("BOOTSTRAP_ORG_WEBSITE", ""))

                                .contactEmail(
                                                envOrDefault("BOOTSTRAP_ORG_CONTACT_EMAIL", ""))

                                .contactPhone(
                                                envOrDefault("BOOTSTRAP_ORG_CONTACT_PHONE", ""))

                                .address(
                                                envOrDefault("BOOTSTRAP_ORG_ADDRESS", ""))

                                .registrationNumber(
                                                envOrDefault("BOOTSTRAP_ORG_REGISTRATION_NUMBER", ""))

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

                                .foundedYear(parseIntegerEnv("BOOTSTRAP_ORG_FOUNDED_YEAR"))

                                .facebookUrl(envOrDefault("BOOTSTRAP_ORG_FACEBOOK_URL", ""))
                                .instagramUrl(envOrDefault("BOOTSTRAP_ORG_INSTAGRAM_URL", ""))
                                .linkedinUrl(envOrDefault("BOOTSTRAP_ORG_LINKEDIN_URL", ""))
                                .twitterUrl(envOrDefault("BOOTSTRAP_ORG_TWITTER_URL", ""))
                                .whatsappUrl(envOrDefault("BOOTSTRAP_ORG_WHATSAPP_URL", ""))
                                .mapUrl(envOrDefault("BOOTSTRAP_ORG_MAP_URL", ""))

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
                                 * NULL means unlimited.
                                 */
                                .maxLoanAmount(
                                                null)

                                .subscribedAt(
                                                LocalDateTime.now())

                                .subscriptionExpiresAt(
                                                LocalDateTime.now()
                                                                .plusYears(1))

                                .build();

                return orgRepo.save(
                                organization);
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

                String configuredPhone = System.getenv(
                                "BOOTSTRAP_ADMIN_PHONE");

                if (email == null
                                || email.isBlank()
                                || password == null
                                || password.isBlank()) {

                        throw new IllegalStateException(
                                        "BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD "
                                                        + "must both be set — refusing to create "
                                                        + "an admin account with a guessable default.");
                }

                String normalizedEmail = email.trim()
                                .toLowerCase();

                if (userRepo.findByEmail(normalizedEmail).isPresent()) {

                        ensureConfiguredAdminPhone();

                        log.info(
                                        "Bootstrap admin {} already exists — no account reset performed.",
                                        normalizedEmail);

                        return;
                }

                String name = configuredName != null
                                && !configuredName.isBlank()
                                                ? configuredName.trim()
                                                : "Admin";

                User user = makeUser(
                                name,
                                normalizedEmail,
                                password,
                                adminRole,
                                organization);

                if (configuredPhone != null
                                && !configuredPhone.isBlank()) {

                        user.setPhone(
                                        configuredPhone.trim());
                }

                userRepo.save(user);

                log.info(
                                "Bootstrap administrator created: {}",
                                normalizedEmail);
        }

        private void ensureConfiguredAdminPhone() {

                String email = System.getenv(
                                "BOOTSTRAP_ADMIN_EMAIL");

                String phone = System.getenv(
                                "BOOTSTRAP_ADMIN_PHONE");

                if (email == null
                                || email.isBlank()) {

                        log.warn(
                                        "BOOTSTRAP_ADMIN_EMAIL is not configured; "
                                                        + "cannot identify the bootstrap administrator for login-phone repair.");

                        return;
                }

                if (phone == null
                                || phone.isBlank()) {

                        log.warn(
                                        "BOOTSTRAP_ADMIN_PHONE is not configured. "
                                                        + "ADMIN login will continue to require a registered mobile number.");

                        return;
                }

                String normalizedEmail = email.trim()
                                .toLowerCase();

                String normalizedPhone = phone.trim();

                User user = userRepo
                                .findByEmail(normalizedEmail)
                                .orElse(null);

                if (user == null) {

                        log.warn(
                                        "Configured bootstrap administrator {} does not exist yet; "
                                                        + "its phone will be assigned when the account is created.",
                                        normalizedEmail);

                        return;
                }

                if (user.getPhone() == null
                                || user.getPhone().isBlank()
                                || !user.getPhone()
                                                .trim()
                                                .equals(normalizedPhone)) {

                        user.setPhone(
                                        normalizedPhone);

                        userRepo.save(user);

                        log.info(
                                        "Registered login mobile number for bootstrap administrator {} was updated.",
                                        normalizedEmail);
                }
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
                                .findByName(name)
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
                                encoder.encode(password));

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
         * LOAN PRODUCTS
         * ============================================================
         */

        private void ensureLoanProducts(
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

        /*
         * ============================================================
         * PRODUCT UPSERT
         * ============================================================
         */

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

                product.setName(
                                name);

                product.setIcon(
                                icon);

                product.setLoanType(
                                type);

                product.setDescription(
                                description);

                /*
                 * ========================================================
                 * CURRENT FINANCIAL RULES
                 * ========================================================
                 */

                product.setInterestRate(
                                INTEREST_RATE);

                product.setInterestRateType(
                                INTEREST_RATE_TYPE);

                product.setMinAmount(
                                MIN_LOAN_AMOUNT);

                /*
                 * NULL means unlimited.
                 */
                product.setMaxAmount(
                                null);

                product.setMinTermMonths(
                                MIN_TERM_MONTHS);

                product.setMaxTermMonths(
                                MAX_TERM_MONTHS);

                /*
                 * Application fee:
                 * One-time fee charged at application/disbursement.
                 */
                product.setApplicationFeePercent(
                                APPLICATION_FEE);

                /*
                 * Management fee:
                 * Monthly management fee.
                 */
                product.setManagementFeePercent(
                                MANAGEMENT_FEE);

                product.setActive(
                                true);

                product.setDisplayOrder(
                                displayOrder);

                if (isNew) {

                        log.info(
                                        "Creating loan product '{}' for organization {}",
                                        name,
                                        organization.getId());

                } else {

                        log.info(
                                        "Updating loan product '{}' for organization {} "
                                                        + "to current lending rules",
                                        name,
                                        organization.getId());
                }

                loanProductRepo.save(
                                product);
        }

        private void ensureProductionOrganizationIdentity(Organization organization) {
                if (organization == null || organization.getName() == null) {
                        return;
                }

                String configuredName = envOrDefault("BOOTSTRAP_ORG_NAME", "").trim();
                String currentName = organization.getName().trim();
                boolean targetOrganization = !configuredName.isBlank()
                                ? currentName.equalsIgnoreCase(configuredName)
                                : currentName.equalsIgnoreCase("Noble Loan Solutions Ltd")
                                                || currentName.equalsIgnoreCase("Noble Loan Solutions");

                if (!targetOrganization) {
                        return;
                }

                String configuredSlug = envOrDefault("BOOTSTRAP_ORG_SLUG", "nobleloansolutions");
                String currentSlug = organization.getSlug() == null ? "" : organization.getSlug().trim();
                if (currentSlug.isBlank()
                                || currentSlug.equalsIgnoreCase("nobleloansolution")
                                || currentSlug.equalsIgnoreCase("nobleloan")) {
                        organization.setSlug(configuredSlug);
                }

                String configuredDomain = envOrDefault("BOOTSTRAP_ORG_PUBLIC_DOMAIN", "");
                if (!configuredDomain.isBlank()
                                && (organization.getPublicDomain() == null || organization.getPublicDomain().isBlank())) {
                        organization.setPublicDomain(configuredDomain);
                }

                setConfiguredIfBlankOrKnownPlaceholder(organization.getWebsite(),
                                envOrDefault("BOOTSTRAP_ORG_WEBSITE", ""), organization::setWebsite);
                setConfiguredIfBlankOrKnownPlaceholder(organization.getContactEmail(),
                                envOrDefault("BOOTSTRAP_ORG_CONTACT_EMAIL", ""), organization::setContactEmail);
                setConfiguredIfBlankOrKnownPlaceholder(organization.getContactPhone(),
                                envOrDefault("BOOTSTRAP_ORG_CONTACT_PHONE", ""), organization::setContactPhone);
                setConfiguredIfBlankOrKnownPlaceholder(organization.getAddress(),
                                envOrDefault("BOOTSTRAP_ORG_ADDRESS", ""), organization::setAddress);
                setConfiguredIfBlankOrKnownPlaceholder(organization.getRegistrationNumber(),
                                envOrDefault("BOOTSTRAP_ORG_REGISTRATION_NUMBER", ""), organization::setRegistrationNumber);

                orgRepo.save(organization);
        }

        private void setConfiguredIfBlankOrKnownPlaceholder(
                        String current, String configured, java.util.function.Consumer<String> setter) {
                if (configured == null || configured.isBlank()) {
                        return;
                }
                if (current == null || current.isBlank()
                                || current.equalsIgnoreCase("https://nobleloansolutions.rw")
                                || current.equalsIgnoreCase("info@nobleloansolutions.rw")
                                || current.equalsIgnoreCase("+250 788 000 000")
                                || current.equalsIgnoreCase("REG-NLS-004")
                                || current.equalsIgnoreCase("KG 7 Ave, Kigali, Rwanda")) {
                        setter.accept(configured.trim());
                }
        }

        private Integer parseIntegerEnv(String name) {
                String value = System.getenv(name);
                if (value == null || value.isBlank()) {
                        return null;
                }
                try {
                        return Integer.valueOf(value.trim());
                } catch (NumberFormatException ex) {
                        throw new IllegalStateException(name + " must be a valid integer", ex);
                }
        }

        private String envOrDefault(
                        String name,
                        String defaultValue) {

                String value = System.getenv(
                                name);

                return value == null
                                || value.isBlank()
                                                ? defaultValue
                                                : value.trim();
        }

        /*
         * ============================================================
         * BOOTSTRAP LOG
         * ============================================================
         */

        private void logBootstrapInformation(
                        Organization organization) {

                log.info("");

                log.info(
                                "╔══════════════════════════════════════════════════════════════╗");

                log.info(
                                "║             LOANSAAS PRO — BOOTSTRAP COMPLETE              ║");

                log.info(
                                "╠══════════════════════════════════════════════════════════════╣");

                log.info(
                                "║ Organization : {}",
                                organization.getName());

                log.info(
                                "║ Currency     : {}",
                                organization.getDefaultCurrency());

                log.info(
                                "║ Min Loan     : {}",
                                MIN_LOAN_AMOUNT);

                log.info(
                                "║ Max Loan     : UNLIMITED");

                log.info(
                                "║ Interest     : {}% MONTHLY",
                                INTEREST_RATE);

                log.info(
                                "║ Application   : {}%",
                                APPLICATION_FEE);

                log.info(
                                "║ Management   : {}%",
                                MANAGEMENT_FEE);

                log.info(
                                "║ Term         : {}-{} months",
                                MIN_TERM_MONTHS,
                                MAX_TERM_MONTHS);

                log.info(
                                "╚══════════════════════════════════════════════════════════════╝");

                log.info("");
        }
}