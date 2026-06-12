interface LoginViewProps {
  email: string;
  password: string;
  error: string | null;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onLogin: () => void;
}

export function LoginView({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onLogin,
}: LoginViewProps) {
  return (
    <div className="app">
      <h1>Tonezen</h1>
      <p>Sign in with your account to sync audiobook progress.</p>
      <input
        placeholder="Email"
        value={email}
        onChange={(e) => onEmailChange(e.target.value)}
        style={{ display: "block", marginBottom: 8, width: "100%", padding: 8 }}
      />
      <input
        placeholder="Password"
        type="password"
        value={password}
        onChange={(e) => onPasswordChange(e.target.value)}
        style={{ display: "block", marginBottom: 8, width: "100%", padding: 8 }}
      />
      <button onClick={onLogin}>Sign in</button>
      {error && <p style={{ color: "#f87171" }}>{error}</p>}
    </div>
  );
}
