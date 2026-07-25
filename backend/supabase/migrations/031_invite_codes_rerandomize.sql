-- Replace predictable md5(user_id)-derived invite codes with cryptographically
-- random ones. Avatar listing previously exposed user UUIDs, which made
-- upper(substr(md5(uuid), 1, 12)) recoverable for accounts seeded by 018.

ALTER TABLE invite_redemptions
  DROP CONSTRAINT IF EXISTS invite_redemptions_invite_code_fkey;

ALTER TABLE invite_redemptions
  ADD CONSTRAINT invite_redemptions_invite_code_fkey
  FOREIGN KEY (invite_code) REFERENCES invite_codes (code)
  ON UPDATE CASCADE
  ON DELETE RESTRICT;

DO $$
DECLARE
  r RECORD;
  new_code TEXT;
  alphabet CONSTANT TEXT := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  i INT;
  attempt INT;
  updated BOOLEAN;
BEGIN
  FOR r IN
    SELECT code, owner_user_id
    FROM invite_codes
    WHERE code = upper(substr(md5(owner_user_id::text), 1, 12))
  LOOP
    updated := false;
    FOR attempt IN 1..32 LOOP
      new_code := '';
      FOR i IN 1..12 LOOP
        new_code := new_code || substr(
          alphabet,
          1 + (get_byte(gen_random_bytes(1), 0) % 36),
          1
        );
      END LOOP;
      BEGIN
        UPDATE invite_codes
        SET code = new_code
        WHERE code = r.code;
        updated := true;
        EXIT;
      EXCEPTION
        WHEN unique_violation THEN
          NULL; -- retry with a new random code
      END;
    END LOOP;

    IF NOT updated THEN
      RAISE EXCEPTION
        'Failed to rerandomize invite code for user %',
        r.owner_user_id;
    END IF;
  END LOOP;
END $$;
