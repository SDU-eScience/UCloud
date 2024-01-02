#!/usr/bin/env bash
openssl \
  req \
  -newkey rsa:4096 \
  -nodes \
  -keyout server-key.pem -out server-req.pem \
  -subj "/C=DK/ST=ST/L=L/O=O/OU=OU/CN=grpc-test-provider"

openssl \
  x509 -req \
  -in server-req.pem -days 60 \
  -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial \
  -out server-cert.pem
