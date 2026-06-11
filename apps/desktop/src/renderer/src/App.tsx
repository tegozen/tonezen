import { useCallback, useEffect, useRef, useState } from "react";
import { CyclePlaybackResolver } from "@shared/cyclePlayback";
import type { Book, Track } from "@shared/types";

const cycleResolver = new CyclePlaybackResolver();

export function App() {
  const [sessionState, setSessionState] = useState("Unauthenticated");
  const [books, setBooks] = useState<Book[]>([]);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [progressLabel, setProgressLabel] = useState<string | null>(null);
  const lastProgressSaveRef = useRef(0);
  const audioRef = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    return window.tplayer.progress.onUpdated((progress) => {
      if (selectedBook?.id === progress.bookId) {
        const track = tracks.find((t) => t.id === progress.trackId);
        setProgressLabel(track ? `Continue: ${track.title}` : null);
      }
    });
  }, [selectedBook, tracks]);

  const refreshSession = useCallback(async () => {
    const online = navigator.onLine;
    await window.tplayer.session.setOnline(online);
    const snap = await window.tplayer.session.get();
    setSessionState(snap.state);
  }, []);

  useEffect(() => {
    refreshSession();
    window.tplayer.db.getBooks().then(setBooks);
    const onOnline = () => refreshSession();
    const onOffline = () => refreshSession();
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, [refreshSession]);

  const login = async () => {
    try {
      setError(null);
      const snap = await window.tplayer.session.login(email, password);
      setSessionState(snap.state);
      await syncCatalog();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
    }
  };

  const logout = async () => {
    await window.tplayer.session.logout();
    await refreshSession();
  };

  const syncCatalog = async () => {
    setSyncing(true);
    try {
      const synced = await window.tplayer.catalog.sync();
      setBooks(synced as Book[]);
    } finally {
      setSyncing(false);
    }
  };

  const openBook = async (book: Book) => {
    setSelectedBook(book);
    const bookTracks = await window.tplayer.db.getTracks(book.id);
    setTracks(bookTracks as Track[]);
    const saved = await window.tplayer.progress.get(book.id);
    if (saved && book.contentType === "audiobook") {
      const resumeTrack = bookTracks.find((t) => t.id === saved.trackId) ?? bookTracks[0];
      setCurrentTrack(resumeTrack ?? null);
      setProgressLabel(resumeTrack ? `Continue: ${resumeTrack.title}` : null);
    } else {
      setCurrentTrack(bookTracks[0] ?? null);
      setProgressLabel(null);
    }
  };

  const playTrack = (track: Track, startMs = 0) => {
    if (!track.localPath) return;
    setCurrentTrack(track);
    window.tplayer.playback.setActive(true);
    if (audioRef.current) {
      audioRef.current.src = `file://${track.localPath}`;
      if (startMs > 0) audioRef.current.currentTime = startMs / 1000;
      void audioRef.current.play();
    }
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.title,
        artist: selectedBook?.author ?? selectedBook?.title ?? "TPlayer",
      });
      navigator.mediaSession.setActionHandler("play", () => audioRef.current?.play());
      navigator.mediaSession.setActionHandler("pause", () => audioRef.current?.pause());
    }
  };

  const downloadTrack = async (track: Track) => {
    if (!selectedBook) return;
    try {
      const localPath = await window.tplayer.download.track(selectedBook.id, track.id);
      await openBook(selectedBook);
      playTrack({ ...track, localPath });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    }
  };

  const onTimeUpdate = () => {
    if (!selectedBook || selectedBook.contentType !== "audiobook" || !currentTrack || !audioRef.current) return;
    const now = Date.now();
    if (now - lastProgressSaveRef.current < 15000) return;
    lastProgressSaveRef.current = now;
    void window.tplayer.progress.save(
      selectedBook.id,
      currentTrack.id,
      Math.floor(audioRef.current.currentTime * 1000),
    );
  };

  const resumeProgress = async () => {
    if (!selectedBook) return;
    const saved = await window.tplayer.progress.get(selectedBook.id);
    if (!saved) return;
    const track = tracks.find((t) => t.id === saved.trackId);
    if (track?.localPath) playTrack(track, saved.positionMs);
  };

  const onTrackEnded = () => {
    if (!selectedBook || !currentTrack) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next?.localPath) playTrack(next);
  };

  if (sessionState === "Unauthenticated") {
    return (
      <div className="app">
        <h1>TPlayer</h1>
        <p>Sign in with your account to sync audiobook progress.</p>
        <input
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          style={{ display: "block", marginBottom: 8, width: "100%", padding: 8 }}
        />
        <input
          placeholder="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          style={{ display: "block", marginBottom: 8, width: "100%", padding: 8 }}
        />
        <button onClick={login}>Sign in</button>
        {error && <p style={{ color: "#f87171" }}>{error}</p>}
      </div>
    );
  }

  return (
    <div className="app">
      <h1>TPlayer</h1>
      {sessionState === "AuthenticatedOffline" && (
        <div className="banner">No network — sync paused</div>
      )}
      {!selectedBook ? (
        <>
          <button onClick={syncCatalog} disabled={syncing}>
            {syncing ? "Syncing…" : "Sync catalog"}
          </button>
          <div className="grid">
            {books.map((book) => (
              <div key={book.id} className="card" onClick={() => openBook(book)}>
                <strong>{book.title}</strong>
                <div>{book.author}</div>
                <small>{book.contentType}</small>
              </div>
            ))}
          </div>
          <button className="secondary" onClick={logout} style={{ marginTop: 16 }}>
            Sign out
          </button>
        </>
      ) : (
        <>
          <button className="secondary" onClick={() => setSelectedBook(null)}>
            Back
          </button>
          <h2>{selectedBook.title}</h2>
          {progressLabel && selectedBook.contentType === "audiobook" && (
            <button onClick={resumeProgress}>{progressLabel}</button>
          )}
          <div className="grid">
            {tracks.map((track) => (
              <div key={track.id} className="card">
                <div>
                  {track.sortOrder + 1}. {track.title}
                  {track.localPath ? " ✓" : ""}
                </div>
                <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
                  {track.localPath ? (
                    <button onClick={() => playTrack(track)}>Play</button>
                  ) : (
                    <button onClick={() => downloadTrack(track)}>Download</button>
                  )}
                  {track.localPath && (
                    <button
                      className="secondary"
                      onClick={() =>
                        window.tplayer.download.delete(selectedBook.id, track.id).then(() => openBook(selectedBook))
                      }
                    >
                      Delete
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
          <div className="player">
            <strong>Now playing: {currentTrack?.title ?? "—"}</strong>
            <audio ref={audioRef} controls onEnded={onTrackEnded} onTimeUpdate={onTimeUpdate} />
          </div>
        </>
      )}
      {error && <p style={{ color: "#f87171" }}>{error}</p>}
    </div>
  );
}
