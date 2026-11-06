package launcher

import (
	"os"
	"path/filepath"
)

func ServiceFrontend() {
	service := Service{
		Name:     "frontend",
		Title:    "Frontend",
		Flags:    SvcLogs | SvcExec,
		UiParent: UiParentCore,
	}

	command := "npm ci ; npm run start:compose"
	if os.Getenv("UCLOUD_LAUNCHER_PROFILE") == "ci" {
		command = "npm run build -- --mode compose ; npm run serve -- --mode compose --port 9000 --host 0.0.0.0"
	}

	AddService(service, DockerComposeService{
		Image:      "node:24.19.0",
		Hostname:   "frontend",
		Restart:    "always",
		WorkingDir: "/opt/ucloud",
		Command:    []string{"sh", "-c", command},
		Volumes: []string{
			Mount(filepath.Join(RepoRoot, "frontend-web/webclient"), "/opt/ucloud"),
		},
	})
}
