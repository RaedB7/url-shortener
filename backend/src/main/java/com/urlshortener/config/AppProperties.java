package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urlshortener")
public class AppProperties {

    private int workerId;
    private String sessionSecret;
    private int sessionTtlDays = 7;
    private final BloomFilter bloomFilter = new BloomFilter();
    private final RateLimit rateLimit = new RateLimit();

    public static class BloomFilter {
        private int expectedInsertions = 100000;
        private double falsePositiveRate = 0.001;

        public int getExpectedInsertions() {
            return expectedInsertions;
        }

        public void setExpectedInsertions(int expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFalsePositiveRate() {
            return falsePositiveRate;
        }

        public void setFalsePositiveRate(double falsePositiveRate) {
            this.falsePositiveRate = falsePositiveRate;
        }
    }

    public static class RateLimit {
        private int createAnonPerMinute = 5;
        private int createAuthPerMinute = 30;
        private int loginPerMinute = 5;
        private int statsPerMinute = 30;
        private int redirectBurst = 200;
        private int redirectRefillPerMinute = 200;
        private int guestDailyCap = 50;

        public int getCreateAnonPerMinute() {
            return createAnonPerMinute;
        }

        public void setCreateAnonPerMinute(int v) {
            this.createAnonPerMinute = v;
        }

        public int getCreateAuthPerMinute() {
            return createAuthPerMinute;
        }

        public void setCreateAuthPerMinute(int v) {
            this.createAuthPerMinute = v;
        }

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int v) {
            this.loginPerMinute = v;
        }

        public int getStatsPerMinute() {
            return statsPerMinute;
        }

        public void setStatsPerMinute(int v) {
            this.statsPerMinute = v;
        }

        public int getRedirectBurst() {
            return redirectBurst;
        }

        public void setRedirectBurst(int v) {
            this.redirectBurst = v;
        }

        public int getRedirectRefillPerMinute() {
            return redirectRefillPerMinute;
        }

        public void setRedirectRefillPerMinute(int v) {
            this.redirectRefillPerMinute = v;
        }

        public int getGuestDailyCap() {
            return guestDailyCap;
        }

        public void setGuestDailyCap(int v) {
            this.guestDailyCap = v;
        }
    }

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public String getSessionSecret() {
        return sessionSecret;
    }

    public void setSessionSecret(String sessionSecret) {
        this.sessionSecret = sessionSecret;
    }

    public int getSessionTtlDays() {
        return sessionTtlDays;
    }

    public void setSessionTtlDays(int sessionTtlDays) {
        this.sessionTtlDays = sessionTtlDays;
    }

    public BloomFilter getBloomFilter() {
        return bloomFilter;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }
}
