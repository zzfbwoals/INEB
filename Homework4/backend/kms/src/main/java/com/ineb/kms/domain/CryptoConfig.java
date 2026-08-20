package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 마스터키 유도 및 KCV 설정 (key-value 저장소).
 * salt·kcv는 비밀이 아니므로 Base64 문자열로 저장하며, DB와 함께 백업된다.
 */
@Entity
@Table(name = "crypto_config")
public class CryptoConfig {

    @Id
    @Column(name = "config_key", length = 50)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    protected CryptoConfig() {
    }

    public CryptoConfig(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValue() {
        return configValue;
    }
}
