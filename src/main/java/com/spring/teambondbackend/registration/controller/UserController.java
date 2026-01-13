package com.spring.teambondbackend.registration.controller;

import com.spring.teambondbackend.OAuth2.util.EncryptionUtil;
import com.spring.teambondbackend.rabbitmq.producer.RabbitMqProducer;
import com.spring.teambondbackend.OAuth2.util.JwtUtil;
import com.spring.teambondbackend.recommendation.controllers.FrameworkController;
import com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest;
import com.spring.teambondbackend.registration.model.User;
import com.spring.teambondbackend.registration.repository.UserRepository;
import com.spring.teambondbackend.registration.service.CodeChefScraperService;
import com.spring.teambondbackend.registration.dto.CodeChefStatsDto;
import com.spring.teambondbackend.registration.service.CodeforcesScraperService;
import com.spring.teambondbackend.registration.dto.CodeforcesStatsDto;
import com.spring.teambondbackend.registration.service.UserService;
import com.spring.teambondbackend.analysis.service.GithubAnalysisService;
import com.spring.teambondbackend.analysis.dto.DeveloperEvaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.teambondbackend.registration.exception.InvalidCredentialsException;
import com.spring.teambondbackend.subscription.model.PaymentOrder;
import com.spring.teambondbackend.subscription.repository.PaymentOrderRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import com.razorpay.RazorpayClient;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse; // For HttpServletResponse parameter
import jakarta.servlet.http.Cookie; // For creating and manipulating cookies
import org.springframework.http.ResponseEntity; // For ResponseEntity return type
import org.springframework.web.bind.annotation.PostMapping; // For @PostMapping annotation
import org.springframework.web.bind.annotation.RequestBody; // For @RequestBody annotation

