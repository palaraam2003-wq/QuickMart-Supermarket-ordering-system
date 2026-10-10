package com.rapidmart.auth;

import com.rapidmart.common.exception.BusinessException;
import java.util.Locale;

public final class LoginEmailRules {
    private LoginEmailRules() {}
    public static String validate(String email, boolean staff) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String pattern = staff ? "^[^@\\s]+@rapidmart\\.local$" : "^[^@\\s]+@[^@\\s]+\\.com$";
        if (value.length() > 254 || !value.matches(pattern)) {
            throw new BusinessException("INVALID_EMAIL", staff
                    ? "Staff email must end in @rapidmart.local."
                    : "Customer email must contain @ and end in .com.");
        }
        return value;
    }
}
