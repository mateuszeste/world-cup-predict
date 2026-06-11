package com.worldcup.service;

import com.worldcup.model.User;
import com.worldcup.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Rejestracja i logowanie uzytkownikow (konta przechowywane w bazie).
 * Nazwy uzytkownikow sa unikalne i porownywane bez wzgledu na wielkosc liter.
 */
@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** Walidacja przy nieprawidlowych danych rejestracji/logowania. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** Rejestruje nowego uzytkownika i zwraca jego (kanoniczna) nazwe. */
    public String register(String rawUsername, String password) {
        String username = rawUsername == null ? "" : rawUsername.trim();

        if (username.length() < 2 || username.length() > 20) {
            throw new ValidationException("Nazwa użytkownika musi mieć od 2 do 20 znaków");
        }
        if (!username.matches("[\\p{L}\\p{N}_ .\\-]+")) {
            throw new ValidationException("Nazwa może zawierać tylko litery, cyfry, spację i . _ -");
        }
        if (password == null || password.length() < 4) {
            throw new ValidationException("Hasło musi mieć co najmniej 4 znaki");
        }
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ValidationException("Użytkownik o tej nazwie już istnieje");
        }

        User user = repository.save(new User(username, PasswordHasher.hash(password)));
        return user.getUsername();
    }

    /** Zwraca kanoniczna nazwe uzytkownika przy poprawnym logowaniu; inaczej null. */
    public String authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        return repository.findByUsernameIgnoreCase(username.trim())
                .filter(u -> PasswordHasher.matches(password, u.getPasswordHash()))
                .map(User::getUsername)
                .orElse(null);
    }
}
