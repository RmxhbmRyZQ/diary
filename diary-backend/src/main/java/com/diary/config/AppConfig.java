package com.diary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private Security security = new Security();
    private Kdf kdf = new Kdf();
    private Upload upload = new Upload();
    private RateLimit rateLimit = new RateLimit();

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Kdf getKdf() { return kdf; }
    public void setKdf(Kdf kdf) { this.kdf = kdf; }
    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public static class Security {
        private int bcryptCost = 12;
        private long sessionMaxAge = 604800;

        public int getBcryptCost() { return bcryptCost; }
        public void setBcryptCost(int bcryptCost) { this.bcryptCost = bcryptCost; }
        public long getSessionMaxAge() { return sessionMaxAge; }
        public void setSessionMaxAge(long sessionMaxAge) { this.sessionMaxAge = sessionMaxAge; }
    }

    public static class Kdf {
        private String defaultAlgorithm = "pbkdf2-sha256";
        private int defaultIterations = 600000;
        private int minIterations = 100000;

        public String getDefaultAlgorithm() { return defaultAlgorithm; }
        public void setDefaultAlgorithm(String defaultAlgorithm) { this.defaultAlgorithm = defaultAlgorithm; }
        public int getDefaultIterations() { return defaultIterations; }
        public void setDefaultIterations(int defaultIterations) { this.defaultIterations = defaultIterations; }
        public int getMinIterations() { return minIterations; }
        public void setMinIterations(int minIterations) { this.minIterations = minIterations; }
    }

    public static class Upload {
        private String basePath = "attachments";
        private int maxFileSizeMb = 10;
        private int maxPerEntry = 20;
        private int unattachedTtlHours = 24;

        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
        public int getMaxPerEntry() { return maxPerEntry; }
        public void setMaxPerEntry(int maxPerEntry) { this.maxPerEntry = maxPerEntry; }
        public int getUnattachedTtlHours() { return unattachedTtlHours; }
        public void setUnattachedTtlHours(int unattachedTtlHours) { this.unattachedTtlHours = unattachedTtlHours; }
    }

    public static class RateLimit {
        private int loginPerMinute = 5;
        private int registerPerHour = 3;
        private int recoveryPerMinute = 5;
        private int apiPerMinute = 60;
        private int attachmentPerMinute = 10;

        public int getLoginPerMinute() { return loginPerMinute; }
        public void setLoginPerMinute(int loginPerMinute) { this.loginPerMinute = loginPerMinute; }
        public int getRegisterPerHour() { return registerPerHour; }
        public void setRegisterPerHour(int registerPerHour) { this.registerPerHour = registerPerHour; }
        public int getRecoveryPerMinute() { return recoveryPerMinute; }
        public void setRecoveryPerMinute(int recoveryPerMinute) { this.recoveryPerMinute = recoveryPerMinute; }
        public int getApiPerMinute() { return apiPerMinute; }
        public void setApiPerMinute(int apiPerMinute) { this.apiPerMinute = apiPerMinute; }
        public int getAttachmentPerMinute() { return attachmentPerMinute; }
        public void setAttachmentPerMinute(int attachmentPerMinute) { this.attachmentPerMinute = attachmentPerMinute; }
    }
}
