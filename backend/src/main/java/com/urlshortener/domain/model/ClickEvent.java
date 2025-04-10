package com.urlshortener.domain.model;

import java.time.Instant;
import java.util.Objects;

public class ClickEvent {

    private final String urlCode;
    private final Instant clickedAt;
    private final String referrer;
    private final String ipHash;
    private final String deviceType;
    private final String browser;
    private final String os;

    public ClickEvent(String urlCode, Instant clickedAt, String referrer, String ipHash,
            String deviceType, String browser, String os) {
        this.urlCode = Objects.requireNonNull(urlCode, "urlCode must not be null");
        this.clickedAt = Objects.requireNonNull(clickedAt, "clickedAt must not be null");
        this.referrer = referrer;
        this.ipHash = ipHash;
        this.deviceType = deviceType;
        this.browser = browser;
        this.os = os;
    }

    public String getUrlCode() {
        return urlCode;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }
}
