# Usage: make bundle | make zip | make release
# Optional: make release VERSION=1.11.0

NAME := $(notdir $(CURDIR))
VERSION ?= $(shell date +%Y-%m-%d-%H-%M)

.PHONY: bundle zip release

bundle:
	git bundle create $(NAME)-$(VERSION).bundle --all

zip:
	git archive -o $(NAME)-$(VERSION).zip HEAD

release: bundle zip

patch:
	./apply_patch.sh $(NAME)-*

 # Build intellij plugin : requires jdk 17 otherwise use IDE conf
ide:
	cd intellij && ./gradlew clean buildPlugin

 # Build vscode : requires node
vs:
	cd vscode && npm install && npm run package