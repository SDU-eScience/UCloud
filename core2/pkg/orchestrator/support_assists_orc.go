package orchestrator

import (
	"fmt"
	"net/http"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/foundation"
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
	project, err := foundation.ProjectRetrieve.Invoke(foundation.ProjectRetrieveRequest{
		Id: projectId,
		ProjectFlags: foundation.ProjectFlags{
			IncludeMembers:  true,
			IncludeGroups:   true,
			IncludeFavorite: false,
			IncludeArchived: false,
			IncludeSettings: true,
			IncludePath:     true,
		},
	})
	if err != nil {
		return orcapi.SupportAssistRetrieveProjectInfoResponse{}, err
	}
	piUser := ""
	for _, mem := range project.Status.Members {
		if mem.Role.Satisfies(foundation.ProjectRolePI) {
			piUser = mem.Username
		}
	}

	var wallets []apm.WalletV2
	var issues []apm.WalletV2
	var jobs []orcapi.Job

	projectAccountingInfo, err := apm.RetrieveAccountingInfoForProject.Invoke(
		apm.RetrieveAccountingInfoForProjectRequest{
			ProjectId:  projectId,
			PiUsername: piUser,
		},
	)

	if err != nil {
		return orcapi.SupportAssistRetrieveProjectInfoResponse{}, err
	}

	wallets = projectAccountingInfo.Wallets
	foundGrants := projectAccountingInfo.Grants

	var grantResults []apm.GrantApplication

	for _, grant := range foundGrants {
		reference := grant.CurrentRevision.Document.Recipient.Reference().Value
		if reference == projectId || reference == project.Specification.Title {
			grantResults = append(grantResults, grant)
		}
	}

	//TODO() Most likely there is a better way to determine which wallet is the cause of problems
	allAncestors := projectAccountingInfo.Ancestors
	for _, wallet := range wallets {
		var problematicWallet apm.WalletV2
		//If we cannot use all remaining quota we have a problem
		if wallet.MaxUsable-(wallet.Quota-wallet.TotalUsage) != 0 {
			key := fmt.Sprintf("%s@%s", wallet.PaysFor.Name, wallet.PaysFor.Provider)
			ancestors := allAncestors[key]
			for i, ancestor := range ancestors {
				if ancestor.MaxUsable-(ancestor.Quota-ancestor.TotalUsage) != 0 {
					if i == len(ancestors)-1 {
						problematicWallet = ancestor
					} else {
						problematicWallet = ancestors[i+1]
					}
				}
			}
		}
		issues = append(issues, problematicWallet)
	}

	pi, found := rpc.LookupActor(piUser)
	if !found {
		return orcapi.SupportAssistRetrieveProjectInfoResponse{}, util.HttpErr(http.StatusBadRequest, "Cannot lookup PI")
	}
	piActor := rpc.Actor{
		Username:         pi.Username,
		Role:             pi.Role,
		Project:          util.OptValue(rpc.ProjectId(projectId)),
		TokenInfo:        pi.TokenInfo,
		Membership:       pi.Membership,
		Groups:           pi.Groups,
		ProviderProjects: pi.ProviderProjects,
		Domain:           pi.Domain,
		OrgId:            pi.OrgId,
	}

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

	response, err := apm.RetrieveWalletByAllocationId.Invoke(apm.RetrieveWalletByAllocationRequest{
		AllocationId: allocationId,
	})
	if err != nil {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, util.HttpErr(http.StatusNotFound, "Wallet not found")
	}

	graphResponse, err := apm.RetrieveAccountingGraph.Invoke(apm.RetrieveAccountingGraphRequest{WalletId: response.Id})
	if err != nil {
		return orcapi.SupportAssistRetrieveWalletInfoResponse{}, err
	}

	return orcapi.SupportAssistRetrieveWalletInfoResponse{
		Wallet:          response.Wallet,
		AccountingGraph: graphResponse.Graph,
	}, nil
}
