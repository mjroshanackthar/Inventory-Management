package com.inventory.service;

import com.inventory.dto.JwtResponse;
import com.inventory.model.User;
import com.inventory.repository.UserRepository;
import com.inventory.security.JwtUtils;
import com.inventory.security.UserDetailsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GoogleAuthService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final Pattern USERNAME_SAFE = Pattern.compile("[^a-zA-Z0-9]");

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    public GoogleAuthService(
            UserRepository userRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public JwtResponse authenticateWithGoogle(String idToken) {
        logger.info("Google Authentication initiated. Received token: {}", idToken);

        if (googleClientId == null || googleClientId.isBlank()) {
            logger.error("Google Authentication failed: app.google.client-id is not configured on the server");
            throw new IllegalStateException("Google sign-in is not configured on the server");
        }

        Map<String, Object> payload = verifyGoogleToken(idToken);
        logger.info("Successfully verified token with Google. Verification response payload: {}", payload);

        String email = asString(payload.get("email"));
        String emailVerified = asString(payload.get("email_verified"));
        String audience = asString(payload.get("aud"));
        String sub = asString(payload.get("sub"));
        String name = asString(payload.get("name"));
        String picture = asString(payload.get("picture"));

        logger.info("Extracted Google token fields: email={}, email_verified={}, aud={}, sub={}, name={}, picture={}", 
                email, emailVerified, audience, sub, name, picture);

        if (email == null || email.isBlank()) {
            logger.error("Google Authentication failed: Email claim is missing in token");
            throw new IllegalArgumentException("Google account email is missing");
        }
        if (!"true".equalsIgnoreCase(emailVerified)) {
            logger.error("Google Authentication failed: Email {} is not verified on Google side", email);
            throw new IllegalArgumentException("Google email is not verified");
        }

        String cleanedClientId = googleClientId.trim();
        logger.info("Comparing token audience '{}' with configured client-id '{}'", audience, cleanedClientId);
        if (!cleanedClientId.equals(audience)) {
            logger.error("Google Authentication failed: Client ID mismatch. Configured: {}, Token: {}", cleanedClientId, audience);
            throw new IllegalArgumentException("Invalid Google token (audience mismatch)");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            logger.info("Email {} not found in database. Automatically creating new user.", email);
            String username = generateUniqueUsername(name, email);
            String randomPassword = UUID.randomUUID().toString();
            user = new User(username, email, passwordEncoder.encode(randomPassword));
            user.setGoogleId(sub);
            user.setFullName(name);
            user.setProfilePicture(picture);
            user = userRepository.save(user);
            logger.info("Successfully created and saved new Google user: id={}, username={}, email={}", 
                    user.getId(), user.getUsername(), user.getEmail());
        } else {
            logger.info("User with email {} already exists. Proceeding with sign-in.", email);
            boolean updated = false;
            if (user.getGoogleId() == null) {
                user.setGoogleId(sub);
                updated = true;
            }
            if (user.getFullName() == null && name != null) {
                user.setFullName(name);
                updated = true;
            }
            if (user.getProfilePicture() == null && picture != null) {
                user.setProfilePicture(picture);
                updated = true;
            }
            if (updated) {
                user = userRepository.save(user);
                logger.info("Updated Google metadata for existing user: id={}", user.getId());
            }
        }

        logger.info("Generating internal JWT for user: id={}, email={}", user.getId(), user.getEmail());
        JwtResponse jwtResponse = buildJwtResponse(user);
        logger.info("Internal JWT successfully generated for user: id={}", user.getId());
        return jwtResponse;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyGoogleToken(String idToken) {
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        try {
            logger.info("Sending HTTP GET request to Google tokeninfo: {}", url);
            Map<String, Object> payload = restTemplate.getForObject(url, Map.class);
            if (payload == null) {
                logger.error("Google tokeninfo returned null payload");
                throw new IllegalArgumentException("Google tokeninfo returned an empty response");
            }
            if (payload.containsKey("error") || payload.containsKey("error_description")) {
                String error = asString(payload.get("error"));
                String desc = asString(payload.get("error_description"));
                logger.error("Google tokeninfo returned error: {} - {}", error, desc);
                throw new IllegalArgumentException("Invalid Google token: " + (desc != null ? desc : error));
            }
            return payload;
        } catch (Exception e) {
            logger.error("Exception occurred while calling Google tokeninfo endpoint", e);
            throw new IllegalArgumentException("Failed to verify Google token with Google's API: " + e.getMessage(), e);
        }
    }

    private JwtResponse buildJwtResponse(User user) {
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String jwt = jwtUtils.generateJwtToken(authentication);
        return new JwtResponse(jwt, user.getId(), user.getUsername(), user.getEmail(), List.of("ROLE_USER"));
    }

    private String generateUniqueUsername(String displayName, String email) {
        String base = USERNAME_SAFE.matcher(
                displayName != null && !displayName.isBlank()
                        ? displayName.replace(" ", "")
                        : email.substring(0, email.indexOf('@'))
        ).replaceAll("");

        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 45) {
            base = base.substring(0, 45);
        }

        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + suffix++;
        }
        return username;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
