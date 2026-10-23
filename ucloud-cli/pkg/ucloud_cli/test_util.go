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

func FindCompute() accapi.ProductV2 {
	products, httpErr := accapi.ProductsBrowse.Invoke(accapi.ProductsBrowseRequest{})
	if httpErr != nil {
		panic(httpErr)
	}
	if len(products.Items) == 0 {
		panic("no products found")
	}
	for _, product := range products.Items {
		if product.Category.Provider == "k8s" && product.ProductType == "COMPUTE" {
			return product
		}
	}
	panic("no compute product found")

}

func allocateRoot(grantId string) error {

	computeProduct := FindCompute()
	provider := "k8s"
	Quota := int64(100000)
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
				Quota: Quota,
			},
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: provider,
					Name:     "storage",
				},
				Quota: Quota,
				Start: start,
				End:   end,
			},
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: "k8s",
					Name:     "test-license",
				},
				Quota: Quota,
				Start: start,
				End:   end,
			},
			{
				Category: accapi.ProductCategoryIdV2{
					Provider: provider,
					Name:     computeProduct.Category.Name,
				},
				Quota: Quota,
				Start: start,
				End:   end,
			},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to allocate test workspace: %s", httpErr.Why)
	}
	err := createGrant(grantId, computeProduct.Category.Name)
	if err != nil {
		return err
	}
	return nil
}

func createTestGrant(grantGiverId string, computeName string) accapi.GrantDocument {
	provider := "k8s"
	grant := accapi.GrantDocument{
		AllocationRequests: []accapi.AllocationRequest{
			{
				Category:         "public-ip",
				BalanceRequested: util.OptValue(int64(100)),
				Period: accapi.Period{
					Start: util.OptValue(fndapi.Timestamp(time.Now())),
					End:   util.OptValue(fndapi.Timestamp(time.Now().Add(time.Hour * 24 * 365 * 10))),
				},
				GrantGiver: grantGiverId,
				Provider:   provider,
			},
			{
				Category:         "storage",
				BalanceRequested: util.OptValue(int64(100)),
				Period: accapi.Period{
					Start: util.OptValue(fndapi.Timestamp(time.Now())),
					End:   util.OptValue(fndapi.Timestamp(time.Now().Add(time.Hour * 24 * 365 * 10))),
				},
				GrantGiver: grantGiverId,
				Provider:   provider,
			},
			{
				Category:         computeName,
				BalanceRequested: util.OptValue(int64(240)),
				Period: accapi.Period{
					Start: util.OptValue(fndapi.Timestamp(time.Now())),
					End:   util.OptValue(fndapi.Timestamp(time.Now().Add(time.Hour * 24 * 365 * 10))),
				},
				GrantGiver: grantGiverId,
				Provider:   provider,
			},
		},
		Recipient: accapi.Recipient{
			Title: util.OptValue("test-workspace"),
			Type:  accapi.RecipientTypeNewProject,
		},
		Form: accapi.Form{
			AnswerForms: []accapi.AnswerForm{
				{
					AllocatorId: grantGiverId,
					AnswerFields: []accapi.AnswerFieldForm{
						{
							Answer: "Hello World",
							Field: accapi.FormField{
								Name:        "new_project",
								Description: "Please describe why you are applying for this resources",
								Optional:    false,
								Title:       "Application",
							},
						},
					},
					TemplateRevisionNumber: 0,
				},
			},
			Type:         accapi.FormTypeStructured,
			SubAllocator: util.OptValue(false),
			Text:         "",
		},
	}
	return grant
}

func createGrant(grantId string, computeName string) error {
	grant, httpErr := accapi.GrantsSubmitRevision.Invoke(accapi.GrantsSubmitRevisionRequest{
		Comment:  "Submitted the application",
		Revision: createTestGrant(grantId, computeName),
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
	fmt.Printf("Successfully created grants %v\n", grant)
	return nil
}

func CreateTestWorkspace() error {
	if TestEnvCreated {
		return nil
	}

	shared.InitializeUCloudClient()

	rootWs, err := shared.FindWorkspaceByName("provider-k8s")
	if err != nil {
		return err
	}
	shared.SetActiveWorkspace(rootWs.Id)

	wss, wsErr := fndapi.ProjectBrowse.Invoke(fndapi.ProjectBrowseRequest{})
	if wsErr.AsError() != nil {
		return fmt.Errorf("failed to list workspaces: %s", wsErr.Why)
	}
	for _, ws := range wss.Items {
		if ws.Specification.Title == "test-workspace" {
			shared.SetActiveWorkspace(ws.Id)
			fmt.Println("test-workspace is already made, skipping creation.")
			return nil
		}
	}
	// allocate root resources and create test-workspace
	allocateErr := allocateRoot(rootWs.Id)

	if allocateErr != nil {
		return allocateErr
	}
	TestEnvCreated = true
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
	shared.TestMode = true
	SetTestEnv()
	shared.LoginDirectlyIntoDev()
}
