package com.mycompany.maildesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias de la aplicación (prefijo {@code app}). Los valores se enlazan desde
 * application*.yml y, en última instancia, desde variables de entorno o el archivo .env.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private String initialAdminEmail = "";
    private String setupSecret = "";
    private String otpHmacSecret = "";
    private String otpHmacKeyFile = "./data/otp-hmac.key";
    private boolean allowGeneratedOtpKey = false;
    private String storageDir = "./data/storage";
    private Mail mail = new Mail();
    private Outbox outbox = new Outbox();
    private Attachments attachments = new Attachments();
    private Security security = new Security();

    public static class Mail {
        private String from = "";
        private String fromName = "MailDesk Pro";
        private boolean real = false;
        private int concurrency = 2;
        private int maxAttempts = 3;
        private long retryBackoffSeconds = 60;
        private int quotaPerUserPerDay = 200;
        private int maxRecipientsPerMessage = 50;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        public boolean isReal() { return real; }
        public void setReal(boolean real) { this.real = real; }
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getRetryBackoffSeconds() { return retryBackoffSeconds; }
        public void setRetryBackoffSeconds(long retryBackoffSeconds) { this.retryBackoffSeconds = retryBackoffSeconds; }
        public int getQuotaPerUserPerDay() { return quotaPerUserPerDay; }
        public void setQuotaPerUserPerDay(int quotaPerUserPerDay) { this.quotaPerUserPerDay = quotaPerUserPerDay; }
        public int getMaxRecipientsPerMessage() { return maxRecipientsPerMessage; }
        public void setMaxRecipientsPerMessage(int maxRecipientsPerMessage) { this.maxRecipientsPerMessage = maxRecipientsPerMessage; }
    }

    public static class Outbox {
        private boolean schedulerEnabled = true;
        private long pollDelayMs = 5000;
        private int batchSize = 10;

        public boolean isSchedulerEnabled() { return schedulerEnabled; }
        public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }
        public long getPollDelayMs() { return pollDelayMs; }
        public void setPollDelayMs(long pollDelayMs) { this.pollDelayMs = pollDelayMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Attachments {
        private int maxCount = 10;
        private long maxFileBytes = 10L * 1024 * 1024;
        private long maxTotalBytes = 25L * 1024 * 1024;

        public int getMaxCount() { return maxCount; }
        public void setMaxCount(int maxCount) { this.maxCount = maxCount; }
        public long getMaxFileBytes() { return maxFileBytes; }
        public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
        public long getMaxTotalBytes() { return maxTotalBytes; }
        public void setMaxTotalBytes(long maxTotalBytes) { this.maxTotalBytes = maxTotalBytes; }
    }

    public static class Security {
        private int otpTtlSeconds = 300;
        private int otpMaxAttempts = 5;
        private int otpResendIntervalSeconds = 60;
        private int otpMaxRequestsPerHour = 5;
        private int otpMaxRequestsPerIpPerHour = 20;
        private int loginMaxFailures = 5;
        private int loginLockMinutes = 15;
        private int loginMaxAttemptsPerIpPer15min = 30;
        private int resetTokenTtlMinutes = 30;
        private int inviteTtlHours = 72;
        private int resetMaxRequestsPerIpPerHour = 10;

        public int getOtpTtlSeconds() { return otpTtlSeconds; }
        public void setOtpTtlSeconds(int otpTtlSeconds) { this.otpTtlSeconds = otpTtlSeconds; }
        public int getOtpMaxAttempts() { return otpMaxAttempts; }
        public void setOtpMaxAttempts(int otpMaxAttempts) { this.otpMaxAttempts = otpMaxAttempts; }
        public int getOtpResendIntervalSeconds() { return otpResendIntervalSeconds; }
        public void setOtpResendIntervalSeconds(int otpResendIntervalSeconds) { this.otpResendIntervalSeconds = otpResendIntervalSeconds; }
        public int getOtpMaxRequestsPerHour() { return otpMaxRequestsPerHour; }
        public void setOtpMaxRequestsPerHour(int otpMaxRequestsPerHour) { this.otpMaxRequestsPerHour = otpMaxRequestsPerHour; }
        public int getOtpMaxRequestsPerIpPerHour() { return otpMaxRequestsPerIpPerHour; }
        public void setOtpMaxRequestsPerIpPerHour(int otpMaxRequestsPerIpPerHour) { this.otpMaxRequestsPerIpPerHour = otpMaxRequestsPerIpPerHour; }
        public int getLoginMaxFailures() { return loginMaxFailures; }
        public void setLoginMaxFailures(int loginMaxFailures) { this.loginMaxFailures = loginMaxFailures; }
        public int getLoginLockMinutes() { return loginLockMinutes; }
        public void setLoginLockMinutes(int loginLockMinutes) { this.loginLockMinutes = loginLockMinutes; }
        public int getLoginMaxAttemptsPerIpPer15min() { return loginMaxAttemptsPerIpPer15min; }
        public void setLoginMaxAttemptsPerIpPer15min(int v) { this.loginMaxAttemptsPerIpPer15min = v; }
        public int getResetTokenTtlMinutes() { return resetTokenTtlMinutes; }
        public void setResetTokenTtlMinutes(int resetTokenTtlMinutes) { this.resetTokenTtlMinutes = resetTokenTtlMinutes; }
        public int getInviteTtlHours() { return inviteTtlHours; }
        public void setInviteTtlHours(int inviteTtlHours) { this.inviteTtlHours = inviteTtlHours; }
        public int getResetMaxRequestsPerIpPerHour() { return resetMaxRequestsPerIpPerHour; }
        public void setResetMaxRequestsPerIpPerHour(int v) { this.resetMaxRequestsPerIpPerHour = v; }
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getInitialAdminEmail() { return initialAdminEmail; }
    public void setInitialAdminEmail(String initialAdminEmail) { this.initialAdminEmail = initialAdminEmail; }
    public String getSetupSecret() { return setupSecret; }
    public void setSetupSecret(String setupSecret) { this.setupSecret = setupSecret; }
    public String getOtpHmacSecret() { return otpHmacSecret; }
    public void setOtpHmacSecret(String otpHmacSecret) { this.otpHmacSecret = otpHmacSecret; }
    public String getOtpHmacKeyFile() { return otpHmacKeyFile; }
    public void setOtpHmacKeyFile(String otpHmacKeyFile) { this.otpHmacKeyFile = otpHmacKeyFile; }
    public boolean isAllowGeneratedOtpKey() { return allowGeneratedOtpKey; }
    public void setAllowGeneratedOtpKey(boolean allowGeneratedOtpKey) { this.allowGeneratedOtpKey = allowGeneratedOtpKey; }
    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
    public Mail getMail() { return mail; }
    public void setMail(Mail mail) { this.mail = mail; }
    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public Attachments getAttachments() { return attachments; }
    public void setAttachments(Attachments attachments) { this.attachments = attachments; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
}
