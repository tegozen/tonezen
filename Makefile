.PHONY: test lint up down indexer-test api-test desktop-test storage-export storage-import postgres-export postgres-import gen-env check-eol

test: check-eol indexer-test api-test desktop-test
	@echo "All unit tests passed"

lint: check-eol
	cd backend/indexer && npm run lint
	cd backend/api && npm run lint
	cd apps/desktop && npm run lint

check-eol:
	node ci/check-eol.mjs

fix-eol:
	node ci/check-eol.mjs --fix

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

postgres-export:
	bash scripts/postgres-export.sh $(if $(OUT),$(OUT),)

postgres-import:
	@test -n "$(ARCHIVE)" || (echo "Usage: make postgres-import ARCHIVE=backups/file.tar.gz" && exit 1)
	bash scripts/postgres-import.sh $(ARCHIVE)

gen-env:
	node scripts/gen-env.mjs $(if $(FORCE),--force,)
