import { AppShell, BottomNav } from "@/widgets/app-shell";
import { MiniPlayerBar } from "@/widgets/mini-player";
import { LoginView } from "@/pages/login";
import { NowPlayingSheet } from "@/widgets/now-playing";
import { ToastMessage } from "@/shared/ui/ToastMessage";
import { EarlierChapterPrompt, EarlierCycleBookPrompt, ProgressSyncConflictPrompt } from "@/features/playback";
import { AppShellRoutes } from "@/app/shell";
import { useAppShellWiring } from "@/app/model";

export function App() {
  const wiring = useAppShellWiring();
  const {
    sessionState,
    email,
    setEmail,
    password,
    setPassword,
    error,
    verifyInviteCode,
    registerWithInvite,
    requestPasswordRecovery,
    handleLogin,
    audioRef,
    onTimeUpdate,
    audiobook,
    library,
    showMiniPlayer,
    showBottomNav,
    miniTitle,
    miniSubtitle,
    coverSeed,
    isPlaying,
    progress,
    miniDownloadProgress,
    setShowExpandedPlayer,
    music,
    activeMusicTrack,
    handleTabSelect,
    activeTab,
    showExpandedPlayer,
    currentTrack,
    positionMs,
    durationMs,
    seekBy,
    seekTo,
    volume,
    setVolume,
    toastMessage,
  } = wiring;

  if (sessionState === "Unauthenticated") {
    return (
      <>
        <LoginView
          email={email}
          password={password}
          error={error}
          onEmailChange={setEmail}
          onPasswordChange={setPassword}
          onLogin={() => void handleLogin()}
          onVerifyInviteCode={verifyInviteCode}
          onSignup={registerWithInvite}
          onPasswordRecovery={requestPasswordRecovery}
        />
        <audio ref={audioRef} className="hidden" onEnded={audiobook.handleTrackEnded} onTimeUpdate={onTimeUpdate} />
      </>
    );
  }

  return (
    <>
      <AppShell
        showMiniPlayer={showMiniPlayer}
        showBottomNav={showBottomNav}
        miniPlayer={
          <MiniPlayerBar
            title={miniTitle}
            subtitle={miniSubtitle}
            coverSeed={coverSeed}
            isPlaying={isPlaying}
            progress={progress}
            downloadProgress={miniDownloadProgress}
            onBarClick={() => setShowExpandedPlayer(true)}
            onPlayPause={() => {
              music.onMiniPlayerPlayPause(activeMusicTrack);
            }}
          />
        }
        bottomNav={<BottomNav active={activeTab} onSelect={handleTabSelect} />}
      >
        <AppShellRoutes {...wiring} />
      </AppShell>
      {toastMessage && <ToastMessage message={toastMessage} />}
      <EarlierChapterPrompt
        visible={Boolean(audiobook.earlierChapterPrompt && library.selectedBook)}
        onCancel={audiobook.dismissEarlierChapterPrompt}
        onConfirm={audiobook.confirmEarlierChapterPrompt}
      />
      <EarlierCycleBookPrompt
        visible={Boolean(audiobook.earlierCycleBookPrompt && library.selectedBook)}
        laterBookTitle={audiobook.earlierCycleBookPrompt?.laterBookTitle ?? ""}
        onCancel={audiobook.dismissEarlierCycleBookPrompt}
        onConfirm={audiobook.confirmEarlierCycleBookPrompt}
      />
      <ProgressSyncConflictPrompt
        visible={Boolean(audiobook.syncConflictModel && library.selectedBook)}
        model={audiobook.syncConflictModel}
        onCancel={audiobook.dismissSyncConflictPrompt}
        onChooseLocal={() => void audiobook.chooseSyncConflictLocal()}
        onChooseServer={() => void audiobook.chooseSyncConflictServer()}
      />
      <NowPlayingSheet
        visible={showExpandedPlayer && Boolean(currentTrack)}
        title={miniTitle ?? ""}
        subtitle={miniSubtitle ?? ""}
        coverSeed={coverSeed}
        isPlaying={isPlaying}
        positionMs={positionMs}
        durationMs={durationMs}
        isMusic={music.musicMode}
        waveformPeaks={currentTrack?.waveformPeaks ?? null}
        downloadProgress={miniDownloadProgress}
        controlsDisabled={miniDownloadProgress != null}
        onDismiss={() => setShowExpandedPlayer(false)}
        onPlayPause={() => music.onMiniPlayerPlayPause(activeMusicTrack)}
        onSeekBy={seekBy}
        onSkipPrevious={audiobook.handleSkipPrevious}
        onSkipNext={audiobook.handleSkipNext}
        onSeek={seekTo}
        volume={volume}
        onVolumeChange={setVolume}
      />
      <audio ref={audioRef} className="hidden" onEnded={audiobook.handleTrackEnded} onTimeUpdate={onTimeUpdate} />
    </>
  );
}
