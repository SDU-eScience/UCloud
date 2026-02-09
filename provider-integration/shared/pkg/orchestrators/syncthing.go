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

const SyncthingNamespace = "syncthing"

var SyncthingRetrieveConfiguration = IAppRetrieveConfiguration[SyncthingConfig](SyncthingNamespace)
var SyncthingUpdateConfiguration = IAppUpdateConfiguration[SyncthingConfig](SyncthingNamespace)
var SyncthingReset = IAppReset[SyncthingConfig](SyncthingNamespace)
var SyncthingRestart = IAppRestart[SyncthingConfig](SyncthingNamespace)

var SyncthingProviderRetrieveConfiguration = IAppProviderRetrieveConfiguration[SyncthingConfig](SyncthingNamespace)
var SyncthingProviderUpdateConfiguration = IAppProviderUpdateConfiguration[SyncthingConfig](SyncthingNamespace)
var SyncthingProviderReset = IAppProviderReset[SyncthingConfig](SyncthingNamespace)
var SyncthingProviderRestart = IAppProviderRestart[SyncthingConfig](SyncthingNamespace)
