.PHONY: test lint up down indexer-test api-test desktop-test android-test storage-export storage-import gen-env scripts-test

test: indexer-test api-test desktop-test scripts-test
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

storage-export:
	bash scripts/storage-export.sh $(if $(OUT),$(OUT),)

storage-import:
	@test -n "$(ARCHIVE)" || (echo "Usage: make storage-import ARCHIVE=backups/file.tar.gz" && exit 1)
	bash scripts/storage-import.sh $(ARCHIVE)

gen-env:
	node scripts/gen-env.mjs $(if $(FORCE),--force,)

scripts-test:
	node --test scripts/seed-admin.test.mjs
