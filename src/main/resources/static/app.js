const { useState, useEffect, useMemo } = React;

const API = "/api";

// Fetch z dolaczonym tokenem JWT. Na 401 czysci sesje i wraca do logowania.
function api(path, options = {}) {
    const token = localStorage.getItem("wc_token");
    const headers = { ...(options.headers || {}) };
    if (token) headers["Authorization"] = "Bearer " + token;
    return fetch(path, { ...options, headers }).then((res) => {
        if (res.status === 401) {
            localStorage.removeItem("wc_token");
            localStorage.removeItem("wc_user");
            window.dispatchEvent(new Event("wc-logout"));
        }
        return res;
    });
}

function flagUrl(code) {
    return `https://flagcdn.com/w40/${code}.png`;
}

const DNI = ["niedziela", "poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota"];
const MIESIACE = ["stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
    "lipca", "sierpnia", "września", "października", "listopada", "grudnia"];

function formatDate(iso) {
    const [y, mo, d] = iso.split("-").map(Number);
    const dt = new Date(y, mo - 1, d);
    return { weekday: DNI[dt.getDay()], label: `${d} ${MIESIACE[mo - 1]} ${y}` };
}

function kickoffInfo(iso, slateDate) {
    if (!iso) return { pl: "—", et: "—", nextDay: null, plDate: "??.??" };
    const dt = new Date(iso);
    const pl = new Intl.DateTimeFormat("pl-PL",
        { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Warsaw" }).format(dt);
    const et = new Intl.DateTimeFormat("en-GB",
        { hour: "2-digit", minute: "2-digit", timeZone: "America/New_York" }).format(dt);
    const plIso = new Intl.DateTimeFormat("en-CA",
        { year: "numeric", month: "2-digit", day: "2-digit", timeZone: "Europe/Warsaw" }).format(dt);
    const [, mm, dd] = plIso.split("-");
    const plDate = `${dd}.${mm}`;
    let nextDay = null;
    if (plIso !== slateDate) {
        nextDay = plDate;
    }
    return { pl, et, nextDay, plDate };
}

function isLive(match) {
    const now = Date.now();
    const kickoff = new Date(match.kickoffUtc).getTime();
    const MATCH_DURATION_MS = 2 * 60 * 60 * 1000;
    const hasResult = match.actualScore1 != null && match.actualScore2 != null;
    return !hasResult && now >= kickoff && now <= kickoff + MATCH_DURATION_MS;
}

function meczeWord(n) {
    if (n === 1) return "mecz";
    const ten = n % 10, hundred = n % 100;
    if (ten >= 2 && ten <= 4 && (hundred < 12 || hundred > 14)) return "mecze";
    return "meczów";
}

function Flag({ code, name }) {
    return <img className="flag" src={flagUrl(code)} alt={name} title={name} loading="lazy" />;
}

const STAGE_LABELS = {
    R32: "1/16 finału", R16: "1/8 finału", QF: "Ćwierćfinały",
    SF: "Półfinały", "3P": "Mecz o 3. miejsce", F: "Finał"
};
const STAGE_ORDER = ["R32", "R16", "QF", "SF", "3P", "F"];

// ---- Pojedynczy mecz z edycja wyniku (PT + FT) ----
function MatchRow({ match, onSaved, showGroup = true }) {
    const [ht1, setHt1] = useState(match.htScore1 ?? "");
    const [ht2, setHt2] = useState(match.htScore2 ?? "");
    const [s1, setS1] = useState(match.score1 ?? "");
    const [s2, setS2] = useState(match.score2 ?? "");
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);

    useEffect(() => {
        setHt1(match.htScore1 ?? "");
        setHt2(match.htScore2 ?? "");
        setS1(match.score1 ?? "");
        setS2(match.score2 ?? "");
    }, [match.htScore1, match.htScore2, match.score1, match.score2]);

    const locked = new Date() >= new Date(match.kickoffUtc);
    const isTbd = match.team1Name === "TBD" || match.team2Name === "TBD";

    const dirty =
        String(ht1) !== String(match.htScore1 ?? "") ||
        String(ht2) !== String(match.htScore2 ?? "") ||
        String(s1) !== String(match.score1 ?? "") ||
        String(s2) !== String(match.score2 ?? "");
    const bothFilled = s1 !== "" && s2 !== "";

    async function save() {
        if (!bothFilled || locked) return;
        setSaving(true);
        await api(`${API}/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                score1: Number(s1), score2: Number(s2),
                htScore1: ht1 !== "" ? Number(ht1) : null,
                htScore2: ht2 !== "" ? Number(ht2) : null,
            }),
        });
        setSaving(false);
        setJustSaved(true);
        setTimeout(() => setJustSaved(false), 1500);
        onSaved();
    }

    async function clear() {
        setSaving(true);
        await api(`${API}/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ score1: null, score2: null }),
        });
        setHt1(""); setHt2(""); setS1(""); setS2("");
        setSaving(false);
        onSaved();
    }

    function onKey(e) { if (e.key === "Enter") save(); }

    const t = kickoffInfo(match.kickoffUtc, match.date);
    const hasActual = match.actualScore1 != null && match.actualScore2 != null;

    return (
        <div className={"match-row" + (match.played ? " played" : "") + (locked ? " locked" : "")}>
            <div className="match-group">
                <span className="kick">🕑 {match.phase === "KNOCKOUT" ? `${t.plDate} ` : ""}{t.pl} PL {t.et} ET{match.phase !== "KNOCKOUT" && t.nextDay ? ` (${t.nextDay})` : ""}</span>
                {isLive(match) && (
                    <span className="live-badge"><span className="live-ball">⚽</span>LIVE</span>
                )}
            </div>

            <div className="team home">
                <span className="name">{match.team1Name}</span>
                {!isTbd && <Flag code={match.team1Code} name={match.team1Name} />}
            </div>

            <div className="score-col">
                <div className="score-row ht-row">
                    <span className="score-label">PT</span>
                    <input type="number" min="0" value={ht1}
                           disabled={locked || isTbd} onKeyDown={onKey}
                           onChange={(e) => setHt1(e.target.value)} />
                    <span className="sep">:</span>
                    <input type="number" min="0" value={ht2}
                           disabled={locked || isTbd} onKeyDown={onKey}
                           onChange={(e) => setHt2(e.target.value)} />
                </div>
                <div className="score-row ft-row">
                    <span className="score-label">FT</span>
                    <input type="number" min="0" value={s1}
                           disabled={locked || isTbd} onKeyDown={onKey}
                           onChange={(e) => setS1(e.target.value)} />
                    <span className="sep">:</span>
                    <input type="number" min="0" value={s2}
                           disabled={locked || isTbd} onKeyDown={onKey}
                           onChange={(e) => setS2(e.target.value)} />
                </div>
            </div>

            <div className="team away">
                {!isTbd && <Flag code={match.team2Code} name={match.team2Name} />}
                <span className="name">{match.team2Name}</span>
            </div>

            <div className="row-actions">
                {isTbd ? (
                    <span className="locked-badge">⏳ Drużyny wyłonione po fazie grupowej</span>
                ) : locked ? (
                    <span className="locked-badge">🔒 Zakłady zamknięte — mecz się rozpoczął</span>
                ) : (
                    <React.Fragment>
                        <button className="btn btn-save" disabled={!bothFilled || !dirty || saving} onClick={save}>
                            {saving ? "Zapisywanie…" : "Zapisz"}
                        </button>
                        {match.played && (
                            <button className="btn btn-clear" disabled={saving} onClick={clear}>Wyczyść</button>
                        )}
                        {justSaved && <span className="saved-badge">✓ zapisano</span>}
                    </React.Fragment>
                )}
            </div>
        </div>
    );
}

