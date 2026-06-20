import { randomInt } from "node:crypto";
import type pg from "pg";

const INVITE_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
const INVITE_CODE_LENGTH = 12;

export interface InviteCodeRow {
  code: string;
  owner_user_id: string;
}

export function normalizeInviteCode(code: string): string {
  return code.trim().replace(/[^a-z0-9]/gi, "").toUpperCase();
}

export function generateInviteCode(): string {
  let code = "";
  for (let i = 0; i < INVITE_CODE_LENGTH; i += 1) {
    code += INVITE_CODE_ALPHABET[randomInt(INVITE_CODE_ALPHABET.length)];
  }
  return code;
}

export class AuthRepository {
  constructor(private pool: pg.Pool) {}

  async getInviteCode(code: string): Promise<InviteCodeRow | null> {
    const normalized = normalizeInviteCode(code);
    const result = await this.pool.query(
      `SELECT code, owner_user_id
       FROM invite_codes
       WHERE code = $1 AND disabled_at IS NULL`,
      [normalized],
    );
    return (result.rows[0] as InviteCodeRow | undefined) ?? null;
  }

  async ensureReferralCode(userId: string): Promise<string> {
    const existing = await this.pool.query(
      `SELECT code
       FROM invite_codes
       WHERE owner_user_id = $1 AND disabled_at IS NULL`,
      [userId],
    );
    const existingRow = existing.rows[0] as { code: string } | undefined;
    if (existingRow) return existingRow.code;

    for (let attempt = 0; attempt < 5; attempt += 1) {
      const code = generateInviteCode();
      const result = await this.pool.query(
        `INSERT INTO invite_codes (code, owner_user_id)
         VALUES ($1, $2)
         ON CONFLICT DO NOTHING
         RETURNING code`,
        [code, userId],
      );
      const row = result.rows[0] as { code: string } | undefined;
      if (row) return row.code;
    }

    throw new Error("Failed to create referral code");
  }

  async createRedemption(inviteeUserId: string, inviterUserId: string, inviteCode: string): Promise<void> {
    await this.pool.query(
      `INSERT INTO invite_redemptions (invitee_user_id, inviter_user_id, invite_code)
       VALUES ($1, $2, $3)
       ON CONFLICT (invitee_user_id) DO NOTHING`,
      [inviteeUserId, inviterUserId, normalizeInviteCode(inviteCode)],
    );
  }
}
