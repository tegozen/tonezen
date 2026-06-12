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
    <div className="app-shell">
      <h1>Tonezen</h1>
      <p>Sign in with your account to sync audiobook progress.</p>
      <input
        className="input-field"
        placeholder="Email"
        value={email}
        onChange={(e) => onEmailChange(e.target.value)}
      />
      <input
        className="input-field"
        placeholder="Password"
        type="password"
        value={password}
        onChange={(e) => onPasswordChange(e.target.value)}
      />
      <button className="btn-primary" type="button" onClick={onLogin}>Sign in</button>
      {error && <p className="error-text">{error}</p>}
    </div>
  );
}
