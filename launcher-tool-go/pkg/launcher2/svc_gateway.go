package launcher2

import (
	_ "embed"
	"os"
	"path/filepath"
)

//go:embed config/gateway/Caddyfile
var gatewayCaddyFile []byte

func ServiceGateway() {
	service := Service{
		Name:     "gateway",
		Title:    "Gateway",
		Flags:    SvcLogs | SvcExec,
		UiParent: UiParentCore,
	}

	data := AddDirectory(service, "data")
	configDir := AddDirectory(service, "config")

	certDir := "./.compose/certs"

	AddInstaller(service, func() {
		_ = os.MkdirAll(certDir, 0700)

		certFile := filepath.Join(certDir, "tls.crt")
		keyFile := filepath.Join(certDir, "tls.key")

		_, err := os.Stat(certFile)
		needsCert := err != nil

		_, err = os.Stat(keyFile)
		needsCert = needsCert || err != nil

		if needsCert {
			installerScript := ""
			if HasPty {
				installerScript += `
					echo "UCloud needs to install a root certificate for development."
					echo;
					echo "• This certificate is required for local TLS and it simplifies the setup."
					echo "• You might be prompted for your sudo password."
					echo "• https://github.com/FiloSottile/mkcert is used for generating the certificate."
					echo;
					echo "Press enter to continue"
					read;
				`
			}

			installerScript += `
				cd .compose/certs
				git clone https://github.com/FiloSottile/mkcert && cd mkcert
				git checkout v1.4.4
				go build -ldflags "-X main.Version=$(git describe --tags)"
				./mkcert localhost.direct "*.localhost.direct"
				./mkcert -install
				mv *key.pem ../tls.key
				mv *.pem ../tls.crt
				cd ../
				rm -rf mkcert
				cd ../..
			`
			installerScriptPath := filepath.Join(certDir, "installer.sh")
			_ = os.WriteFile(installerScriptPath, []byte(installerScript), 0700)

			command := []string{"bash", installerScriptPath}
			if HasPty {
				_ = PtyExecCommand(command, &StatusInfo{StatusLine: "Installing certificates"})
			} else {
				StreamingExecute("Installing certificates", command, ExecuteOptions{})
			}
		}

		_ = os.WriteFile(filepath.Join(configDir, "Caddyfile"), gatewayCaddyFile, 0660)
	})

	AddService(service, DockerComposeService{
		// NOTE: The gateway is from this repo with no changes:
		// https://github.com/mholt/caddy-grpc-web
		Image:    "dreg.cloud.sdu.dk/ucloud/caddy-gateway:1",
		Hostname: "gateway",
		Restart:  "always",
		Ports:    []string{"80:80", "443:443"},
		Volumes: []string{
			Mount(data, "/data"),
			Mount(filepath.Join(configDir, "Caddyfile"), "/etc/caddy/Caddyfile"),
			Mount("./certs", "/certs"),
		},
	})
}
