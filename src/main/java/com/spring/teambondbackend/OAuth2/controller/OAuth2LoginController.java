package com.spring.teambondbackend.OAuth2.controller;

import com.spring.teambondbackend.OAuth2.util.EncryptionUtil;
import com.spring.teambondbackend.OAuth2.util.JwtUtil;
import com.spring.teambondbackend.registration.model.User;
import com.spring.teambondbackend.rabbitmq.producer.RabbitMqProducer;
import com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest;
import com.spring.teambondbackend.registration.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

@RestController
@RequestMapping("/oauth2")
public class OAuth2LoginController {

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RabbitMqProducer rabbitMqProducer;

    private static Logger logger = LoggerFactory.getLogger(OAuth2LoginController.class);

    @Value("${frontend.url}")
    private String url;

    @GetMapping("/success")
    public RedirectView oauth2Success(OAuth2AuthenticationToken authentication, HttpServletResponse response) {
        System.out.println("==== /oauth2/success endpoint HIT ====");

        OAuth2User oAuth2User = authentication.getPrincipal();
        int githubId = oAuth2User.getAttribute("id");
        String githubUsername = oAuth2User.getAttribute("login");
        // String email = oAuth2User.getAttribute("email");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");

        Optional<User> optionalUser = userRepository.findByGithubId(githubId);
        User user;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();
        } else {
            user = new User();
            user.setGithubId(githubId);
            user.setGithubUsername(githubUsername);
            user.setGithubAvatarUrl(avatarUrl);
            // user.setEmail(email != null ? email : githubUsername + "@github.com");
            user.setProfileComplete(false);
            userRepository.save(user);
        }

        user.evaluateProfileCompletion();

        if (!user.isProfileComplete()) {

            // redirect to frontend register form
            String redirectUrl = String.format(
                    url + "/register?oauth=true&username=%s&id=%s",
                    githubUsername, user.getId());
            RedirectView redirectView = new RedirectView(redirectUrl);
            redirectView.setExposeModelAttributes(false);
            return redirectView;
        }

        // Generate JWT Token using status
        String jwtToken = JwtUtil.generateToken(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus() // keep status
        );

        System.out.println("Generated JWT Token: " + jwtToken);

        Cookie cookie = new Cookie("jwtToken", jwtToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // only if HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        response.addCookie(cookie);

        logger.info("OAuth2 User Attributes: " + oAuth2User.getAttributes());
        GithubScoreRequest githubScoreRequest = new GithubScoreRequest();
        githubScoreRequest.setEmail(user.getEmail());
        // Decrypt token when needed
        String decryptedToken = EncryptionUtil.decrypt(user.getGithubAccessToken(), secretKey);
        githubScoreRequest.setAccessToken(decryptedToken);
        githubScoreRequest.setUsername(user.getGithubUsername());
        System.out.println("decrepted github access token" + decryptedToken);
        System.out.println(githubScoreRequest);
        logger.info("Github Framework: " + githubScoreRequest);
        rabbitMqProducer.sendUserToQueue(githubScoreRequest);

        // String redirectUrl = String.format(
        // url + "/dashboard?username=%s&userId=%s&githubUsername=%s&status=%s",
        // user.getUsername(), user.getId(), user.getGithubUsername(), user.getStatus()
        // );
        String redirectUrl = String.format(url + "/dashboard");

        RedirectView redirectView = new RedirectView(redirectUrl);
        redirectView.setExposeModelAttributes(false);
        return redirectView;
    }
}
