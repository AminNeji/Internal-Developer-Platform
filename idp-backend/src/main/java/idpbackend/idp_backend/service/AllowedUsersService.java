package idpbackend.idp_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

// GitHub login alone lets in anyone with a GitHub account; this keeps it to the listed usernames
@Service
public class AllowedUsersService extends DefaultOAuth2UserService {

    @Value("${idp.allowed-users}")
    private List<String> allowedUsers;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // calls GitHub's /user endpoint with the access token Spring just received
        OAuth2User user = super.loadUser(userRequest);
        String login = user.getAttribute("login");

        if (!allowedUsers.contains(login)) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"),
                    login + " is not allowed to use the platform");
        }
        return user;
    }
}
