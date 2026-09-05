-- Grant the existing API role only the operations used by book-watch endpoints and worker.
GRANT SELECT, INSERT, UPDATE ON book_watches TO tonezen_api;
GRANT SELECT, INSERT, DELETE ON book_watch_queries TO tonezen_api;
GRANT SELECT, INSERT, UPDATE ON book_watch_events TO tonezen_api;
GRANT SELECT, INSERT ON book_watch_event_links TO tonezen_api;
GRANT SELECT, INSERT, UPDATE ON book_watch_jobs TO tonezen_api;
