package main

import (
	"context"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	_ "github.com/lib/pq"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"log"
	"net"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"time"
	"ucloud.dk/gateway"
	"ucloud.dk/orchestrators/files"
	"github.com/liamzdenek/go-pthreads"
)

type server struct {
	files.UnimplementedFilesProviderServiceServer
}

var ucloudPublicKey *rsa.PublicKey

func authInterceptor(ctx context.Context, req interface{}, _ *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
	if ucloudPublicKey == nil {
		return nil, status.Errorf(codes.Internal, "bad internal configuration")
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

func (s *server) Browse(_ context.Context, request *files.ProviderBrowseRequest) (*files.ProviderBrowseReply, error) {
	log.Printf("This is the request %v", request.Next)

	var result []*files.UFile = nil
	result = append(result, &files.UFile{
		Metadata: &files.UFile_Metadata{Path: "/1111/hello from go"},
		Status: &files.UFile_Status{
			Type:              files.FileType_FILE_TYPE_FILE,
			Icon:              files.FileIconHint_FILE_ICON_HINT_UNSPECIFIED,
			Size:              100,
			SizeRecursive:     200,
			ModifiedAt:        300,
			AccessedAt:        400,
			CreatedAt:         500,
			UnixMode:          0o777,
			UnixOwner:         600,
			UnixGroup:         700,
			MetadataDocuments: nil,
		},
	})

	return &files.ProviderBrowseReply{
		Next:  "",
		Items: result,
	}, nil
}

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

func loadServerCertificate() credentials.TransportCredentials {
	serverCertificate, err := tls.LoadX509KeyPair("certs/server-cert.pem", "certs/server-key.pem")
	if err != nil {
		log.Fatalf("Could not load server certificate %v", err)
	}

	return credentials.NewTLS(&tls.Config{
		Certificates: []tls.Certificate{serverCertificate},
		ClientAuth:   tls.NoClientCert,
	})
}

type ServerMode int

const (
	ServerModeUser ServerMode = iota
	ServerModeServer
	ServerModeProxy
	ServerModePlugin
)

func printUid(msg string) {
	log.Printf("[%v] uid = %v | euid = %v", msg, syscall.Getuid(), syscall.Geteuid())
}

func userThread(uid uint64, wg *sync.WaitGroup) {
	pthread.Create(func() {
		runtime.LockOSThread()
		printUid(fmt.Sprintf("before-user-%v", uid))
		defer wg.Done()

		_, _, serr := syscall.Syscall(syscall.SYS_SETUID, uintptr(uid), 0, 0)
		if serr != 0 {
			log.Fatalf("err %v", serr)
		}

		//Do a bit of work to ensure we have a lot of threads running at the same time
		time.Sleep(1 * time.Second)
		go func() {
			printUid("this will fail")
		}()
		//pthread.Sleep(1)

		printUid(fmt.Sprintf("user-%v", uid))

		//thread.Kill()
	})
}

func altMain() {
	//count := 50000
	//printUid("before")
	//var wg = sync.WaitGroup{}
	//wg.Add(count)
	//
	//go func(wg *sync.WaitGroup) {
	//	defer wg.Done()
	//	time.Sleep(2 * time.Second)
	//	printUid("normal")
	//}(&wg)
	//
	//for i := 1; i <= count; i++ {
	//	userThread(uint64(i), &wg)
	//}
	//
	//wg.Wait()
	//printUid("after")

	syscall.Seteuid(9999)
	os.WriteFile("/tmp/9999.txt", []byte("test"), 0o777)
	syscall.Seteuid(0)
	syscall.Seteuid(14100)
	os.WriteFile("/tmp/14100.txt", []byte("test"), 0o777)
	o, _ := exec.Command("id").Output()
	log.Printf(string(o))
	syscall.Seteuid(0)
	os.WriteFile("/tmp/0.txt", []byte("test"), 0o777)
}

func main() {
	if true {
		altMain()
		os.Exit(0)
	}
	//db, err := sql.Open("postgres", "postgres://postgres:postgrespassword@localhost/postgres")
	//if err != nil {
	//	log.Fatalf("Could not open database %v", err)
	//}
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

	s := grpc.NewServer(
		grpc.Creds(loadServerCertificate()),
		grpc.UnaryInterceptor(authInterceptor),
	)
	files.RegisterFilesProviderServiceServer(s, &server{})
	log.Printf("Server is ready!")
	if err := s.Serve(listener); err != nil {
		log.Fatalf("Failed to start server")
	}
}
