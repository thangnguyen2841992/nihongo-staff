package com.nihongo.staff.service.monitor.mysql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MysqlCredentialCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final String configuredKey;

    public MysqlCredentialCipher(@Value("${MONITORING_ENCRYPTION_KEY:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Không thể mã hóa mật khẩu MySQL.", e);
        }
    }

    public String decrypt(String value) {
        try {
            byte[] data = Base64.getDecoder().decode(value);
            if (data.length <= IV_BYTES + 16) throw new IllegalArgumentException("Invalid ciphertext");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Không thể giải mã mật khẩu MySQL; kiểm tra MONITORING_ENCRYPTION_KEY.", e);
        }
    }

    private SecretKeySpec key() {
        if (configuredKey == null || configuredKey.isBlank())
            throw new IllegalStateException("Thiếu MONITORING_ENCRYPTION_KEY trong file cấu hình local.");
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(configuredKey.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("MONITORING_ENCRYPTION_KEY không hợp lệ.", e); }
        if (decoded.length != 32) throw new IllegalStateException("MONITORING_ENCRYPTION_KEY phải dài 32 byte (Base64).");
        return new SecretKeySpec(decoded, "AES");
    }
}
