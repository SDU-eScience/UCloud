package command

import (
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type ComputeProductsCommand struct {
	Provider string `flag:"provider" usage:"Provider name"`
	Verbose  bool   `flag:"verbose" usage:"Show verbose output"`
}

var ComputeCommands = map[string]CommandFunc{
	"products": func() Command { return &ComputeProductsCommand{} },
}

func printMachineTypeDetails(t *termio.Table, p orcapi.ResolvedSupport[orcapi.JobSupport]) {
	t.Cell(p.Product.Name)
	t.Cell(p.Product.Description)
}

func (c ComputeProductsCommand) Execute() error {
	shared.InitializeUCloudClient()
	products, httpErr := orcapi.JobsRetrieveProducts.Invoke(util.Empty{})
	if httpErr.AsError() != nil {
		return httpErr.AsError()
	}
	t := termio.Table{}
	t.AppendHeader("Machine type")
	t.AppendHeader("Description")
	for _, product := range products.ProductsByProvider {
		for _, p := range product {
			if p.Product.HiddenInGrantApplications {
				continue
			}
			if c.Provider == p.Product.Category.Provider {
				printMachineTypeDetails(&t, p)
			} else {
				printMachineTypeDetails(&t, p)
			}
		}
	}
	t.Print()
	return nil
}
