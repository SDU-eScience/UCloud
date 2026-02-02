package orchestrator

import (
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initSyncthing() {
	// !! NOTE(Dan): The comment below is potentially outdated and has simply been moved from the old Core. !!
	// -----------------------------------------------------------------------------------------------------------------
	// NOTE(Dan): Syncthing is provided in UCloud as an integrated application. This essentially means that the
	// orchestrator is providing a very limited API to the provider centered around pushing configuration. The
	// orchestrator expects the provider to use this configuration to update the storage and compute systems, in such
	// a way that Syncthing responds to the requested changes.
	//
	// The orchestrator has the following assumptions about how the provider does this:
	//
	// 1. They must register a job with UCloud (through `JobsControl.register`).
	//    a. It is assumed that every user gets their own job.
	//    b. Currently, the frontend does a lot of the work in finding the jobs, but the orchestrator might change to
	//       expose an endpoint which returns the relevant job IDs instead.
	// 2. The application must have at least one parameter called "stateFolder" which should be an input directory.
	// 3. The folder which has the state must contain a file called "ucloud_device_id.txt". This file must contain the
	//    device ID of the Syncthing server.
	//
	// For more details, see the implementation in UCloud/Compute.
	//
	// Almost all functions in this service are simply proxying the relevant information to the provider. The extra
	// information added by this service is mostly related to authorization.

	actorToOwner := func(actor rpc.Actor) orcapi.ResourceOwner {
		return orcapi.ResourceOwner{
			CreatedBy: actor.Username,
			Project: util.OptMap(actor.Project, func(value rpc.ProjectId) string {
				return string(value)
			}),
		}
	}

	orcapi.SyncthingRetrieveConfiguration.Handler(func(info rpc.RequestInfo, request orcapi.IAppRetrieveConfigRequest) (orcapi.IAppRetrieveConfigResponse[orcapi.SyncthingConfig], *util.HttpError) {
		resp, err := InvokeProvider(
			request.Provider,
			orcapi.SyncthingProviderRetrieveConfiguration,
			orcapi.IAppProviderRetrieveConfigRequest{
				ProductId: request.ProductId,
				Principal: actorToOwner(info.Actor),
			},
			ProviderCallOpts{
				Username: util.OptValue(info.Actor.Username),
			},
		)

		return resp, err
	})

	orcapi.SyncthingUpdateConfiguration.Handler(func(info rpc.RequestInfo, request orcapi.IAppUpdateConfigurationRequest[orcapi.SyncthingConfig]) (util.Empty, *util.HttpError) {
		// NOTE(Dan): This used to do permission checks in the Core, but this is no longer required since the provider
		// will do this instead.

		_, err := InvokeProvider(
			request.Provider,
			orcapi.SyncthingProviderUpdateConfiguration,
			orcapi.IAppProviderUpdateConfigurationRequest[orcapi.SyncthingConfig]{
				ProductId:    request.ProductId,
				Principal:    actorToOwner(info.Actor),
				Config:       request.Config,
				ExpectedETag: request.ExpectedETag,
			},
			ProviderCallOpts{
				Username: util.OptValue(info.Actor.Username),
			},
		)

		return util.Empty{}, err
	})

	orcapi.SyncthingRestart.Handler(func(info rpc.RequestInfo, request orcapi.IAppRestartRequest) (util.Empty, *util.HttpError) {
		_, err := InvokeProvider(
			request.Provider,
			orcapi.SyncthingProviderRestart,
			orcapi.IAppProviderRestartRequest{
				ProductId: request.ProductId,
				Principal: actorToOwner(info.Actor),
			},
			ProviderCallOpts{
				Username: util.OptValue(info.Actor.Username),
			},
		)

		return util.Empty{}, err
	})

	orcapi.SyncthingReset.Handler(func(info rpc.RequestInfo, request orcapi.IAppRestartRequest) (util.Empty, *util.HttpError) {
		_, err := InvokeProvider(
			request.Provider,
			orcapi.SyncthingProviderReset,
			orcapi.IAppProviderResetRequest{
				ProductId: request.ProductId,
				Principal: actorToOwner(info.Actor),
			},
			ProviderCallOpts{
				Username: util.OptValue(info.Actor.Username),
			},
		)

		return util.Empty{}, err
	})
}
