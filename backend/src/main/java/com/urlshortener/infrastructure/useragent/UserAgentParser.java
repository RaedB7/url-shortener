package com.urlshortener.infrastructure.useragent;

import org.springframework.stereotype.Component;

@Component
public class UserAgentParser {

    public UaResult parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new UaResult("desktop", "Other", "Other");
        }
        String ua = userAgent.toLowerCase();

        String device = "desktop";
        if (ua.contains("ipad") || ua.contains("tablet")) {
            device = "tablet";
        } else if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone")) {
            device = "mobile";
        }
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            device = "desktop";
        }

        String browser = "Other";
        if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("chrome") && !ua.contains("chromium") && !ua.contains("edg/")) {
            browser = "Chrome";
        } else if (ua.contains("firefox")) {
            browser = "Firefox";
        } else if (ua.contains("safari") && !ua.contains("chrome")) {
            browser = "Safari";
        }

        String os = "Other";
        if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("mac os") || ua.contains("macintosh")) {
            os = "macOS";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            os = "iOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        }

        return new UaResult(device, browser, os);
    }

    public record UaResult(String deviceType, String browser, String os) {
    }
}
