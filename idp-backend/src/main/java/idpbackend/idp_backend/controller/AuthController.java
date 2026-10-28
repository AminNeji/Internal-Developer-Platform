package idpbackend.idp_backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    // GET /api/me
    // The frontend calls this on startup: 200 = logged in (show the portal), 401 = show "Sign in".
    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal OAuth2User user, CsrfToken csrfToken) {
        // reading the token makes Spring write the XSRF-TOKEN cookie; without it the cookie
        // only appears after the first POST has already been rejected
        csrfToken.getToken();

        Map<String, String> userInfo = Map.of(
                "login", user.getAttribute("login"),
                "avatarUrl", user.getAttribute("avatar_url")
        );

        return userInfo;
    }
}
