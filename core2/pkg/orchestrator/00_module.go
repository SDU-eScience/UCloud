package orchestrator

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// TODO
type integrationRespTemporary struct {
	Provider               string `json:"provider"`
	Connected              bool   `json:"connected"`
	ProviderTitle          string `json:"providerTitle"`
	RequiresMessageSigning bool   `json:"requiresMessageSigning"`
	UnmanagedConnection    bool   `json:"unmanagedConnection"`
}

func Init() {
	initProviders()
	InitResources()
	initFeatures()

	initDrives()
	initFiles()
	initAppSearchIndex()
	initAppLogos()
	initAppCatalog()
	initJobs()
	initLicenses()
	initPublicIps()
	initIngresses()
	initTasks()
	initMetadata()

	// TODO Dummy implementation to make frontend happy
	r := rpc.Call[util.Empty, fndapi.PageV2[integrationRespTemporary]]{
		BaseContext: "providers/integration",
		Convention:  rpc.ConventionBrowse,
		Roles:       rpc.RolesEndUser,
	}

	// TODO TODO TODO
	r.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.PageV2[integrationRespTemporary], *util.HttpError) {
		p := fndapi.PageV2[integrationRespTemporary]{ItemsPerPage: 250}
		p.Items = append(p.Items, integrationRespTemporary{
			Provider:               "4412",
			Connected:              true,
			ProviderTitle:          "gok8s",
			RequiresMessageSigning: false,
			UnmanagedConnection:    false,
		})
		return p, nil
	})
}