// Also import your User class and jwtUtil as per your project structure

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    @Value("${JWT_SECRET_KEY}")
    private String secretKey;
    private final UserService userService;
    private final FrameworkController frameworkController;
    private final PaymentOrderRepository paymentOrderRepository;
    private final JwtUtil jwtUtil;
    private final RabbitMqProducer rabbitMqProducer;

    private final UserRepository userRepository;
    private final CodeChefScraperService codeChefScraperService;
    private final CodeforcesScraperService codeforcesScraperService;
    private final GithubAnalysisService githubAnalysisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Value("${razorpay.key_id}")
    private String key_id;

    @Value("${razorpay.key_secret}")
    private String key_secret;

    @GetMapping("/me")
    public ResponseEntity<?> getUsersDetailsFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        System.out.println(cookies);
        String token = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                System.out.println("Name : " + cookie.getName());
                System.out.println("Value : " + cookie.getValue());
                if (cookie.getName().equals("jwtToken")) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            Claims claims = jwtUtil.validateToken(token);

            String id = claims.get("id", String.class);
            String username = claims.get("username", String.class);
            String email = claims.get("email", String.class);
            String status = claims.get("status", String.class);

            System.out.println("Username from token: " + username);
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            User fullUser = userOpt.get();

            Map<String, String> userMap = Map.of(
                    "id", id,
                    "email", email,
                    "username", username,
                    "status", fullUser.getStatus(),
                    "encryptedPrivateKey",
                    fullUser.getEncryptedPrivateKey() != null ? fullUser.getEncryptedPrivateKey() : "",
                    "privateKeyIv", fullUser.getPrivateKeyIv() != null ? fullUser.getPrivateKeyIv() : "");

            return ResponseEntity.ok(userMap);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
    }

    // Register endpoint
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody User user, HttpServletResponse response,
            HttpServletRequest request) {
        try {
            System.out.println("In registerUser method");
            // Your existing user save/update logic
            Optional<User> existingUser = userRepository.findById(user.getId());
            User savedUser;
            User u = null;
            if (existingUser.isPresent()) {
                u = existingUser.get();
                u.setUsername(user.getUsername());
                u.setPassword(user.getPassword());
                u.setDisplayName(user.getDisplayName());
                u.setEmail(user.getEmail());
                u.setLeetcodeUsername(user.getLeetcodeUsername());
                u.setCodechefUsername(user.getCodechefUsername());
                // Update githubUsername if provided
                if (user.getGithubUsername() != null && !user.getGithubUsername().trim().isEmpty()) {
                    u.setGithubUsername(user.getGithubUsername());
                }
                // Save the public key from request
                // System.out.println(user.getRsaPublicKey());
                String encryptPublickey = EncryptionUtil.encrypt(user.getRsaPublicKey(), secretKey);
                u.setRsaPublicKey(encryptPublickey);
                u.setEncryptedPrivateKey(user.getEncryptedPrivateKey());
                u.setPrivateKeyIv(user.getPrivateKeyIv());
                savedUser = userRepository.save(u);
            } else {
                savedUser = userRepository.save(user);
            }

            // Trigger Gemini AI Analysis if githubUsername is provided and analysis doesn't
            // exist
            if (savedUser.getGithubUsername() != null && !savedUser.getGithubUsername().trim().isEmpty()) {
                if (savedUser.getGeminiAnalysis() == null || savedUser.getGeminiAnalysis().trim().isEmpty()) {
                    try {
                        System.out.println("Triggering Gemini AI Analysis for: " + savedUser.getGithubUsername());
                        DeveloperEvaluation analysis = githubAnalysisService
                                .analyzeDeveloper(savedUser.getGithubUsername());
                        // Store as JSON string
                        String analysisJson = objectMapper.writeValueAsString(analysis);
                        savedUser.setGeminiAnalysis(analysisJson);
                        userRepository.save(savedUser);
                        System.out.println(
                                "Gemini AI Analysis completed and stored for: " + savedUser.getGithubUsername());
                    } catch (Exception e) {
                        System.err.println("Failed to generate Gemini analysis during registration: " + e.getMessage());
                        // Don't fail registration if analysis fails - just log the error
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Gemini analysis already exists for: " + savedUser.getGithubUsername());
                }
            }

            // Generate JWT token
            String token = jwtUtil.generateToken(
                    savedUser.getId(),
                    savedUser.getUsername(),
                    savedUser.getEmail(),
                    savedUser.getStatus());
            // Log JWT token to console
            System.out.println("Generated JWT Token: " + token);
            // Set JWT token as HttpOnly, Secure cookie with SameSite=Strict
            Cookie cookie = new Cookie("jwtToken", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(request.isSecure()); // Set secure only if request is HTTPS
            cookie.setPath("/");
            cookie.setMaxAge(86400);
            response.addCookie(cookie);
            System.out.println("Hello" + response.getHeader("Set-Cookie"));
            // Return user info (without token in body)
            GithubScoreRequest githubScoreRequest = new GithubScoreRequest();
            githubScoreRequest.setUsername(user.getUsername());
            githubScoreRequest.setEmail(user.getEmail());
            // Decrypt token when needed
            String decryptedToken = EncryptionUtil.decrypt(u.getGithubAccessToken(), secretKey);
            githubScoreRequest.setAccessToken(decryptedToken);
            System.out.println("decrepted github access token" + decryptedToken);
            // rabbitMqProducer.sendUserToQueue(githubScoreRequest);
            return ResponseEntity.ok(savedUser);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server Error: " + e.getMessage());
        }
    }

    // Login endpoint .... No longer required
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginRequest) {
        try {
            User authenticatedUser = userService.authenticateUser(
                    loginRequest.getUsername(), loginRequest.getPassword());
            return ResponseEntity.ok(authenticatedUser);
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Clear SecurityContext
        SecurityContextHolder.clearContext();

        // Invalidate HTTP session if it exists
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Expire the JWT cookie
        String expiredCookie = "jwtToken=; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=0";
        response.addHeader("Set-Cookie", expiredCookie);

        return ResponseEntity.ok("Logged out successfully");
    }

    // Get user details
    @GetMapping("/{username}")
    public ResponseEntity<?> getUserDetails(@PathVariable java.lang.String username) {
        try {
            User user = userService.getUserByUsername(username);
            // System.out.println(user);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("User not found.");
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{username}")
    public ResponseEntity<?> updateUserByUsername(@RequestBody User user, @PathVariable java.lang.String username) {
        User user1 = userService.updateUser(user, username);
        if (user1 == null) {
            return ResponseEntity.badRequest().body("User not found.");
        }
        return ResponseEntity.ok(user1);
    }

    @PostMapping("/create_order")
    @ResponseBody
    public ResponseEntity<?> createOrder(@RequestBody Map<String, String> data) {
        try {
            System.out.println("Order created successfully");

            int amt = Integer.parseInt(data.get("amount").toString());
            var client = new RazorpayClient(key_id, key_secret);
            // RazorpayClient("Key_id","key_secret");

            JSONObject ob = new JSONObject();
            ob.put("amount", amt * 100);
            ob.put("currency", "INR");
            ob.put("receipt", "order_RC_123456789");

            // Creating order
            Order order = client.orders.create(ob);
            System.out.println(order);

            // save this order into database...
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setAmount(order.get("amount") + "");
            paymentOrder.setOrderId(order.get("id")); // correct key
            paymentOrder.setStatus("created");
            paymentOrder.setUserId(data.get("userId").toString());

            Optional<User> user = userRepository.findById(data.get("userId").toString());
            if (user.isPresent()) {
                user.get().setStatus("created");
                userRepository.save(user.get());
            }

            paymentOrderRepository.save(paymentOrder);

            return ResponseEntity.ok(order.toString()); // Return order details
        } catch (RazorpayException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Razorpay Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/update_order")
    public ResponseEntity<?> updateOrder(@RequestBody Map<String, Object> data, HttpServletResponse response)
            throws RazorpayException {

        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(data.get("order_id").toString());
        paymentOrder.setPaymentId(data.get("payment_id").toString());
        paymentOrder.setStatus(data.get("status").toString());
        Optional<User> user = userRepository.findById(data.get("userId").toString());
        if (user.isPresent()) {
            User updatedUser = user.get();
            updatedUser.setStatus("paid");
            userRepository.save(updatedUser);

            // Generate new JWT token with updated status
            String newToken = jwtUtil.generateToken(
                    updatedUser.getId(),
                    updatedUser.getUsername(),
                    updatedUser.getEmail(),
                    updatedUser.getStatus() // now "paid"
            );
            // Log JWT token to console
            System.out.println("updated JWT Token: " + newToken);
            // Set the new JWT token as a cookie
            String cookieValue = "jwtToken=" + newToken
                    + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=86400"; // Secure removed for localhost
                                                                            // compatibility
            response.addHeader("Set-Cookie", cookieValue);
        }
        paymentOrder.setUserId(data.get("userId").toString());
        paymentOrderRepository.save(paymentOrder);

        System.out.println(data);
        return ResponseEntity.ok(Map.of("msg", "updated status successfully"));
    }

    @GetMapping("/get_status/{username}")
    public ResponseEntity<?> getUserStatus(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(Map.of("status", user.getStatus()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) {
        try {
            String payload = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            String signature = request.getHeader("X-Razorpay-Signature");

            if (!verifySignature(payload, signature, webhookSecret)) {
                return ResponseEntity.status(400).body("Invalid signature");
            }

            // Process webhook asynchronously in a new thread
            new Thread(() -> processWebhookPayload(payload)).start();

            // Return 200 OK immediately
            return ResponseEntity.ok("Webhook received");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Webhook error: " + e.getMessage());
        }
    }

    private void processWebhookPayload(String payload) {
        try {
            JSONObject webhookPayload = new JSONObject(payload);
            String paymentId = webhookPayload.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity")
                    .getString("id");

            String orderId = webhookPayload.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity")
                    .getString("order_id");

            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderId);

            // Avoid duplicate update if already paid
            if (paymentOrder != null && !"paid".equalsIgnoreCase(paymentOrder.getStatus())) {
                paymentOrder.setPaymentId(paymentId);
                paymentOrder.setStatus("paid");
                paymentOrderRepository.save(paymentOrder);

                Optional<User> user = userRepository.findById(paymentOrder.getUserId());
                user.ifPresent(u -> {
                    u.setStatus("paid");
                    userRepository.save(u);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean verifySignature(String payload, String actualSignature, String secret) {
        try {
            String computedSignature = hmacSHA256(payload, secret);
            return computedSignature.equals(actualSignature);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String hmacSHA256(String data, String secret) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes());
        return new String(Hex.encodeHex(hash));
    }

    @GetMapping("/public_key/{username}")
    public ResponseEntity<?> getPublicKey(@PathVariable String username) {
        try {
            User user = userService.getUserByUsername(username);
            if (user != null && user.getRsaPublicKey() != null) {
                String decrypyPublickey = EncryptionUtil.decrypt(user.getRsaPublicKey(), secretKey);
                return ResponseEntity.ok(decrypyPublickey);
            } else {
                return ResponseEntity.status(404).body("User or public key not found");
            }
        } catch (Exception e) {
            return ResponseEntity.status(404).body("User not found");
        }
    }

    @PostMapping("/update_public_key")
    public ResponseEntity<?> updatePublicKey(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String publicKey = payload.get("publicKey");

        if (username == null || publicKey == null) {
            return ResponseEntity.badRequest().body("Username and publicKey are required");
        }

        try {
            User user = userService.getUserByUsername(username);
            String encryptPublickey = EncryptionUtil.encrypt(publicKey, secretKey);
            user.setRsaPublicKey(encryptPublickey);
            userRepository.save(user);
            return ResponseEntity.ok("Public key updated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error updating public key: " + e.getMessage());
        }
    }

    @PostMapping("/update_private_key")
    public ResponseEntity<?> updatePrivateKey(@RequestBody Map<String, String> payload) {
        try {
            String username = payload.get("username");
            String encryptedPrivateKey = payload.get("encryptedPrivateKey");
            String privateKeyIv = payload.get("privateKeyIv");

            if (username == null || encryptedPrivateKey == null || privateKeyIv == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username, encryptedPrivateKey, and privateKeyIv are required"));
            }

            userService.updateEncryptedPrivateKey(username, encryptedPrivateKey, privateKeyIv);
            return ResponseEntity.ok(Map.of("message", "Private key updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/update_recovery_key")
    public ResponseEntity<?> updateRecoveryKey(@RequestBody Map<String, String> payload) {
        try {
            String username = payload.get("username");
            String encryptedRecoveryKey = payload.get("encryptedRecoveryPrivateKey");
            String recoveryKeyIv = payload.get("recoveryKeyIv");

            if (username == null || encryptedRecoveryKey == null || recoveryKeyIv == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username, encryptedRecoveryPrivateKey, and recoveryKeyIv are required"));
            }

            userService.updateRecoveryPrivateKey(username, encryptedRecoveryKey, recoveryKeyIv);
            return ResponseEntity.ok(Map.of("message", "Recovery key updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public String getCurrentUserId() {
        return this.userService.getCurrentUserId();
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("ping");
    }

    @GetMapping("/codechef/{username}")
    public ResponseEntity<?> getCodeChefStats(@PathVariable String username) {
        try {
            return ResponseEntity.ok(codeChefScraperService.scrapeUserStats(username));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching CodeChef stats: " + e.getMessage());
        }
    }

    @GetMapping("/codeforces/{username}")
    public ResponseEntity<?> getCodeforcesStats(@PathVariable String username) {
        try {
            return ResponseEntity.ok(codeforcesScraperService.scrapeUserStats(username));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching Codeforces stats: " + e.getMessage());
        }
    }

    @PostMapping("/{username}/upgrade-premium")
    public ResponseEntity<?> upgradeToPremium(@PathVariable String username, HttpServletResponse response) {
        try {
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            user.setStatus("paid");
            userRepository.save(user);

            // Generate new JWT token with updated status
            String newToken = jwtUtil.generateToken(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getStatus() // now "paid"
            );

            // Set the new JWT token as a cookie
            Cookie cookie = new Cookie("jwtToken", newToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(false); // Set to true in production
            cookie.setPath("/");
            cookie.setMaxAge(86400);
            response.addCookie(cookie);

            return ResponseEntity.ok(Map.of("message", "Upgraded to premium successfully", "user", user));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error upgrading user: " + e.getMessage());
        }
    }
}
