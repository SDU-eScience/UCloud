package ucloud_cli

import (
	"fmt"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

var TestEnvCreated = false

func SetTestEnv() {
	devServerUrl := "https://ucloud.localhost.direct"
	input := []string{"environment", "add", "dev", "--url", devServerUrl}
	cmd, err := Parse(input)
	err = cmd.Execute()
	if err != nil {
		panic(err)
	}
	input = []string{"environment", "use", "dev"}
	cmd, err = Parse(input)
	err = cmd.Execute()
	if err != nil {
		panic(err)
	}
}

func alreadyRootAllocated() bool {
	start := time.Now().Add(-1 * time.Hour)
	end := time.Now().Add(time.Hour)
	usage, httpErr := accapi.UsageRetrieve.Invoke(accapi.UsageRetrieveRequest{
		Start: uint64(start.UnixMilli()),
		End:   uint64(end.UnixMilli()),
	})
	if httpErr.AsError() != nil {
		return false
	}
	quota := usage.Reports[0].Kpis.QuotaAtEnd
	return quota > 0
}

func allocateRoot() error {

	provider := "k8s"
	start := fndapi.Timestamp(time.Now())
	end := fndapi.Timestamp(time.Now().Add(time.Hour * 24 * 365 * 10))
	_, httpErr := accapi.RootAllocate.Invoke(fndapi.BulkRequest[accapi.RootAllocateRequest]{
		Items: []accapi.RootAllocateRequest{
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: provider,
					Name:     "public-ip",
				},
				Start: start,
				End:   end,
				Quota: 10,
			},
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: provider,
					Name:     "storage",
				},
				Quota: 10,
				Start: start,
				End:   end,
			},
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: "k8s",
					Name:     "test-license",
				},
				Quota: 10,
				Start: start,
				End:   end,
			},
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: provider,
					Name:     "u1-standard",
				},
				Quota: 600,
				Start: start,
				End:   end,
			},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to allocate test workspace: %s", httpErr.Why)
	}
	err := createGrant()
	if err != nil {
		return err
	}
	return nil
}

func createGrant() error {
	// TODO: We need to mimic the frontend and create a grant
	grant, httpErr := accapi.GrantsSubmitRevision.Invoke(accapi.GrantsSubmitRevisionRequest{
		Comment: "test grant",
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to create grants: %s", httpErr.Why)
	}
	_, httpErr = accapi.GrantsUpdateState.Invoke(accapi.GrantsUpdateStateRequest{
		ApplicationId: grant.Id,
		NewState:      accapi.GrantApplicationStateApproved,
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to update grants: %s", httpErr.Why)
	}
	return nil
}

func CreateTestWorkspace() error {
	if TestEnvCreated {
		return nil
	}

	cfg := shared.InitializeUCloudClient()

	rootWs, err := shared.FindWorkspaceByName("provider-k8s")
	if err != nil {
		return err
	}
	shared.SetActiveWorkspace(rootWs.Id)

	allocateErr := allocateRoot()
	if allocateErr != nil {
		return allocateErr
	}

	ws, httpErr := fndapi.ProjectCreate.Invoke(fndapi.BulkRequest[fndapi.ProjectSpecification]{
		Items: []fndapi.ProjectSpecification{
			{
				Title:               "test-workspace",
				CanConsumeResources: true,
			},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to create test workspace: %s", httpErr.Why)
	}
	cfg.CurrentWorkspace = util.OptValue(ws.Responses[0].Id)

	TestEnvCreated = true
	accapi.GrantsSubmitRevision.Invoke(accapi.GrantsSubmitRevisionRequest{})
	return nil
}

func CleanupTestWorkspace() error {
	_, httpErr := accapi.RootAllocate.Invoke(fndapi.BulkRequest[accapi.RootAllocateRequest]{
		Items: []accapi.RootAllocateRequest{},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to cleanup test workspace: %s", httpErr.Why)
	}

	return nil
}

func isAlreadyConnected() bool {
	shared.TestMode = true
	cfg, err := shared.ReadConfig()
	if err != nil {
		return false
	}
	if cfg.TokenRef == "" {
		SetTestEnv()
		return false
	}
	return true
}

func login() {
	//if isAlreadyConnected() {
	//	return
	//}
	shared.TestMode = true
	SetTestEnv()
	shared.LoginDirectlyIntoDev()
}
