import { useEffect, useState } from "react";
import type { Book, Track } from "@shared/types";
import { LoginView } from "./components/LoginView";
import { useTonezenSession } from "./hooks/useTonezenSession";
import { usePlayback } from "./hooks/usePlayback";

export function App() {
  const {
    sessionState,
    email,
    setEmail,
    password,
    setPassword,
    error,
    setError,
    login,
    logout,
  } = useTonezenSession();

  const [books, setBooks] = useState<Book[]>([]);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [syncing, setSyncing] = useState(false);

  const {
    currentTrack,
    progressLabel,
    audioRef,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    onTrackEnded,
    resumeProgress,
    setInitialTrackState,
  } = usePlayback(selectedBook, tracks);

  useEffect(() => {
    window.tonezen.db.getBooks().then(setBooks);
  }, []);

  const syncCatalog = async () => {
    setSyncing(true);
    try {
      const synced = await window.tonezen.catalog.sync();
      setBooks(synced as Book[]);
    } finally {
      setSyncing(false);
    }
  };

  const handleLogin = async () => {
    const ok = await login();
    if (ok) await syncCatalog();
  };

  const handleLogout = async () => {
    stopPlayback();
    await logout();
  };

  const openBook = async (book: Book) => {
    setSelectedBook(book);
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    setTracks(bookTracks as Track[]);
    const saved = await window.tonezen.progress.get(book.id);
    setInitialTrackState(book, bookTracks as Track[], saved);
  };

  const downloadTrack = async (track: Track) => {
    if (!selectedBook) return;
    try {
      const localPath = await window.tonezen.download.track(selectedBook.id, track.id);
      await openBook(selectedBook);
      playTrack({ ...track, localPath });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    }
  };

  const leaveBook = () => {
    stopPlayback();
    setSelectedBook(null);
  };

  if (sessionState === "Unauthenticated") {
    return (
      <LoginView
        email={email}
        password={password}
        error={error}
        onEmailChange={setEmail}
        onPasswordChange={setPassword}
        onLogin={() => void handleLogin()}
      />
    );
  }

  return (
    <div className="app">
      <h1>Tonezen</h1>
      {sessionState === "AuthenticatedOffline" && (
        <div className="banner">No network — sync paused</div>
      )}
      {!selectedBook ? (
        <>
          <button onClick={() => void syncCatalog()} disabled={syncing}>
            {syncing ? "Syncing…" : "Sync catalog"}
          </button>
          <div className="grid">
            {books.map((book) => (
              <div key={book.id} className="card" onClick={() => void openBook(book)}>
                <strong>{book.title}</strong>
                <div>{book.author}</div>
                <small>{book.contentType}</small>
              </div>
            ))}
          </div>
          <button className="secondary" onClick={() => void handleLogout()} style={{ marginTop: 16 }}>
            Sign out
          </button>
        </>
      ) : (
        <>
          <button className="secondary" onClick={leaveBook}>
            Back
          </button>
          <h2>{selectedBook.title}</h2>
          {progressLabel && selectedBook.contentType === "audiobook" && (
            <button onClick={() => void resumeProgress()}>{progressLabel}</button>
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
                    <button onClick={() => void downloadTrack(track)}>Download</button>
                  )}
                  {track.localPath && (
                    <button
                      className="secondary"
                      onClick={() =>
                        window.tonezen.download.delete(selectedBook.id, track.id).then(() => openBook(selectedBook))
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
