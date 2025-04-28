package orchestrators

type SyncthingConfig struct {
	Folders []SyncthingFolder `json:"folders"`
	Devices []SyncthingDevice `json:"devices"`
}

type SyncthingFolder struct {
	UCloudPath string `json:"ucloudPath"`
	Path       string `json:"path"`
	Id         string `json:"id"`
}

type SyncthingDevice struct {
	DeviceId string `json:"deviceId"`
	Label    string `json:"label"`
}

type SyncthingOrchestratorInfo struct {
	FolderPathToPermission map[string]Permission `json:"folderPathToPermission"`
}
