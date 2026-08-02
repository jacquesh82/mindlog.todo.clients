# Raccourcis. Tout ce qu'ils font est dans scripts/ ; ce fichier n'existe que
# pour rendre les quatre commandes utiles trouvables sans lire les scripts.

.PHONY: help project build test openapi models lint

help:
	@echo "make project   régénère MindlogTodo.xcodeproj depuis project.yml"
	@echo "make build     construit le client (MINDLOG_ENV=prod|qualif|local, défaut qualif)"
	@echo "make test      lance les tests du paquet"
	@echo "make openapi   rafraîchit le snapshot OpenAPI depuis le serveur"
	@echo "make models    régénère les DTO Swift depuis le snapshot"

project:
	xcodegen generate

build:
	./scripts/xcode.sh build

test:
	swift test

openapi:
	./scripts/fetch-openapi.sh

models:
	./scripts/generate-models.sh
