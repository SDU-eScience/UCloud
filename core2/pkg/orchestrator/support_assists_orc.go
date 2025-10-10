package orchestrator

import (
	"net/http"
	"strconv"
	"ucloud.dk/core/pkg/accounting"
	"ucloud.dk/core/pkg/foundation"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initSupportAssistsOrc() {
	orcapi.SupportAssistRetrieveProjectInfo.Handler(func(info rpc.RequestInfo, request orcapi.SupportAssistRetrieveProjectInfoRequest) (orcapi.SupportAssistRetrieveProjectInfoResponse, *util.HttpError) {
		return retrieveProjectInfo(request.ProjectId, request.Flags)
	})

	orcapi.SupportAssistRetrieveJobsInfo.Handler(func(info rpc.RequestInfo, request orcapi.SupportAssistRetrieveJobInfoRequest) (orcapi.SupportAssistRetrieveJobInfoResponse, *util.HttpError) {
		return retrieveJobInfo(request.JobId)
	})

	orcapi.SupportAssistRetrieveWalletsInfo.Handler(func(info rpc.RequestInfo, request orcapi.SupportAssistRetrieveWalletInfoRequest) (orcapi.SupportAssistRetrieveWalletInfoResponse, *util.HttpError) {
		return retrieveWalletInfo(request.AllocationId, request.WalletId, request.Flags)
	})
}

func retrieveProjectInfo(projectId string, flags orcapi.SupportAssistProjectInfoFlags) (orcapi.SupportAssistRetrieveProjectInfoResponse, *util.HttpError) {
	project, err := foundation.ProjectRetrieve(
		rpc.ActorSystem,
		projectId,
		fnd.ProjectFlags{
			IncludeMembers:  flags.IncludeMembers,
			IncludeGroups:   false,
			IncludeFavorite: false,
			IncludeArchived: false,
			IncludeSettings: false,
			IncludePath:     false,
		},
		fnd.ProjectRoleUser,
	)
	if err != nil {
		return orcapi.SupportAssistRetrieveProjectInfoResponse{}, err
	}
	var piActor rpc.Actor
	for _, mem := range project.Status.Members {
		if mem.Role.Satisfies(fnd.ProjectRolePI) {
			pi, found := rpc.LookupActor(mem.Username)
			if found {
				piActor = pi
				break
			}
		}
	}

	var wallets []apm.WalletV2
	var issues []orcapi.WalletIssue
	var jobs []orcapi.Job

	if flags.IncludeAccountingInfo {
		wallets = accounting.WalletsBrowse(
			piActor,
			apm.WalletsBrowseRequest{},
		).Items

		/*for _, wallet := range wallets {

			//If not able to use all available resource we have an issue
			if wallet.MaxUsable < wallet.Quota-wallet.TotalUsage {
				for _, group := range wallet.AllocationGroups {
					if group.Parent.Present {
						parent := group.Parent.Value
					}
				}
			}
		}*/
	}

	if flags.IncludeJobsInfo {
		foundJobs, err := JobsBrowse(
			piActor,
			util.OptString{},
			50,
			orcapi.JobFlags{
				IncludeParameters:  false,
				IncludeApplication: false,
			},
		)
		if err != nil {
			return orcapi.SupportAssistRetrieveProjectInfoResponse{}, err
		}
		jobs = foundJobs.Items
	}

	return orcapi.SupportAssistRetrieveProjectInfoResponse{
		Project:          project,
		ProjectWallets:   wallets,
		AccountingIssues: issues,
		Jobs:             jobs,
	}, nil
}

func retrieveJobInfo(jobId string) (orcapi.SupportAssistRetrieveJobInfoResponse, *util.HttpError) {
	job, err := orchestrators.RetrieveJob(jobId, orchestrators.BrowseJobsFlags{
		IncludeParameters:  true,
		IncludeApplication: true,
		IncludeProduct:     true,
		IncludeUpdates:     true,
	})
	if err != nil {
		return orcapi.SupportAssistRetrieveJobInfoResponse{}, util.HttpErr(http.StatusNotFound, "Unknown Job")
	}
	return orcapi.SupportAssistRetrieveJobInfoResponse{JobInfo: job}, nil
}

func retrieveWalletInfo(allocationId string, walletId string, flags orcapi.SupportAssistWalletInfoFlags) (orcapi.SupportAssistRetrieveWalletInfoResponse, *util.HttpError) {
	if walletId != "" && allocationId != "" {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Cannot specify both walletId and allocationId")
	}
	if walletId == "" && allocationId == "" {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Need to specify either walletId or allocationId")
	}

	if walletId != "" {
		id, err := strconv.Atoi(walletId)
		if err != nil {
			return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Wallet ID in invalid format")
		}
		wallet, found := accounting.WalletV2ById(rpc.ActorSystem, id)
		if !found {
			return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusNotFound, "Wallet not found")
		}
		return orcapi.SupportAssistRetrieveWalletInfoResponse{
			Wallet: wallet,
		}, nil
	}

	if allocationId != "" {
		id, err := strconv.Atoi(allocationId)
		if err != nil {
			return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusBadRequest, "AllocationId in invalid format")
		}
		wallet, found := accounting.WalletV2ByAllocationID(rpc.ActorSystem, id)
		if !found {
			return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusNotFound, "Wallet not found")
		}
		return orcapi.SupportAssistRetrieveWalletInfoResponse{
			Wallet: wallet,
		}, nil
	}

	return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusNotFound, "Wallet not found")
}
