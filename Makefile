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

run:
	./apply_patch.sh $(NAME)-*