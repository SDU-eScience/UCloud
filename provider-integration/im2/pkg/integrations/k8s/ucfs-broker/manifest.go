package ucfs_broker

const (
	EnvManifest  = "UCFS_BROKER_MANIFEST"
	EnvReadyFile = "UCFS_BROKER_READY_FILE"
	EnvRoot      = "UCFS_BROKER_ROOT"

	DefaultRoot = "/ucloud"
)

type MountSpec struct {
	SourcePath string `json:"sourcePath"`
	Name       string `json:"name"`
	ReadOnly   bool   `json:"readOnly"`
}

type WorkspaceMount struct {
	RootPath string      `json:"rootPath"`
	Mounts   []MountSpec `json:"mounts"`
}

type Manifest struct {
	Workspaces []WorkspaceMount `json:"workspaces"`
}
