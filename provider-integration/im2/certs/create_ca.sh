#!/usr/bin/env bash
openssl \
  req -x509 \
  -newkey rsa:4096 \
  -days 365 \
  -nodes \
  -keyout ca-key.pem -out ca-cert.pem \
  -subj "/C=DK/ST=ST/L=L/O=O/OU=OU/CN=ucloud-ca"
