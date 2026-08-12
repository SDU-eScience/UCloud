package command

import (
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"

	"gopkg.in/yaml.v3"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type ConnectCommand struct {
	Token  string `flag:"token" usage:"Token"`
	Server string `flag:"server" usage:"Server"`
}

var ConnectCommands = map[string]CommandFunc{
	"connect": func() Command { return &ConnectCommand{} },
}

func (c *ConnectCommand) Execute() error {
	return performConnection()
}

func getRoot(w http.ResponseWriter, r *http.Request) {
	io.WriteString(w, "UCloud-CLI server is running!")
}

func cliAuth(w http.ResponseWriter, r *http.Request) error {
	token := r.URL.Query().Get("token")

	message := fmt.Sprintf("Token received %s\n", token)
	io.WriteString(w, message)
	if token == "" {
		return fmt.Errorf("no token received")
	}
	return saveToken(token)
}

func startAuthServer(ready chan<- string, authDone chan<- error) error {
	http.HandleFunc("/", getRoot)

	http.HandleFunc("/auth", func(w http.ResponseWriter, r *http.Request) {
		err := cliAuth(w, r)
		authDone <- err
	})

	ln, err := net.Listen("tcp4", ":0")
	if err != nil {
		return err
	}
	defer ln.Close()

	port := ln.Addr().(*net.TCPAddr).Port

	from := fmt.Sprintf("http://localhost:%d/auth", port)

	ready <- from

	return http.Serve(ln, nil)
}

func openBrowser(url string) error {
	var err error

	switch runtime.GOOS {
	case "linux":
		err = exec.Command("xdg-open", url).Start()
	case "windows":
		err = exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	case "darwin":
		err = exec.Command("open", url).Start()
	default:
		err = fmt.Errorf("unsupported platform")
	}
	if err != nil {
		log.Fatal(err)
	}
	return err
}

func saveToken(token string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}

	dir := filepath.Join(home, ".config", "ucloud")
	path := filepath.Join(dir, "config.yaml")

	// Make sure ~/.config/ucloud exists.
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}

	var cfg shared.Config

	data, err := os.ReadFile(path)
	if err != nil {
		if !os.IsNotExist(err) {
			return err
		}

		// Config doesn't exist yet, create an empty one.
		data = []byte{}
	}

	// Empty config is valid.
	if len(data) > 0 {
		if err := yaml.Unmarshal(data, &cfg); err != nil {
			return err
		}
	}

	// Make sure the workspace map exists.
	if cfg.Workspaces == nil {
		cfg.Workspaces = make(map[string]shared.Workspace)
	}

	workspace := cfg.Workspaces[cfg.CurrentWorkspace]
	workspace.Token = token

	cfg.Workspaces[cfg.CurrentWorkspace] = workspace

	data, err = yaml.Marshal(&cfg)
	if err != nil {
		return err
	}

	return os.WriteFile(path, data, 0600)
}

func performConnection() error {
	ready := make(chan string)
	authDone := make(chan error, 1)

	go func() {
		if err := startAuthServer(ready, authDone); err != nil {
			fmt.Println("server:", err)
		}
	}()

	from := <-ready

	connectionUrl := "https://ucloud.localhost.direct/app/connect?redirect=" +
		url.QueryEscape(from)

	if err := openBrowser(connectionUrl); err != nil {
		return err
	}

	return <-authDone
}
