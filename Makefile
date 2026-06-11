.PHONY: test lint up down indexer-test api-test desktop-test android-test

test: indexer-test api-test desktop-test
	@echo "All unit tests passed"

lint:
	cd backend/indexer && npm run lint
	cd backend/api && npm run lint
	cd apps/desktop && npm run lint

up:
	docker compose up -d

down:
	docker compose down

indexer-test:
	cd backend/indexer && npm test

api-test:
	cd backend/api && npm test

desktop-test:
	cd apps/desktop && npm test

indexer-run:
	cd backend/indexer && npm run start
