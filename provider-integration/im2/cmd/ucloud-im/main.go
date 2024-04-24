package main

import (
	"crypto/rsa"
	"crypto/x509"
	"database/sql"
	"encoding/pem"
	"fmt"
	_ "github.com/golang-jwt/jwt/v5"
	_ "github.com/lib/pq"
	"log"
	"net"
	"os"
	"strings"
	"ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/gateway"
)

var ucloudPublicKey *rsa.PublicKey

/*
func authInterceptor(ctx context.Context, req interface{}, _ *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
	if ucloudPublicKey == nil {
		return nil, status.Errorf(codes.Internal, "bad pkg configuration")
	}

	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return nil, status.Errorf(codes.Unauthenticated, "invalid authorization token supplied")
	}

	authToken := md.Get("ucloud-auth")
	if len(authToken) != 1 {
		return nil, status.Errorf(codes.Unauthenticated, "invalid authorization token supplied")
	}

	_, err := jwt.Parse(authToken[0], func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return ucloudPublicKey, nil
	})

	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "invalid authorization token supplied")
	}

	return handler(ctx, req)
}
*/

func readPublicKey() *rsa.PublicKey {
	content, _ := os.ReadFile("/etc/ucloud/ucloud_crt.pem")
	if content == nil {
		return nil
	}

	var keyBuilder strings.Builder
	keyBuilder.WriteString("-----BEGIN PUBLIC KEY-----\n")
	keyBuilder.WriteString(chunkString(string(content), 64))
	keyBuilder.WriteString("\n-----END PUBLIC KEY-----\n")

	key := keyBuilder.String()

	block, _ := pem.Decode([]byte(key))
	if block == nil {
		return nil
	}

	pubKey, _ := x509.ParsePKIXPublicKey(block.Bytes)
	if pubKey == nil {
		return nil
	}

	rsaKey, _ := pubKey.(*rsa.PublicKey)
	return rsaKey
}

func chunkString(input string, chunkSize int) string {
	var builder strings.Builder
	for i, c := range input {
		if i != 0 && i%chunkSize == 0 {
			builder.WriteString("\n")
		}
		builder.WriteRune(c)
	}
	return builder.String()
}

type ServerMode int

const (
	ServerModeUser ServerMode = iota
	ServerModeServer
	ServerModeProxy
	ServerModePlugin
)

func main() {
	if true {
		config.Parse("/tmp/foo.yaml")
		return
	}
	db, err := sql.Open("postgres", "postgres://postgres:postgrespassword@localhost/postgres")
	if err != nil {
		log.Fatalf("Could not open database %v", err)
	}
	_ = db
	ucloudPublicKey = readPublicKey()
	if ucloudPublicKey == nil {
		log.Fatalf("Unable to load certificate from UCloud. It was expected at /etc/ucloud/ucloud_crt.pem!")
	}

	mode := ServerModePlugin
	plugin := ""
	switch os.Args[1] {
	case "user":
		mode = ServerModeUser
	case "server":
		mode = ServerModeServer
	case "proxy":
		mode = ServerModeProxy
	default:
		mode = ServerModePlugin
		plugin = os.Args[1]
	}

	_ = mode
	_ = plugin

	var listener net.Listener = nil
	_ = listener
	if mode == ServerModeServer {
		lis, err := net.Listen("tcp", fmt.Sprintf(":%v", gateway.ServerClusterPort))
		if err != nil {
			log.Fatalf("Failed to start listener")
		}

		gateway.Initialize(gateway.Config{
			ListenAddress:   "0.0.0.0",
			Port:            8889,
			InitialClusters: nil,
			InitialRoutes:   nil,
		})

		gateway.Resume()

		listener = lis
	} else if mode == ServerModeUser {

	}
}
