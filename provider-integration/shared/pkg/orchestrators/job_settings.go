package orchestrators

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const jobSettingsContext = "jobs/settings"

type JobSettings struct {
	Toggled         bool                `json:"toggled"`
	SampleRateValue util.Option[string] `json:"sampleRateValue"`
}

var JobSettingsUpdate = rpc.Call[JobSettings, util.Empty]{
	BaseContext: jobSettingsContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var JobSettingsRetrieve = rpc.Call[util.Empty, JobSettings]{
	BaseContext: jobSettingsContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}