// ---- Sekcja jednego dnia ----
function DayCard({ date, matches, onSaved, showGroup = true }) {
    const { weekday, label } = formatDate(date);
    return (
        <div className="day-card">
            <h2>
                <span className="weekday">{weekday}</span>
                <span className="day-label">{label}</span>
                <span className="day-count">{matches.length} {meczeWord(matches.length)}</span>
            </h2>
            {matches.map((m) => <MatchRow key={m.id} match={m} onSaved={onSaved} showGroup={showGroup} />)}
        </div>
    );
}

// ---- Ranking ----
function Leaderboard() {
    const [entries, setEntries] = useState(null);
    useEffect(() => {
        api(`${API}/leaderboard`).then((res) => { if (res.ok) res.json().then(setEntries); });
    }, []);

    if (entries === null) return <div className="loading">Ładowanie rankingu…</div>;

    return (
        <div className="leaderboard">
            <div className="table-responsive">
                <table className="leaderboard-table">
                    <thead><tr>
                        <th className="lb-rank">#</th>
                        <th>Użytkownik</th>
                        <th className="lb-stat">FT (5 pkt)</th>
                        <th className="lb-stat">Kierunek (2 pkt)</th>
                        <th className="lb-stat">PT (1 pkt)</th>
                        <th className="lb-stat">Bonus</th>
                        <th className="lb-points">Suma</th>
                    </tr></thead>
                    <tbody>
                        {entries.map((e, i) => (
                            <tr key={e.username}>
                                <td className="lb-rank">{i + 1}</td>
                                <td className="lb-username">{e.username}</td>
                                <td className="lb-stat">{e.exactHits}</td>
                                <td className="lb-stat">{e.directionHits}</td>
                                <td className="lb-stat">{e.htHits}</td>
                                <td className="lb-stat">{e.bonusPoints > 0 ? `+${e.bonusPoints}` : "0"}</td>
                                <td className="lb-points">{e.points}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="leaderboard-legend">
                <h3>💡 Zasady punktacji</h3>
                <div className="legend-grid">
                    <div className="legend-item">
                        <span className="legend-points">+5 pkt</span>
                        <span className="legend-desc">Dokładny wynik meczu (FT)</span>
                    </div>
                    <div className="legend-item">
                        <span className="legend-points">+2 pkt</span>
                        <span className="legend-desc">Kierunek meczu (zwycięstwo / remis)</span>
                    </div>
                    <div className="legend-item">
                        <span className="legend-points">+1 pkt</span>
                        <span className="legend-desc">Premia za dokładny wynik do przerwy (PT)</span>
                    </div>
                    <div className="legend-item">
                        <span className="legend-points">+15 pkt</span>
                        <span className="legend-desc">Trafiony Mistrz lub Król strzelców</span>
                    </div>
                </div>
            </div>
        </div>
    );
}

// ---- Zakładka Typy (wszystkie typy wszystkich użytkowników) ----
function PredictionsTab() {
    const [data, setData] = useState(null);
    const [filter, setFilter] = useState("matches");

    useEffect(() => {
        api(`${API}/predictions`).then((res) => {
            if (res.ok) res.json().then(setData);
        });
    }, []);

    if (data === null) return <div className="loading">Ładowanie typów…</div>;

    const { matchPredictions, championPicks, topScorerPicks } = data;

    const groupMatches = matchPredictions.filter((m) => m.phase === "GROUP");
    const knockoutMatches = matchPredictions.filter((m) => m.phase === "KNOCKOUT");

    return (
        <div className="predictions-tab">
            <div className="group-filter">
                <button className={"chip wide" + (filter === "matches" ? " active" : "")}
                        onClick={() => setFilter("matches")}>Mecze</button>
                <button className={"chip wide" + (filter === "champion" ? " active" : "")}
                        onClick={() => setFilter("champion")}>Mistrz turnieju</button>
                <button className={"chip wide" + (filter === "scorer" ? " active" : "")}
                        onClick={() => setFilter("scorer")}>Król strzelców</button>
            </div>

            {filter === "matches" && (
                <div className="predictions-matches">
                    <details className="group-phase-details">
                        <summary><h2>Faza grupowa</h2></summary>
                        {groupMatches.length === 0 && <p className="no-data">Brak typów na mecze grupowe.</p>}
                        {groupMatches.map((m) => (
                            <PredictionMatchCard key={m.matchId} match={m} />
                        ))}
                    </details>
                    <h2>Faza pucharowa</h2>
                    {knockoutMatches.length === 0 && <p className="no-data">Brak typów na mecze pucharowe.</p>}
                    {knockoutMatches.map((m) => (
                        <PredictionMatchCard key={m.matchId} match={m} />
                    ))}
                </div>
            )}

            {filter === "champion" && (
                <div className="predictions-special">
                    <h2>🏆 Typy na mistrza turnieju</h2>
                    {championPicks.length === 0 && <p className="no-data">Nikt jeszcze nie typował mistrza.</p>}
                    <div className="predictions-grid">
                        {championPicks.map((p, i) => (
                            <div key={i} className="prediction-chip">
                                <span className="pred-user">{p.username}</span>
                                <span className="pred-value">
                                    <img className="flag flag-sm" src={flagUrl(p.teamCode)} alt={p.teamName} />
                                    {p.teamName}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {filter === "scorer" && (
                <div className="predictions-special">
                    <h2>⚽ Typy na króla strzelców</h2>
                    {topScorerPicks.length === 0 && <p className="no-data">Nikt jeszcze nie typował króla strzelców.</p>}
                    <div className="predictions-grid">
                        {topScorerPicks.map((p, i) => (
                            <div key={i} className="prediction-chip">
                                <span className="pred-user">{p.username}</span>
                                <span className="pred-value">{p.playerName}</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

function PredictionMatchCard({ match }) {
    const t = kickoffInfo(match.kickoffUtc, match.date);
    const hasActual = match.actualScore1 != null && match.actualScore2 != null;
    const locked = new Date() >= new Date(match.kickoffUtc);
    const stageLabel = match.phase === "KNOCKOUT"
        ? (STAGE_LABELS[match.groupName] || match.groupName)
        : `Gr. ${match.groupName}`;

    return (
        <div className={"prediction-match-card" + (locked ? " locked" : "")}>
            <div className="pred-match-header">
                <span className="pred-stage">{stageLabel}</span>
                <span className="pred-kickoff">🕑 {t.plDate} {t.pl} PL {t.et} ET{t.nextDay ? " (kolejny dzień)" : ""}</span>
                {isLive(match) && (
                    <span className="live-badge"><span className="live-ball">⚽</span>LIVE</span>
                )}
                {hasActual && (
                    <span className="pred-actual">
                        {match.actualScore1}:{match.actualScore2}
                    </span>
                )}
            </div>
            <div className="pred-match-teams">
                <div className="pred-team">
                    <img className="flag" src={flagUrl(match.team1Code)} alt={match.team1Name} />
                    <span>{match.team1Name}</span>
                </div>
                <span className="pred-vs">vs</span>
                <div className="pred-team">
                    <img className="flag" src={flagUrl(match.team2Code)} alt={match.team2Name} />
                    <span>{match.team2Name}</span>
                </div>
            </div>
            <div className="pred-picks">
                {match.picks.length === 0 && <span className="no-picks">Brak typów</span>}
                {match.picks.map((p, i) => (
                    <span key={i} className="pred-pick-chip">
                        <span className="pred-pick-user">{p.username}:</span>
                        {p.htScore1 != null && p.htScore2 != null
                            ? <span className="pred-pick-score">{p.htScore1}:{p.htScore2} (PT) </span>
                            : null}
                        <span className="pred-pick-score">{p.score1 ?? "?"}:{p.score2 ?? "?"}</span>
                    </span>
                ))}
            </div>
        </div>
    );
}

// ---- Typ na mistrza turnieju ----
function ChampionPicker() {
    const [data, setData] = useState(null);
    const [pick, setPick] = useState("");
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);

    function load() {
        api(`${API}/champion`).then((res) => {
            if (!res.ok) return;
            res.json().then((d) => { setData(d); setPick(d.pick ?? ""); });
        });
    }
    useEffect(() => { load(); }, []);

    async function save() {
        setSaving(true);
        const res = await api(`${API}/champion`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ code: pick || null }),
        });
        setSaving(false);
        if (res.ok) {
            setJustSaved(true);
            setTimeout(() => setJustSaved(false), 1500);
            res.json().then(setData);
        }
    }

    if (data === null) return <div className="loading">Ładowanie…</div>;

    const { teams, locked, actualChampion } = data;
    const dirty = pick !== (data.pick ?? "");
    const pickedTeam = teams.find((t) => t.code === pick);
    const championTeam = teams.find((t) => t.code === actualChampion);

    return (
        <div className="champion-card">
            <h2>🏆 Typ na mistrza turnieju <span className="bonus-info">(+15 pkt)</span></h2>

            {actualChampion && (
                <div className="champion-result">
                    <span>Mistrz:</span>
                    {championTeam && <Flag code={championTeam.code} name={championTeam.name} />}
                    <strong>{championTeam ? championTeam.name : actualChampion}</strong>
                </div>
            )}

            <div className="champion-picker">
                {pickedTeam && <Flag code={pickedTeam.code} name={pickedTeam.name} />}
                <select value={pick} disabled={locked} onChange={(e) => setPick(e.target.value)}>
                    <option value="">— wybierz drużynę —</option>
                    {teams.map((t) => <option key={t.code} value={t.code}>{t.name}</option>)}
                </select>
                {locked ? (
                    <span className="locked-badge">🔒 Typowanie zamknięte — turniej trwa</span>
                ) : (
                    <React.Fragment>
                        <button className="btn btn-save" disabled={!dirty || saving} onClick={save}>
                            {saving ? "Zapisywanie…" : "Zapisz"}
                        </button>
                        {justSaved && <span className="saved-badge">✓ zapisano</span>}
                    </React.Fragment>
                )}
            </div>
        </div>
    );
}

// ---- Typ na krola strzelcow ----
function TopScorerPicker() {
    const [data, setData] = useState(null);
    const [pick, setPick] = useState("");
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);

    function load() {
        api(`${API}/topscorer`).then((res) => {
            if (!res.ok) return;
            res.json().then((d) => { setData(d); setPick(d.pick ?? ""); });
        });
    }
    useEffect(() => { load(); }, []);

    async function save() {
        setSaving(true);
        await api(`${API}/topscorer`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ pick: pick.trim() || null }),
        });
        setSaving(false);
        setJustSaved(true);
        setTimeout(() => setJustSaved(false), 1500);
    }

    if (data === null) return <div className="loading">Ładowanie…</div>;

    const { locked, actualTopScorer, bonusPoints } = data;
    const dirty = pick.trim() !== (data.pick ?? "").trim();

    return (
        <div className="champion-card">
            <h2>⚽ Typ na króla strzelców <span className="bonus-info">(+{bonusPoints} pkt)</span></h2>

            {actualTopScorer && (
                <div className="champion-result">
                    <span>Król strzelców:</span>
                    <strong>{actualTopScorer}</strong>
                </div>
            )}

            <div className="champion-picker">
                <input type="text" className="scorer-input"
                       value={pick} disabled={locked} maxLength={60}
                       placeholder="Imię i nazwisko, np. Vinicius Jr."
                       onChange={(e) => setPick(e.target.value)}
                       onKeyDown={(e) => { if (e.key === "Enter" && !locked && dirty) save(); }} />
                {locked ? (
                    <span className="locked-badge">🔒 Typowanie zamknięte — turniej trwa</span>
                ) : (
                    <React.Fragment>
                        <button className="btn btn-save" disabled={!dirty || saving} onClick={save}>
                            {saving ? "Zapisywanie…" : "Zapisz"}
                        </button>
                        {justSaved && <span className="saved-badge">✓ zapisano</span>}
                    </React.Fragment>
                )}
            </div>
        </div>
    );
}

// ---- Faza pucharowa ----
function KnockoutStage({ matches, onSaved }) {
    const knockoutMatches = useMemo(() => matches.filter((m) => m.phase === "KNOCKOUT"), [matches]);

    const byStage = useMemo(() => {
        const map = new Map(STAGE_ORDER.map((s) => [s, []]));
        knockoutMatches.forEach((m) => {
            if (!map.has(m.groupName)) map.set(m.groupName, []);
            map.get(m.groupName).push(m);
        });
        return [...map.entries()].filter(([, ms]) => ms.length > 0);
    }, [knockoutMatches]);

    return (
        <div className="knockout">
            {byStage.map(([stage, stageMatches]) => (
                <div className="knockout-section" key={stage}>
                    <h2>{STAGE_LABELS[stage] || stage}</h2>
                    {stageMatches.map((m) => (
                        <MatchRow key={m.id} match={m} onSaved={onSaved} showGroup={false} />
                    ))}
                </div>
            ))}
        </div>
    );
}

// ---- Panel admina — reczne wyniki i edycja druzyn ----
function AdminMatchRow({ match, isExpanded, isEditingTeams, onToggle, onToggleTeams, onSaved }) {
    const [htA1, setHtA1] = useState(match.actualHtScore1 ?? "");
    const [htA2, setHtA2] = useState(match.actualHtScore2 ?? "");
    const [ftA1, setFtA1] = useState(match.actualScore1 ?? "");
    const [ftA2, setFtA2] = useState(match.actualScore2 ?? "");
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const [t1Name, setT1Name] = useState(match.team1Name);
    const [t1Code, setT1Code] = useState(match.team1Code);
    const [t1En, setT1En] = useState("");
    const [t2Name, setT2Name] = useState(match.team2Name);
    const [t2Code, setT2Code] = useState(match.team2Code);
    const [t2En, setT2En] = useState("");
    const [savingTeams, setSavingTeams] = useState(false);
    const [teamsSaved, setTeamsSaved] = useState(false);

    useEffect(() => {
        setHtA1(match.actualHtScore1 ?? "");
        setHtA2(match.actualHtScore2 ?? "");
        setFtA1(match.actualScore1 ?? "");
        setFtA2(match.actualScore2 ?? "");
        setT1Name(match.team1Name);
        setT1Code(match.team1Code);
        setT2Name(match.team2Name);
        setT2Code(match.team2Code);
    }, [match]);

    async function saveResult() {
        if (ftA1 === "" || ftA2 === "") return;
        setSaving(true);
        await api(`${API}/admin/matches/${match.id}/result`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                score1: Number(ftA1), score2: Number(ftA2),
                htScore1: htA1 !== "" ? Number(htA1) : null,
                htScore2: htA2 !== "" ? Number(htA2) : null,
            }),
        });
        setSaving(false); setSaved(true);
        setTimeout(() => setSaved(false), 2500);
        onSaved();
    }

    async function saveTeams() {
        setSavingTeams(true);
        await api(`${API}/admin/matches/${match.id}/teams`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ team1Name: t1Name, team1Code: t1Code, team1En: t1En,
                                   team2Name: t2Name, team2Code: t2Code, team2En: t2En }),
        });
        setSavingTeams(false); setTeamsSaved(true);
        setTimeout(() => setTeamsSaved(false), 2500);
        onSaved();
    }

    const t = kickoffInfo(match.kickoffUtc, match.date);
    const stageLabel = match.phase === "KNOCKOUT"
        ? (STAGE_LABELS[match.groupName] || match.groupName)
        : `Gr. ${match.groupName}`;

    return (
        <div className="admin-match-row">
            <div className="admin-match-header" onClick={onToggle}>
                <span className="admin-stage">{stageLabel}</span>
                <span className="admin-kickoff">🕑 {t.plDate} {t.pl} PL {t.et} ET</span>
                {isLive(match) && (
                    <span className="live-badge"><span className="live-ball">⚽</span>LIVE</span>
                )}
                <span className="admin-teams">{match.team1Name} vs {match.team2Name}</span>
                {match.actualScore1 != null && (
                    <span className="admin-result">
                        {match.actualHtScore1 != null ? `PT ${match.actualHtScore1}:${match.actualHtScore2} · ` : ""}
                        FT {match.actualScore1}:{match.actualScore2}
                    </span>
                )}
                <span className="admin-toggle">{isExpanded ? "▲" : "▼"}</span>
            </div>

            {isExpanded && (
                <div className="admin-match-form">
                    <div className="admin-score-row">
                        <label>PT:</label>
                        <input type="number" min="0" style={{width:"50px"}} value={htA1} onChange={(e) => setHtA1(e.target.value)} />
                        <span>:</span>
                        <input type="number" min="0" style={{width:"50px"}} value={htA2} onChange={(e) => setHtA2(e.target.value)} />
                    </div>
                    <div className="admin-score-row">
                        <label>FT:</label>
                        <input type="number" min="0" style={{width:"50px"}} value={ftA1} onChange={(e) => setFtA1(e.target.value)} />
                        <span>:</span>
                        <input type="number" min="0" style={{width:"50px"}} value={ftA2} onChange={(e) => setFtA2(e.target.value)} />
                    </div>
                    <button className="btn btn-save" disabled={ftA1 === "" || ftA2 === "" || saving} onClick={saveResult}>
                        {saving ? "Zapisywanie…" : "Zapisz wynik"}
                    </button>
                    {saved && <span className="saved-badge">✓ Zapisano i przyznano punkty</span>}
                    {match.phase === "KNOCKOUT" && (
                        <button className="btn btn-clear" style={{marginLeft:"4px"}} onClick={onToggleTeams}>
                            {isEditingTeams ? "Anuluj edycję" : "Edytuj drużyny"}
                        </button>
                    )}
                </div>
            )}

            {isEditingTeams && match.phase === "KNOCKOUT" && (
                <div className="admin-teams-form">
                    <div className="admin-team-row">
                        <label>Drużyna 1:</label>
                        <input value={t1Name} onChange={(e) => setT1Name(e.target.value)} placeholder="Polska" />
                        <input value={t1Code} onChange={(e) => setT1Code(e.target.value)}
                               placeholder="pl" style={{maxWidth:"54px"}} />
                        <input value={t1En} onChange={(e) => setT1En(e.target.value)} placeholder="Poland (ang.)" />
                    </div>
                    <div className="admin-team-row">
                        <label>Drużyna 2:</label>
                        <input value={t2Name} onChange={(e) => setT2Name(e.target.value)} placeholder="Niemcy" />
                        <input value={t2Code} onChange={(e) => setT2Code(e.target.value)}
                               placeholder="de" style={{maxWidth:"54px"}} />
                        <input value={t2En} onChange={(e) => setT2En(e.target.value)} placeholder="Germany (ang.)" />
                    </div>
                    <button className="btn btn-save" disabled={savingTeams} onClick={saveTeams}>
                        {savingTeams ? "Zapisywanie…" : "Zapisz drużyny"}
                    </button>
                    {teamsSaved && <span className="saved-badge">✓ Zapisano drużyny</span>}
                </div>
            )}
        </div>
    );
}

function AdminPanel({ matches, onSaved }) {
    const [topScorer, setTopScorer] = useState("");
    const [savingTs, setSavingTs] = useState(false);
    const [tsSaved, setTsSaved] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [generatingBracket, setGeneratingBracket] = useState(false);
    const [expanded, setExpanded] = useState(null);
    const [editTeams, setEditTeams] = useState(null);

    async function saveTopScorer() {
        if (!topScorer.trim()) return;
        setSavingTs(true);
        await api(`${API}/admin/topscorer`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: topScorer.trim() }),
        });
        setSavingTs(false); setTsSaved(true);
        setTimeout(() => setTsSaved(false), 2500);
        onSaved();
    }

    async function forceRefresh() {
        setRefreshing(true);
        await api(`${API}/results/refresh`, { method: "POST" });
        setRefreshing(false);
        onSaved();
    }

    async function forceRecalculate() {
        if (!window.confirm("Przeliczyć wszystkie punkty od zera? To naprawi wszelkie rozbieżności.")) return;
        setRefreshing(true);
        await api(`${API}/admin/recalculate`, { method: "POST" });
        setRefreshing(false);
        onSaved();
    }

    async function generateBracket() {
        if (!window.confirm("Wygenerować drabinkę pucharową R32? Ta operacja zaktualizuje zespoły w fazie 1/16 finału.")) return;
        setGeneratingBracket(true);
        try {
            await api(`${API}/admin/bracket/generate`, { method: "POST" });
            alert("Drabinka R32 wygenerowana poprawnie!");
            onSaved();
        } catch (e) {
            alert("Błąd generowania drabinki: " + e.message);
        } finally {
            setGeneratingBracket(false);
        }
    }

    const adminMatches = useMemo(() => {
        const now = new Date();
        return matches
            .filter((m) => m.phase === "KNOCKOUT" || now >= new Date(m.kickoffUtc))
            .sort((a, b) => a.kickoffUtc.localeCompare(b.kickoffUtc));
    }, [matches]);

    return (
        <div className="admin-panel">
            <div className="admin-section">
                <h3>🔄 Odświeżanie wyników</h3>
                <div style={{padding:"12px 18px"}}>
                    <button className="btn btn-save" disabled={refreshing} onClick={forceRefresh}>
                        {refreshing ? "Odświeżanie…" : "Wymuś pobranie wyników z API"}
                    </button>
                    <span style={{marginLeft:"10px", fontSize:"13px", color:"var(--muted)"}}>
                        Normalnie odświeżane co 5 min automatycznie.
                    </span>
                </div>
                <div style={{padding:"0 18px 12px"}}>
                    <button className="btn btn-clear" disabled={refreshing} onClick={forceRecalculate}>
                        {refreshing ? "Przeliczanie…" : "🔄 Przelicz wszystkie punkty od zera"}
                    </button>
                    <span style={{marginLeft:"10px", fontSize:"13px", color:"var(--muted)"}}>
                        Naprawia rozbieżności między zakładkami.
                    </span>
                </div>
            </div>

            <div className="admin-section">
                <h3>🏆 Generowanie Drabinki</h3>
                <div style={{padding:"12px 18px"}}>
                    <button className="btn btn-save" disabled={generatingBracket} onClick={generateBracket}>
                        {generatingBracket ? "Generowanie…" : "⚽ Wygeneruj drabinkę R32"}
                    </button>
                    <span style={{marginLeft:"10px", fontSize:"13px", color:"var(--muted)"}}>
                        Uzupełnia pary fazy pucharowej wg topologii.
                    </span>
                </div>
            </div>

            <div className="admin-section">
                <h3>👑 Ustaw króla strzelców</h3>
                <div style={{padding:"12px 18px"}}>
                    <div className="champion-picker">
                        <input type="text" className="scorer-input"
                               value={topScorer} maxLength={60}
                               placeholder="Imię i nazwisko (dokładnie jak wpisali gracze)"
                               onChange={(e) => setTopScorer(e.target.value)}
                               onKeyDown={(e) => { if (e.key === "Enter") saveTopScorer(); }} />
                        <button className="btn btn-save" disabled={!topScorer.trim() || savingTs} onClick={saveTopScorer}>
                            {savingTs ? "Zapisywanie…" : "Ustaw i przyznaj punkty"}
                        </button>
                        {tsSaved && <span className="saved-badge">✓ Zapisano, bonusy przyznane</span>}
                    </div>
                    <p style={{margin:"8px 0 0", fontSize:"12px", color:"var(--muted)"}}>
                        ⚠ Punkty za króla strzelców można przyznać tylko raz. Porównanie odbywa się <em>case-insensitive</em>.
                    </p>
                </div>
            </div>

            <div className="admin-section">
                <h3>📋 Ręczne wyniki meczów</h3>
                <p className="admin-hint">
                    Mecze które się już rozpoczęły + wszystkie pucharowe. Kliknij, by rozwinąć formularz.
                </p>
                {adminMatches.map((m) => (
                    <AdminMatchRow key={m.id} match={m}
                        isExpanded={expanded === m.id}
                        isEditingTeams={editTeams === m.id}
                        onToggle={() => setExpanded(expanded === m.id ? null : m.id)}
                        onToggleTeams={() => setEditTeams(editTeams === m.id ? null : m.id)}
                        onSaved={onSaved} />
                ))}
                {adminMatches.length === 0 && (
                    <p style={{padding:"18px", color:"var(--muted)", fontSize:"14px", margin:0}}>
                        Żaden mecz jeszcze się nie rozpoczął.
                    </p>
                )}
            </div>
        </div>
    );
}

