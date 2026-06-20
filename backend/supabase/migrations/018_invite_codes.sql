-- Invite-only registration by reusable referral codes.

CREATE TABLE IF NOT EXISTS invite_codes (
    code TEXT PRIMARY KEY CHECK (code ~ '^[A-Z0-9]{12}$'),
    owner_user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    disabled_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS invite_redemptions (
    invitee_user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    inviter_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    invite_code TEXT NOT NULL REFERENCES invite_codes(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE invite_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE invite_redemptions ENABLE ROW LEVEL SECURITY;

GRANT ALL ON invite_codes, invite_redemptions TO service_role;

INSERT INTO invite_codes (code, owner_user_id)
SELECT upper(substr(md5(u.id::text), 1, 12)), u.id
FROM auth.users u
ON CONFLICT DO NOTHING;
