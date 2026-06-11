package com.worldcup.controller;

import com.worldcup.dto.LoginRequest;
import com.worldcup.service.JwtService;
import com.worldcup.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /** Rejestracja nowego uzytkownika; od razu loguje (zwraca token). */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest request) {
        try {
            String username = userService.register(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(tokenResponse(username));
        } catch (UserService.ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Logowanie: zwraca token JWT i kanoniczna nazwe uzytkownika albo 401. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String username = userService.authenticate(request.getUsername(), request.getPassword());
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Nieprawidłowy login lub hasło"));
        }
        return ResponseEntity.ok(tokenResponse(username));
    }

    private Map<String, String> tokenResponse(String username) {
        return Map.of("token", jwtService.generate(username), "username", username);
    }
}
