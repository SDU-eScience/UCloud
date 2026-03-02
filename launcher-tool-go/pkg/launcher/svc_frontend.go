package launcher

import "path/filepath"

func ServiceFrontend() {
	service := Service{
		Name:     "frontend",
		Title:    "Frontend",
		Flags:    SvcLogs | SvcExec,
		UiParent: UiParentCore,
	}

	AddService(service, DockerComposeService{
		Image:      "node:22.21.0",
		Hostname:   "frontend",
		Restart:    "always",
		WorkingDir: "/opt/ucloud",
		Command:    []string{"sh", "-c", "npm install ; npm run start:compose"},
		Volumes: []string{
			Mount(filepath.Join(RepoRoot, "frontend-web/webclient"), "/opt/ucloud"),
		},
	})
}
