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
	"strings"

	anyascii "github.com/anyascii/go"
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
			display: block;
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
	projectId := r.URL.Query().Get("projectId")
	projectTitle := r.URL.Query().Get("projectTitle")

	//message := fmt.Sprintf("ProjectId %s\nProjectTitle %s\nToken received %s\n", projectId, projectTitle, token)
	io.WriteString(w, successMessage())
	if token == "" {
		return fmt.Errorf("no token received")
	}
	return saveConfig(token, projectId, projectTitle)
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

func repositoryProjectName(title string) string {
	transliterated := strings.ToLower(anyascii.Transliterate(title))
	var result strings.Builder
	separator := false
	for _, r := range transliterated {
		if r >= 'a' && r <= 'z' || r >= '0' && r <= '9' {
			if separator && result.Len() > 0 {
				result.WriteByte('-')
			}
			result.WriteRune(r)
			separator = false
		} else {
			separator = true
		}
	}
	name := strings.Trim(result.String(), "-")
	if name == "" {
		name = "project"
	}
	if len(name) > 32 {
		name = strings.TrimRight(name[:32], "-")
	}
	return name
}

func saveConfig(token string, projectId string, projectTitle string) error {
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

	if cfg.Environments == nil {
		cfg.Environments = make(map[string]shared.Environment)
		cfg.Environments["ucloud"] = shared.Environment{
			URL: "https://cloud.sdu.dk",
		}
	}
	// Defaults

	cfg.DefaultEnvironment = "ucloud"
	cfg.Defaults = shared.Defaults{
		Output:       "table",
		ItemsPerPage: 100,
	}

	cfg.CurrentWorkspace = repositoryProjectName(projectTitle)
	cfg.Workspaces[cfg.CurrentWorkspace] = shared.Workspace{}
	workspace := cfg.Workspaces[cfg.CurrentWorkspace]
	workspace.TokenRef = token
	workspace.Project = projectId
	workspace.Title = projectTitle
	workspace.Environment = cfg.DefaultEnvironment

	cfg.Workspaces[cfg.CurrentWorkspace] = workspace

	data, err = yaml.Marshal(&cfg)
	if err != nil {
		return err
	}
	fmt.Println("Saving config to:", path)

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
