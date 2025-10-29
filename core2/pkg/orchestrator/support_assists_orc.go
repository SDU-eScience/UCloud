package orchestrator

import (
	"net/http"
	"strconv"
	"ucloud.dk/core/pkg/accounting"
	"ucloud.dk/core/pkg/foundation"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initSupportAssistsOrc() {
	orcapi.SupportAssistRetrieveProjectInfo.Handler(func(info rpc.RequestInfo, request orcapi.SupportAssistRetrieveProjectInfoRequest) (orcapi.SupportAssistRetrieveProjectInfoResponse, *util.HttpError) {
		return retrieveProjectInfo(request.ProjectId)
	})

	orcapi.SupportAssistRetrieveJobsInfo.Handler(func(info rpc.RequestInfo, request orcapi.SupportAssistRetrieveJobInfoRequest) (orcapi.SupportAssistRetrieveJobInfoResponse, *util.HttpError) {
		return retrieveJobInfo(request.JobId)
	})

	orcapi.SupportAssistRetrieveWalletsInfo.Handler(func(info rpc.RequestInfo, request orcapi.SupportAssistRetrieveWalletInfoRequest) (orcapi.SupportAssistRetrieveWalletInfoResponse, *util.HttpError) {
		return retrieveWalletInfo(request.AllocationId)
	})
}

func retrieveProjectInfo(projectId string) (orcapi.SupportAssistRetrieveProjectInfoResponse, *util.HttpError) {
	project, err := foundation.ProjectRetrieve(
		rpc.ActorSystem,
		projectId,
		fnd.ProjectFlags{
			IncludeMembers:  true,
			IncludeGroups:   true,
			IncludeFavorite: false,
			IncludeArchived: false,
			IncludeSettings: true,
			IncludePath:     true,
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
				piActor = rpc.Actor{
					Username:         pi.Username,
					Role:             pi.Role,
					Project:          util.OptValue(rpc.ProjectId(project.Id)),
					TokenInfo:        pi.TokenInfo,
					Membership:       pi.Membership,
					Groups:           pi.Groups,
					ProviderProjects: pi.ProviderProjects,
					Domain:           pi.Domain,
					OrgId:            pi.OrgId,
				}
				break
			}
		}
	}
	
	var wallets []apm.WalletV2
	var issues []orcapi.WalletIssue
	var jobs []orcapi.Job

	wallets = accounting.WalletsBrowse(
		piActor,
		apm.WalletsBrowseRequest{},
	).Items

	foundGrants := accounting.GrantsBrowse(
		piActor,
		apm.GrantsBrowseRequest{
			ItemsPerPage:                10,
			Filter:                      util.OptValue(apm.GrantApplicationFilterShowAll),
			IncludeIngoingApplications:  util.OptValue(false),
			IncludeOutgoingApplications: util.OptValue(true),
		},
	)
	var grantResults []apm.GrantApplication

	for _, grant := range foundGrants.Items {
		reference := grant.CurrentRevision.Document.Recipient.Reference().Value
		if reference == projectId || reference == project.Specification.Title {
			grantResults = append(grantResults, grant)
		}
	}

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

	return orcapi.SupportAssistRetrieveProjectInfoResponse{
		Project:          project,
		ProjectWallets:   wallets,
		ActiveGrants:     grantResults,
		AccountingIssues: issues,
		Jobs:             jobs,
	}, nil
}

func retrieveJobInfo(jobId string) (orcapi.SupportAssistRetrieveJobInfoResponse, *util.HttpError) {
	job, err := JobsRetrieve(rpc.ActorSystem, jobId, orcapi.JobFlags{
		IncludeParameters:  false,
		IncludeApplication: false,
	})
	if err != nil {
		return orcapi.SupportAssistRetrieveJobInfoResponse{}, util.HttpErr(http.StatusNotFound, "Unknown Job")
	}
	return orcapi.SupportAssistRetrieveJobInfoResponse{JobInfo: job}, nil
}

func retrieveWalletInfo(allocationId string) (orcapi.SupportAssistRetrieveWalletInfoResponse, *util.HttpError) {
	if allocationId == "" {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Need to specify either walletId or allocationId")
	}

	id, err := strconv.Atoi(allocationId)
	if err != nil {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusBadRequest, "AllocationId in invalid format")
	}
	walletId, wallet, found := accounting.WalletV2ByAllocationID(rpc.ActorSystem, id)
	if !found {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusNotFound, "Wallet not found")
	}

	graph, _ := accounting.AccountingGraphRetrieval(walletId)
	return orcapi.SupportAssistRetrieveWalletInfoResponse{
		Wallet:          wallet,
		AccountingGraph: graph,
	}, nil
}
