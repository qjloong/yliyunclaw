package vip.mate.auth.yliyun.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configuration for the internal Yliyun runtime request verifier. */
@Component
@ConfigurationProperties(prefix = "mateclaw.runtime.yliyun")
public class YliyunRuntimeSecurityProperties {

    private boolean enabled = true;
    private String pathPrefix = "/api/internal/v1";
    private String serviceToken;
    private String internalToken;
    private String serviceSecret;
    private int clockSkewSeconds = 300;
    private int maxBodyBytes = 2 * 1024 * 1024;
    private int maxContextBytes = 16 * 1024;
    private boolean requireInternalToken = true;
    private SignatureVersion signatureVersion = SignatureVersion.CONTEXT_V1_1;
    private boolean allowLegacySignature = false;
    private LegacyHmacInput legacyHmacInput = LegacyHmacInput.RAW_CANONICAL;
    private Map<String, String> appEntitlements = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getServiceSecret() {
        return serviceSecret;
    }

    public void setServiceSecret(String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public int getMaxContextBytes() {
        return maxContextBytes;
    }

    public void setMaxContextBytes(int maxContextBytes) {
        this.maxContextBytes = maxContextBytes;
    }

    public boolean isRequireInternalToken() {
        return requireInternalToken;
    }

    public void setRequireInternalToken(boolean requireInternalToken) {
        this.requireInternalToken = requireInternalToken;
    }

    public SignatureVersion getSignatureVersion() {
        return signatureVersion;
    }

    public void setSignatureVersion(SignatureVersion signatureVersion) {
        this.signatureVersion = signatureVersion;
    }

    public boolean isAllowLegacySignature() {
        return allowLegacySignature;
    }

    public void setAllowLegacySignature(boolean allowLegacySignature) {
        this.allowLegacySignature = allowLegacySignature;
    }

    public LegacyHmacInput getLegacyHmacInput() {
        return legacyHmacInput;
    }

    public void setLegacyHmacInput(LegacyHmacInput legacyHmacInput) {
        this.legacyHmacInput = legacyHmacInput;
    }

    public Map<String, String> getAppEntitlements() {
        return appEntitlements;
    }

    public void setAppEntitlements(Map<String, String> appEntitlements) {
        this.appEntitlements = appEntitlements == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(appEntitlements);
    }

    public enum SignatureVersion {
        FROZEN_V1,
        CONTEXT_V1_1
    }

    public enum LegacyHmacInput {
        RAW_CANONICAL,
        BASE64_CANONICAL
    }
}
