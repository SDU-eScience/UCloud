.DEFAULT_GOAL := build

BINARY_NAME := ucloud
MAIN_PACKAGE := ./cmd/ucloud-tui

.PHONY: build run clean install

build:
	go build -o $(BINARY_NAME) $(MAIN_PACKAGE)

run: build
	./$(BINARY_NAME)

clean:
	rm -f $(BINARY_NAME)

install:
	go install $(MAIN_PACKAGE)