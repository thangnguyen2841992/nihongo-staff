package com.nihongo.staff.service.monitor.mysql;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class MysqlCredentialCipherTest {
    @Test void encryptsWithRandomIvAndRejectsTampering() {
        var key = Base64.getEncoder().encodeToString(new byte[32]);
        var cipher = new MysqlCredentialCipher(key);
        String first = cipher.encrypt("example-password");
        String second = cipher.encrypt("example-password");
        assertNotEquals(first, second);
        assertEquals("example-password", cipher.decrypt(first));
        byte[] altered = Base64.getDecoder().decode(first);
        altered[altered.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(altered)));
    }
}
