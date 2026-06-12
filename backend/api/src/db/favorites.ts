import type pg from "pg";

export class FavoritesRepository {
  constructor(private pool: pg.Pool) {}

  async getFavorites(userId: string) {
    const result = await this.pool.query(
      `SELECT f.book_id, f.created_at, b.title, b.content_type
       FROM favorites f JOIN books b ON b.id = f.book_id
       WHERE f.user_id = $1`,
      [userId],
    );
    return result.rows;
  }

  async addFavorite(userId: string, bookId: string) {
    await this.pool.query(
      `INSERT INTO favorites (user_id, book_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
      [userId, bookId],
    );
  }

  async removeFavorite(userId: string, bookId: string) {
    await this.pool.query(`DELETE FROM favorites WHERE user_id = $1 AND book_id = $2`, [
      userId,
      bookId,
    ]);
  }
}
