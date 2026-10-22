package orchestrators

import "strings"

const (
	ResourceLabelAntiAffinityGroup = "ucloud.dk/anti-affinity-group"
	ResourceLabelInitScript        = "ucloud.dk/init-script"
	ResourceLabelServiceIpAddress  = "ucloud.dk/service-ip-address"
	ResourceLabelStack             = "ucloud.dk/stack"
	ResourceLabelStackController   = "ucloud.dk/stack-controller"
	ResourceLabelDisasterRecovery  = "ucloud.dk/disaster-recovery"
	ResourceLabelStackStateFolder  = "ucloud.dk/stack-state-folder"
	ResourceLabelStackInstance     = "ucloud.dk/stack-instance"
	ResourceLabelStackEntity       = "ucloud.dk/stack-entity"
	ResourceLabelStackProvider     = "ucloud.dk/stack-provider"
	ResourceLabelStackName         = "ucloud.dk/stack-name"
	ResourceLabelStackUi           = "ucloud.dk/stack-ui"
	ResourceLabelUcxAppName        = "ucloud.dk/ucxappname"
	ResourceLabelUcxAppVersion     = "ucloud.dk/ucxappversion"
	ResourceLabelUcxPort           = "ucloud.dk/ucxport"
)

var resourceLabelsReserved = map[string]struct{}{
	ResourceLabelStack:            {},
	ResourceLabelStackName:        {},
	ResourceLabelStackInstance:    {},
	ResourceLabelStackEntity:      {},
	ResourceLabelStackStateFolder: {},
	ResourceLabelStackController:  {},
	ResourceLabelStackProvider:    {},
	ResourceLabelStackUi:          {},
	ResourceLabelUcxAppName:       {},
	ResourceLabelUcxAppVersion:    {},
	"ucloud.dk/ucx-app-name":      {},
	"ucloud.dk/ucx-app-version":   {},
	ResourceLabelDisasterRecovery: {},
}

func ResourceLabelIsReserved(key string) bool {
	_, reserved := resourceLabelsReserved[strings.ToLower(key)]
	return reserved
}
