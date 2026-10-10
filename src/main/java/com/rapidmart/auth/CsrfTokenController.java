package com.rapidmart.auth;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class CsrfTokenController {

    @GetMapping("/csrf")
    public Map<String, String> getCsrfToken(CsrfToken token) {
        if (token == null) {
            return Map.of("parameterName", "_csrf", "token", "", "headerName", "X-XSRF-TOKEN");
        }
        return Map.of(
                "parameterName", token.getParameterName(),
                "token", token.getToken(),
                "headerName", token.getHeaderName()
        );
    }
}
