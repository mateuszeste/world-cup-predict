package com.worldcup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Konto uzytkownika utworzone przez rejestracje.
 * Tabela "app_user" - "user" jest slowem zarezerwowanym w H2.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private int points = 0;

    /** Kod ISO (flagcdn) typowanego mistrza turnieju, np. "br". Null = brak typu. */
    private String championPick;

    /** Imie i nazwisko typowanego krola strzelcow, np. "Kylian Mbappe". Null = brak typu. */
    private String topScorerPick;

    public User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getChampionPick() { return championPick; }
    public void setChampionPick(String championPick) { this.championPick = championPick; }

    public String getTopScorerPick() { return topScorerPick; }
    public void setTopScorerPick(String topScorerPick) { this.topScorerPick = topScorerPick; }
}
