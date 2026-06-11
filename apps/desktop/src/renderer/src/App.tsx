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
  const audioRef = useRef<HTMLAudioElement>(null);

  const refreshSession = useCallback(async () => {
    const snap = await window.tplayer.session.get();
    setSessionState(snap.state);
  }, []);

  useEffect(() => {
    refreshSession();
    window.tplayer.db.getBooks().then(setBooks);
  }, [refreshSession]);

  const login = async () => {
    await window.tplayer.session.login("demo@tplayer.local", "demo");
    await window.tplayer.session.refreshIfNeeded();
    await refreshSession();
  };

  const logout = async () => {
    await window.tplayer.session.logout();
    await refreshSession();
  };

  const openBook = async (book: Book) => {
    setSelectedBook(book);
    const bookTracks = await window.tplayer.db.getTracks(book.id);
    setTracks(bookTracks);
    setCurrentTrack(bookTracks[0] ?? null);
  };

  const playTrack = (track: Track) => {
    setCurrentTrack(track);
    window.tplayer.playback.setActive(true);
    if (audioRef.current) {
      audioRef.current.src = track.localPath ?? "";
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

  const onTrackEnded = () => {
    if (!selectedBook || !currentTrack) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next) {
      playTrack(next);
    }
  };

  if (sessionState === "Unauthenticated") {
    return (
      <div className="app">
        <h1>TPlayer</h1>
        <p>Sign in to sync audiobook progress across devices.</p>
        <button onClick={login}>Continue (demo)</button>
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
          <div className="grid">
            {tracks.map((track) => (
              <div key={track.id} className="card" onClick={() => playTrack(track)}>
                {track.sortOrder + 1}. {track.title}
              </div>
            ))}
          </div>
          <div className="player">
            <strong>Now playing: {currentTrack?.title ?? "—"}</strong>
            <audio ref={audioRef} controls onEnded={onTrackEnded} />
          </div>
        </>
      )}
    </div>
  );
}
