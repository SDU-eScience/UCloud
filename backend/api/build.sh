#!/usr/bin/env bash
set -x
mkdir -p .kotlin
cd .kotlin
rm -rf *
flatc --kotlin ../*.fbs
mkdir -p ../../service-lib/src/api/kotlin/
rm -rf ../../service-lib/src/api/kotlin/*
mv * ../../service-lib/src/api/kotlin/
cd ..

mkdir -p .typescript
cd .typescript
rm -rf *
flatc --ts ../*.fbs
mkdir -p ../../../frontend-web/webclient/app/FlatApi
mv * ../../../frontend-web/webclient/app/FlatApi