// ---- Aplikacja (dla zalogowanego uzytkownika) ----
function App({ user, onLogout }) {
    const [matches, setMatches] = useState([]);
    const [groupFilter, setGroupFilter] = useState("ALL");
    const [loading, setLoading] = useState(true);
    const [tab, setTab] = useState("matches");
    const [isAdmin, setIsAdmin] = useState(false);

    async function loadAll() {
        const res = await api(`${API}/matches`);
        if (!res.ok) return;
        setMatches(await res.json());
        setLoading(false);
    }

    useEffect(() => { loadAll(); }, []);

    useEffect(() => {
        api(`${API}/admin/check`).then((res) => {
            if (res.ok) res.json().then((d) => setIsAdmin(d.admin));
        });
    }, []);

    // Co 30 s - re-render, aby mecze ktore sie zaczynaja same sie zarablokowaly
    const [, setTick] = useState(0);
    useEffect(() => {
        const id = setInterval(() => setTick((x) => x + 1), 30000);
        return () => clearInterval(id);
    }, []);

    const groupMatches = useMemo(() => matches.filter((m) => m.phase === "GROUP"), [matches]);

    const groups = useMemo(
        () => [...new Set(groupMatches.map((m) => m.groupName))].sort(),
        [groupMatches]
    );

    const filtered = useMemo(
        () => groupFilter === "ALL" ? groupMatches : groupMatches.filter((m) => m.groupName === groupFilter),
        [groupMatches, groupFilter]
    );

    const byDate = useMemo(() => {
        const map = new Map();
        filtered.forEach((m) => {
            if (!map.has(m.date)) map.set(m.date, []);
            map.get(m.date).push(m);
        });
        return [...map.entries()];
    }, [filtered]);

    const playedCount = groupMatches.filter((m) => m.played).length;

    if (loading) return <div className="loading">Ładowanie meczów…</div>;

    return (
        <div>
            <header className="app-header">
                <div className="userbar">
                    <span className="user-chip">👤 {user}</span>
                    {isAdmin && <span className="admin-chip">🔧 Admin</span>}
                    <button className="btn btn-clear" onClick={onLogout}>Wyloguj</button>
                </div>
                <img src="/emblem.png" alt="MŚ 2026 Emblem" className="header-emblem" />
                <h1>⚽ MŚ 2026 - Typer Cuniados</h1>
                <p>Faza grupowa: 11–27 czerwca 2026 • {playedCount} / {groupMatches.length} uzupełnionych</p>
            </header>

            <div className="container">
                <div className="tab-bar">
                    <button className={"chip wide" + (tab === "matches" ? " active" : "")}
                            onClick={() => setTab("matches")}>⚽ Faza grupowa</button>
                    <button className={"chip wide" + (tab === "knockout" ? " active" : "")}
                            onClick={() => setTab("knockout")}>🏁 Faza pucharowa</button>
                    <button className={"chip wide" + (tab === "predictions" ? " active" : "")}
                            onClick={() => setTab("predictions")}>📋 Typy</button>
                    <button className={"chip wide" + (tab === "leaderboard" ? " active" : "")}
                            onClick={() => setTab("leaderboard")}>🏆 Ranking</button>
                    {isAdmin && (
                        <button className={"chip wide admin-tab" + (tab === "admin" ? " active" : "")}
                                onClick={() => setTab("admin")}>🔧 Admin</button>
                    )}
                </div>

                {tab === "leaderboard" ? (
                    <Leaderboard />
                ) : tab === "knockout" ? (
                    <KnockoutStage matches={matches} onSaved={loadAll} />
                ) : tab === "predictions" ? (
                    <PredictionsTab />
                ) : tab === "admin" && isAdmin ? (
                    <AdminPanel matches={matches} onSaved={loadAll} />
                ) : (
                    <React.Fragment>
                        <ChampionPicker />
                        <TopScorerPicker />
                        <div className="group-filter">
                            <button className={"chip wide" + (groupFilter === "ALL" ? " active" : "")}
                                    onClick={() => setGroupFilter("ALL")}>Wszystkie</button>
                            {groups.map((g) => (
                                <button key={g}
                                        className={"chip" + (groupFilter === g ? " active" : "")}
                                        onClick={() => setGroupFilter(g)}>{g}</button>
                            ))}
                        </div>
                        {byDate.map(([date, dayMatches]) => (
                            <DayCard key={date} date={date} matches={dayMatches} onSaved={loadAll} />
                        ))}
                    </React.Fragment>
                )}
            </div>

            <footer>
                W pociągu jest tłok, w tramwaju jest tłok, kibice na Orła jadą!
            </footer>
        </div>
    );
}

