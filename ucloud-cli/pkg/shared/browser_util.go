package shared

import (
	"fmt"
	"os/exec"
	"runtime"
)
import (
	"net/url"
	"strconv"
)

type ApplicationCreatorRoute struct {
	Operation             string // "newManaged" | "newCustom" | "newVersion" | "fork"
	ApplicationKind       string // "managed" | "custom"
	Workspace             string
	Name                  string
	Version               string
	Provider              string
	Category              int
	SourceApplicationKind string // "managed" | "custom"
	SourceProvider        string
	ReturnTo              string
}

func vncURL(origin string, jobID string) string {
	return fmt.Sprintf("%s/app/applications/vnc/%s/0?hide-frame", origin, jobID)
}

func ApplicationCreatorURL(route ApplicationCreatorRoute) string {
	q := url.Values{}
	set := func(key, value string) {
		if value != "" {
			q.Set(key, value)
		}
	}
	set("operation", route.Operation)
	set("applicationKind", route.ApplicationKind)
	set("workspace", route.Workspace)
	set("name", route.Name)
	set("version", route.Version)
	set("provider", route.Provider)
	set("sourceApplicationKind", route.SourceApplicationKind)
	set("sourceProvider", route.SourceProvider)
	set("returnTo", route.ReturnTo)
	if route.Category != 0 {
		q.Set("category", strconv.Itoa(route.Category))
	}
	return "/app/applications/creator?" + q.Encode()
}

func OpenBrowser(url string) error {
	if url == "" {
		return fmt.Errorf("Failed to open browser: no URL provided")
	}
	fmt.Printf("Opening browser to %s\n", url)
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
	return nil
}
