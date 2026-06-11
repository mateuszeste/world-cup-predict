package com.worldcup.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lekki, publiczny endpoint sprawdzany przez frontend przy starcie, by wykryc
 * "budzenie" usypianej instancji (np. darmowy plan Render) i pokazac ekran ladowania.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }
}