// ---- Ekran logowania / rejestracji ----
function Login({ onLogin }) {
    const [mode, setMode] = useState("login");
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [password2, setPassword2] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);

    const isRegister = mode === "register";

    function switchMode() { setMode(isRegister ? "login" : "register"); setError(""); setPassword2(""); }

    async function submit(e) {
        e.preventDefault();
        setError("");
        if (isRegister && password !== password2) { setError("Hasła nie są takie same"); return; }
        setBusy(true);
        const res = await fetch(`${API}/auth/${isRegister ? "register" : "login"}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password }),
        });
        setBusy(false);
        if (!res.ok) {
            let msg = isRegister ? "Nie udało się zarejestrować" : "Nieprawidłowy login lub hasło";
            try { const b = await res.json(); if (b.error) msg = b.error; } catch (_) {}
            setError(msg);
            return;
        }
        const data = await res.json();
        localStorage.setItem("wc_token", data.token);
        localStorage.setItem("wc_user", data.username);
        onLogin(data.username);
    }

    return (
        <div className="login-wrap">
            <form className="login-card" onSubmit={submit}>
                <div className="login-logo">⚽</div>
                <h1>MŚ 2026 - Typer Cuniados</h1>
                <p className="login-sub">
                    {isRegister ? "Załóż konto, aby dołączyć" : "Zaloguj się, aby obstawiać mecze"}
                </p>

                <label>Nazwa użytkownika</label>
                <input type="text" value={username} autoFocus placeholder="np. Szymon"
                       onChange={(e) => setUsername(e.target.value)} />

                <label>Hasło</label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />

                {isRegister && (
                    <React.Fragment>
                        <label>Powtórz hasło</label>
                        <input type="password" value={password2} onChange={(e) => setPassword2(e.target.value)} />
                    </React.Fragment>
                )}

                {error && <div className="login-error">{error}</div>}

                <button className="btn btn-save login-btn" type="submit" disabled={busy}>
                    {busy ? (isRegister ? "Rejestrowanie…" : "Logowanie…") : (isRegister ? "Zarejestruj" : "Zaloguj")}
                </button>

                <div className="login-switch">
                    {isRegister ? "Masz już konto?" : "Nie masz konta?"}{" "}
                    <button type="button" className="link-btn" onClick={switchMode}>
                        {isRegister ? "Zaloguj się" : "Zarejestruj się"}
                    </button>
                </div>
            </form>
        </div>
    );
}

// ---- Ekran "serwer sie budzi" ----
function WakeScreen() {
    return (
        <div className="login-wrap">
            <div className="login-card wake-card">
                <img src="/emblem.png" alt="MŚ 2026 Emblem" className="login-emblem" />
                <div className="wake-spinner" />
                <h1>⚽ MŚ 2026 - Typer Cuniados</h1>
                <p className="login-sub">Serwer się uruchamia po dłuższej nieaktywności…</p>
                <p className="wake-sub">To może potrwać do minuty. Strona załaduje się sama.</p>
            </div>
        </div>
    );
}

function useServerWake() {
    const [state, setState] = useState("checking");
    useEffect(() => {
        let cancelled = false;
        const slowTimer = setTimeout(() => { if (!cancelled) setState("waking"); }, 2000);
        async function waitForServer() {
            while (!cancelled) {
                try {
                    const res = await fetch(`${API}/health`, { cache: "no-store" });
                    if (res.ok) { clearTimeout(slowTimer); if (!cancelled) setState("ready"); return; }
                } catch (_) {}
                await new Promise((r) => setTimeout(r, 3000));
            }
        }
        waitForServer();
        return () => { cancelled = true; clearTimeout(slowTimer); };
    }, []);
    return state;
}

// ---- Korzen ----
function Root() {
    const serverState = useServerWake();
    const [user, setUser] = useState(localStorage.getItem("wc_user"));

    useEffect(() => {
        const onLogout = () => setUser(null);
        window.addEventListener("wc-logout", onLogout);
        return () => window.removeEventListener("wc-logout", onLogout);
    }, []);

    function logout() {
        localStorage.removeItem("wc_token");
        localStorage.removeItem("wc_user");
        setUser(null);
    }

    if (serverState === "waking") return <WakeScreen />;
    if (serverState === "checking") return null;
    if (!user) return <Login onLogin={setUser} />;
    return <App user={user} onLogout={logout} />;
}

ReactDOM.createRoot(document.getElementById("root")).render(<Root />);
