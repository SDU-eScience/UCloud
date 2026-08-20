package command

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os/exec"
	"runtime"
	"time"

	"ucloud.dk/ucloud_cli/pkg/shared"
)

const (
	Port = ":59421"
	UCloudCliPath  = "/app/login/external?service=ucloud-cli"
)

type ConnectCommand struct {
	Token  string `flag:"token" usage:"Token"`
	Server string `flag:"server" usage:"Server"`
	Dev    bool   `flag:"dev" usage:"Dev mode"`
}

var ConnectCommands = map[string]CommandFunc{
	"connect": func() Command { return &ConnectCommand{} },
}

func (c *ConnectCommand) Execute() error {
	return performConnection(c.Dev)
}

func getRoot(w http.ResponseWriter, r *http.Request) {
	io.WriteString(w, "UCloud-CLI server is running!")
}

func successMessage() string {
	return `
	<!DOCTYPE html>
	<html>
		<head>
		<style>
		html, body {
			height: 100%;
			margin: 0;
		}
		body {
			display: flex;
			flex-direction: column;
			align-items: center;
			justify-content: center;
			font-family: sans-serif;
			text-align: center;
		}
		</style>
		</head>
		<body>
			<h1>Successfully connected to UCloud.</h1>
			<p>You can now close this window.</p>
		</body>
	</html>
`
}

func cliAuth(w http.ResponseWriter, r *http.Request) error {
	token := r.URL.Query().Get("token")
	username := r.URL.Query().Get("username")
	io.WriteString(w, successMessage())
	// We flush to signal that the page is ready to be displayed.
	if flusher, ok := w.(http.Flusher); ok {
		flusher.Flush()
	}
	if token == "" {
		return fmt.Errorf("no token received")
	}

	return saveConfig(token, username)
}

func startAuthServer(ready chan<- struct{}, authDone chan<- error) error {
	mux := http.NewServeMux()

	var server *http.Server

	mux.HandleFunc("/", getRoot)
	mux.HandleFunc("/auth", func(w http.ResponseWriter, r *http.Request) {
		err := cliAuth(w, r)

		select {
		case authDone <- err:
		default:
		}

		go func() {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			_ = server.Shutdown(ctx)
		}()
	})

	ln, err := net.Listen("tcp4", "127.0.0.1"+PORT)
	if err != nil {
		return fmt.Errorf("start auth server: %w", err)
	}
	defer ln.Close()

	server = &http.Server{
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}

	ready <- struct{}{}

	err = server.Serve(ln)
	if err != nil && !errors.Is(err, http.ErrServerClosed) {
		return fmt.Errorf("serve auth requests: %w", err)
	}

	return nil
}

func openBrowser(url string) error {
	switch runtime.GOOS {
	case "linux":
		return exec.Command("xdg-open", url).Start()
	case "windows":
		return exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	case "darwin":
		return exec.Command("open", url).Start()
	default:
		return fmt.Errorf("unsupported platform: %s", runtime.GOOS)
	}
}

func saveConfig(token string, username string) error {
	cfg, err := shared.ReadConfig()
	if err != nil {
		return err
	}
	cfg.TokenRef = token
	cfg.Username = username

	return shared.SaveConfig(cfg)
}

func performConnection(dev bool) error {
	ready := make(chan struct{})
	authDone := make(chan error, 1)
	serverErr := make(chan error, 1)

	cfg, err := shared.ReadConfig()
	if err != nil {
		return err

	}
	currentEnv := cfg.Environments[cfg.DefaultEnvironment]
	baseURL := currentEnv.URL
	if dev {
		baseURL = shared.DEV_SERVER
	}

	go func() {
		if err := startAuthServer(ready, authDone); err != nil {
			serverErr <- err
		}
	}()

	select {
	case <-ready:
	case err := <-serverErr:
		return err
	case <-time.After(5 * time.Second):
		return fmt.Errorf("auth server did not become ready in time")
	}

	connectionURL := baseURL + UCLOUD_CLI_PATH
	if err := openBrowser(connectionURL); err != nil {
		return err
	}

	select {
	case err := <-authDone:
		return err
	case err := <-serverErr:
		return err
	case <-time.After(5 * time.Minute):
		return fmt.Errorf("timed out waiting for authentication")
	}
}
