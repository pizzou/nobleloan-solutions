package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.CurrencyRate;
import com.patrick.fintech.loan_backend.repository.CurrencyRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Multi-currency FX service.
 *
 * Uses BigDecimal for all monetary and FX-rate calculations.
 *
 * Rates are refreshed daily and once at startup from
 * exchangerate-api.com's free USD-based endpoint.
 *
 * If the live provider is temporarily unavailable, the last
 * successfully cached database rates remain available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyService {

    private final CurrencyRateRepository rateRepo;
    private final RestTemplate restTemplate;

    /**
     * Free, keyless endpoint.
     */
    private static final String RATES_URL =
            "https://open.er-api.com/v6/latest/USD";

    /**
     * Monetary scale used for converted currency amounts.
     */
    private static final int MONEY_SCALE = 2;

    /**
     * FX rate precision.
     *
     * Twelve decimal places gives enough precision for currency
     * conversion without using floating-point arithmetic.
     */
    private static final int RATE_SCALE = 12;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

    public static final List<String> SUPPORTED_CURRENCIES = List.of(
            "USD",
            "EUR",
            "GBP",
            "KES",
            "UGX",
            "TZS",
            "RWF",
            "ETB",
            "NGN",
            "GHS",
            "ZAR",
            "INR",
            "AED",
            "SAR",
            "QAR",
            "EGP",
            "XOF",
            "XAF",
            "MWK",
            "ZMW",
            "BDT",
            "PKR",
            "LKR",
            "PHP",
            "BRL"
    );

    /**
     * Convert an amount from one currency to another.
     *
     * All calculations are performed using BigDecimal.
     *
     * @param amount amount to convert
     * @param from source ISO currency
     * @param to target ISO currency
     * @return converted amount rounded to two decimal places
     */
    public BigDecimal convert(
            BigDecimal amount,
            String from,
            String to) {

        validateAmount(amount);
        validateCurrency(from, "from");
        validateCurrency(to, "to");

        if (from.equalsIgnoreCase(to)) {
            return amount.setScale(MONEY_SCALE, ROUNDING_MODE);
        }

        BigDecimal rate = getRate(from, to);

        return amount
                .multiply(rate)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    /**
     * Get the FX rate from one currency to another.
     *
     * The database stores USD-based rates. If a direct rate does not
     * exist, this method calculates the cross rate through USD:
     *
     *     FROM -> USD -> TO
     *
     * All calculations use BigDecimal.
     *
     * @param from source ISO currency
     * @param to target ISO currency
     * @return FX rate
     */
    public BigDecimal getRate(
            String from,
            String to) {

        validateCurrency(from, "from");
        validateCurrency(to, "to");

        String normalizedFrom = from.trim().toUpperCase();
        String normalizedTo = to.trim().toUpperCase();

        if (normalizedFrom.equals(normalizedTo)) {
            return BigDecimal.ONE.setScale(
                    RATE_SCALE,
                    ROUNDING_MODE
            );
        }

        /*
         * First try a directly stored rate.
         */
        BigDecimal directRate =
        rateRepo
                .findByBaseCurrencyAndTargetCurrency(
                        normalizedFrom,
                        normalizedTo
                )
                .map(CurrencyRate::getRate)
                .map(BigDecimal::valueOf)
                .orElse(null);

        if (directRate != null && directRate.signum() > 0) {
            return directRate.setScale(
                    RATE_SCALE,
                    ROUNDING_MODE
            );
        }

        /*
         * Database rates are primarily stored as:
         *
         *     USD -> currency
         *
         * Therefore calculate:
         *
         *     FROM -> USD
         *     USD  -> TO
         *
         * Example:
         *
         *     USD -> RWF = 1,450
         *
         *     RWF -> USD = 1 / 1,450
         *
         *     EUR -> RWF
         *       = (1 / USD->EUR) * USD->RWF
         */
        BigDecimal fromUsd;

        if ("USD".equals(normalizedFrom)) {
            fromUsd = BigDecimal.ONE;
        } else {
            BigDecimal usdToFrom =
        rateRepo
                .findByBaseCurrencyAndTargetCurrency(
                        "USD",
                        normalizedFrom
                )
                .map(CurrencyRate::getRate)
                .map(BigDecimal::valueOf)
                .orElse(null);

            if (usdToFrom == null
                    || usdToFrom.signum() <= 0) {

                log.warn(
                        "No valid USD rate configured for {}",
                        normalizedFrom
                );

                /*
                 * Preserve the previous service's fallback behavior
                 * for compatibility with the existing application.
                 *
                 * A missing rate is treated as 1 rather than causing
                 * an unexpected system-wide failure.
                 */
                fromUsd = BigDecimal.ONE;
            } else {
                fromUsd = BigDecimal.ONE
                        .divide(
                                usdToFrom,
                                RATE_SCALE,
                                ROUNDING_MODE
                        );
            }
        }

        BigDecimal toUsd;

        if ("USD".equals(normalizedTo)) {
            toUsd = BigDecimal.ONE;
        } else {
           toUsd =
        rateRepo
                .findByBaseCurrencyAndTargetCurrency(
                        "USD",
                        normalizedTo
                )
                .map(CurrencyRate::getRate)
                .map(BigDecimal::valueOf)
                .orElse(null);
            if (toUsd == null || toUsd.signum() <= 0) {
                log.warn(
                        "No valid USD rate configured for {}",
                        normalizedTo
                );

                /*
                 * Preserve compatibility with the existing
                 * fallback behavior.
                 */
                toUsd = BigDecimal.ONE;
            }
        }

        return fromUsd
                .multiply(toUsd)
                .setScale(
                        RATE_SCALE,
                        ROUNDING_MODE
                );
    }

    /**
     * Return all rates for a given base currency.
     */
    public List<CurrencyRate> getRatesForBase(String base) {

        validateCurrency(base, "base");

        return rateRepo.findByBaseCurrency(
                base.trim().toUpperCase()
        );
    }

    /**
     * Refresh FX rates from the live provider.
     *
     * Runs once shortly after startup and then every 24 hours.
     */
    @Scheduled(
            fixedDelay = 86_400_000,
            initialDelay = 5_000
    )
    public RefreshResult refreshRates() {

        try {

            @SuppressWarnings("unchecked")
            Map<String, Object> response =
                    restTemplate.getForObject(
                            RATES_URL,
                            Map.class
                    );

            if (response == null) {
                return new RefreshResult(
                        false,
                        "empty response from FX rate provider",
                        0
                );
            }

            if (!"success".equals(response.get("result"))) {

                return new RefreshResult(
                        false,
                        "FX rate provider returned: "
                                + response.get("result"),
                        0
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Number> rates =
                    (Map<String, Number>) response.get("rates");

            if (rates == null) {

                return new RefreshResult(
                        false,
                        "no rates in FX provider response",
                        0
                );
            }

            int updated = 0;

            for (String currency : SUPPORTED_CURRENCIES) {

                Number providerRate =
                        rates.get(currency);

                if (providerRate == null) {

                    log.warn(
                            "FX provider response did not include a rate for {}",
                            currency
                    );

                    continue;
                }

                /*
                 * Convert provider value to BigDecimal through its
                 * textual representation rather than:
                 *
                 *     new BigDecimal(double)
                 *
                 * This avoids importing binary floating-point
                 * representation errors into the persisted rate.
                 */
                BigDecimal rate =
                        new BigDecimal(
                                providerRate.toString()
                        ).setScale(
                                RATE_SCALE,
                                ROUNDING_MODE
                        );

                if (rate.signum() <= 0) {

                    log.warn(
                            "FX provider returned a non-positive rate for {}",
                            currency
                    );

                    continue;
                }

                CurrencyRate currencyRate =
                        rateRepo
                                .findByBaseCurrencyAndTargetCurrency(
                                        "USD",
                                        currency
                                )
                                .orElse(
                                        CurrencyRate
                                                .builder()
                                                .baseCurrency("USD")
                                                .targetCurrency(currency)
                                                .build()
                                );

                currencyRate.setRate(rate);

                rateRepo.save(currencyRate);

                updated++;
            }

            log.info(
                    "FX rates refreshed for {} currencies from live API",
                    updated
            );

            return new RefreshResult(
                    true,
                    "exchangerate-api.com (live)",
                    updated
            );

        } catch (Exception e) {

            log.warn(
                    "FX rate refresh failed, using cached rates: {}",
                    e.getMessage()
            );

            return new RefreshResult(
                    false,
                    "cached rates (refresh failed: "
                            + e.getMessage()
                            + ")",
                    0
            );
        }
    }

    /**
     * Validate monetary input.
     */
    private void validateAmount(BigDecimal amount) {

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Amount is required"
            );
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amount cannot be negative"
            );
        }
    }

    /**
     * Validate and normalize a currency code.
     */
    private void validateCurrency(
            String currency,
            String fieldName) {

        if (currency == null
                || currency.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName + " currency is required"
            );
        }

        String normalized =
                currency.trim().toUpperCase();

        if (!SUPPORTED_CURRENCIES.contains(normalized)) {

            throw new IllegalArgumentException(
                    "Unsupported currency: " + currency
            );
        }
    }

    /**
     * Result of an FX refresh attempt.
     */
    public record RefreshResult(
            boolean success,
            String source,
            int updatedCount
    ) {
    }
}