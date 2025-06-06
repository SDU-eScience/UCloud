package launcher

import (
	"path/filepath"
)

type UCloudCore2 struct{}

func (uc *UCloudCore2) Build(cb ComposeBuilder) {
	dataDir := GetDataDirectory()
	configDir := NewFile(dataDir).Child("core2-config", true)
	repoRootPath := cb.environment.repoRoot.GetAbsolutePath()

	cb.Service(
		"core2",
		"UCloud/Core 2",
		ComposeServiceJson{
			Image:    imDevImage,
			Command:  []string{"sleep", "inf"},
			Restart:  "always",
			Hostname: "core2",
			Ports: []string{
				ComposePort(51245, 51233),
			},
			Volumes: []string{
				ComposeVolume(filepath.Join(repoRootPath, "core2"), "/opt/ucloud"),
				ComposeVolume(filepath.Join(repoRootPath, "provider-integration"), "/opt/provider-integration"),
				ComposeVolume(configDir.GetAbsolutePath(), "/etc/ucloud"),
			},
		}.ToJson(),
		true,
		true,
		true,
		"",
		"",
	)
}
