# ⚽ MŚ 2026 — Predyktor wyników

Aplikacja do typowania wyników meczów fazy grupowej Mistrzostw Świata 2026.
Obok każdej drużyny wyświetlana jest jej flaga. Każdy zakłada własne konto i ma prywatne typy.

**Stack:** Java 21+ • Spring Boot 3.5 • Spring Data JPA • baza H2 (plik) • JWT (logowanie) • React 18 (frontend serwowany przez backend).

## Funkcje

- **Rejestracja i logowanie (JWT)** — każdy zakłada konto (nazwa wpisywana ręcznie, hasło hashowane PBKDF2). Każdy widzi i edytuje **wyłącznie własne typy** — prywatność egzekwowana po stronie serwera.
- **Prawdziwy terminarz** fazy grupowej MŚ 2026 (72 mecze) wg oficjalnego losowania z 5.12.2025.
- Mecze **ułożone według dni** rozgrywania (11–27 czerwca 2026) z nagłówkiem dnia tygodnia.
- **Godziny rozpoczęcia** w czasie polskim (z oznaczeniem kolejnego dnia dla meczów nad ranem) oraz pomocniczo w czasie wschodnim USA (ET). Strefy i zmianę czasu liczy przeglądarka z zapisanego momentu UTC.
- **Typowanie tylko do rozpoczęcia meczu** — po pierwszym gwizdku pola się blokują (kłódka), a backend odrzuca każdą próbę zmiany (HTTP 403). Blokada aktualizuje się na żywo (co 30 s).
- Flagi państw pobierane z [flagcdn.com](https://flagcdn.com), znacznik grupy przy każdym meczu.
- Filtr po grupach (A–L), licznik uzupełnionych typów.
- **Ranking** — zakładka z listą wszystkich zarejestrowanych użytkowników i ich punktami (na start każdy ma 0 punktów).
- **Automatyczne przyznawanie punktów** — co 5 minut backend sprawdza zakończone mecze (kickoff + 2h)
  w darmowym API [TheSportsDB](https://www.thesportsdb.com/api.php) i wpisuje rzeczywisty wynik.
  Za **dokładny wynik** użytkownik dostaje **3 punkty**, za **trafiony wynik meczu** (1x2: zwycięstwo
  gospodarzy / gości / remis) **1 punkt**. Wynik meczu i zdobyte punkty pokazują się przy każdym meczu.
- **Typ na mistrza turnieju** — w zakładce „Faza pucharowa" każdy wybiera z listy 48 drużyn tę,
  która jego zdaniem wygra MŚ 2026. Typ można zmieniać do startu pierwszego meczu turnieju
  (11.06.2026), później jest zablokowany. Po finale (19.07.2026) backend automatycznie ustala
  zwycięzcę w TheSportsDB i jednorazowo dolicza **15 punktów** każdemu, kto trafił.

## Konta

Brak kont na starcie — przy pierwszym wejściu kliknij **„Zarejestruj się"**, podaj nazwę (2–20 znaków)
i hasło (min. 4 znaki). Nazwy są unikalne (bez względu na wielkość liter). Po rejestracji następuje
automatyczne zalogowanie. Konta i typy zapisują się w bazie H2 (`./data`).

## Uruchomienie

Wymagany tylko **JDK 21+** i **Maven** (Node NIE jest potrzebny — React ładuje się z CDN).

```powershell
mvn spring-boot:run
```

Następnie otwórz **http://localhost:8080** w przeglądarce.

> Baza zapisuje się do `./data/worldcup.mv.db`. Aby zacząć od zera, usuń folder `data/`.
> Konsola H2: http://localhost:8080/h2-console (URL: `jdbc:h2:file:./data/worldcup`, user: `sa`, bez hasła).

## API REST

Wszystkie `/api/matches*` wymagają nagłówka `Authorization: Bearer <token>` (inaczej **401**).

| Metoda | Ścieżka                | Opis                                              |
|--------|------------------------|---------------------------------------------------|
| POST   | `/api/auth/register`   | `{ "username": "...", "password": "..." }` → `{ token, username }` lub **400** (np. nazwa zajęta) |
| POST   | `/api/auth/login`      | `{ "username": "...", "password": "..." }` → `{ token, username }` lub **401** |
| GET    | `/api/matches`         | Mecze wraz z **typami zalogowanego** użytkownika (nigdy cudzymi) |
| PUT    | `/api/matches/{id}`    | Zapis własnego typu `{ "score1": 2, "score2": 1 }` (null/null = wyczyść). Po rozpoczęciu meczu **403**. |
| GET    | `/api/leaderboard`     | Ranking wszystkich zarejestrowanych użytkowników: `[{ "username": "...", "points": 0 }, ...]`, posortowany malejąco wg punktów |
| POST   | `/api/results/refresh` | Wymusza natychmiastowe sprawdzenie wyników (normalnie dzieje się to automatycznie co 5 minut) |
| GET    | `/api/champion`        | Lista drużyn, własny typ na mistrza, status blokady i (po finale) rzeczywisty mistrz + zdobyte punkty |
| PUT    | `/api/champion`        | Zapis własnego typu na mistrza `{ "code": "br" }` (`code: null` = wyczyść). Po starcie turnieju **403** |

## Struktura

```
src/main/java/com/worldcup/
  WorldCupApplication.java     # punkt wejścia
  model/Match.java             # encja meczu (wspólny terminarz + rzeczywisty wynik po zakończeniu)
  model/Prediction.java        # typ konkretnego użytkownika (username + mecz + wynik)
  model/User.java              # konto użytkownika (nazwa + hash hasła + punkty rankingowe + typ na mistrza)
  model/TournamentState.java   # globalny stan turnieju (mistrz po finale, czy punkty rozdane)
  repository/                  # MatchRepository, PredictionRepository, UserRepository, TournamentStateRepository
  service/DataSeeder.java      # wpisuje prawdziwy terminarz 72 meczow przy pierwszym starcie
  service/Teams.java           # wspólny słownik 48 drużyn (kody flag + nazwy angielskie)
  service/UserService.java     # rejestracja + logowanie (walidacja, unikalność)
  service/PasswordHasher.java  # hashowanie hasła PBKDF2 (bez zewnętrznych zależności)
  service/JwtService.java      # generowanie / weryfikacja tokenów JWT
  service/ResultFetchService.java # co 5 min pobiera wyniki z TheSportsDB, przyznaje punkty i ustala mistrza turnieju
  service/ScoringService.java  # logika punktacji typów (3 / 1 / 0 pkt, 15 pkt za mistrza)
  controller/AuthController     # rejestracja + logowanie
  controller/MatchController    # mecze + typy (per użytkownik), blokada po starcie, ranking, typ na mistrza, odświeżanie wyników
  dto/                         # MatchView, ResultRequest, LoginRequest, LeaderboardEntry, ChampionView, ChampionRequest, TeamOption
src/main/resources/static/     # frontend React (index.html, app.js, styles.css)
```

## Wdrożenie na Render + Supabase

Projekt zawiera `Dockerfile` oraz `render.yaml` gotowe do wdrożenia na [Render](https://render.com),
z trwałą bazą danych [Supabase](https://supabase.com) (Postgres, darmowy plan, 500 MB):

1. Załóż projekt na Supabase (region jak najbliżej Render, np. Frankfurt).
2. W **Project Settings → Database → Connection pooling** skopiuj connection string
   trybu **Session pooler** (port 6543, host typu `aws-0-<region>.pooler.supabase.com`).
   Połączenie bezpośrednie (port 5432, `db.xxxx.supabase.co`) jest IPv6-only i **nie
   zadziała** z Render.
3. Wrzuć repozytorium na GitHub i w Render wybierz **New → Blueprint**, wskazując to
   repozytorium (Render odczyta `render.yaml` automatycznie). Albo **New → Web Service**
   z `runtime: docker`.
4. Render sam ustawi `PORT` i wygeneruje losowy `APP_JWT_SECRET`. Ręcznie ustaw w
   zakładce **Environment** (sekrety, nie trafiają do `render.yaml`):
   - `DB_URL` = `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require`
   - `DB_USERNAME` = `postgres.<project-ref>`
   - `DB_PASSWORD` = hasło bazy ustawione przy tworzeniu projektu Supabase
5. Tabele tworzą się automatycznie przy pierwszym starcie (`spring.jpa.hibernate.ddl-auto=update`).
6. Lokalnie (bez tych zmiennych) aplikacja dalej działa na pliku H2 — nie trzeba nic
   dodatkowo konfigurować do developmentu.
7. Konsola H2 (`/h2-console`) jest domyślnie **wyłączona** w produkcji
   (`H2_CONSOLE_ENABLED=false`). Lokalnie włącz ją ustawiając tę zmienną na `true`.

## Dalszy rozwój (opcjonalnie)

Jeśli zainstalujesz Node.js, możesz przenieść frontend z `static/` do osobnego projektu Vite
i budować klasycznie (`npm run dev`). Obecny układ celowo nie wymaga Node — działa od razu.
