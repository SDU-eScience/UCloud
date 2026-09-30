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

func printMachineTypeDetails(t *termio.Table, p orcapi.ResolvedSupport[orcapi.JobSupport], verbose bool) {

	queueStatus := p.Support.QueueStatus.GetOrDefault("Unknown")
	usingType := "CPU"
	if p.Product.Gpu > 0 {
		usingType = "GPU"
	}

	t.Cell(usingType)
	t.Cell(p.Product.Name)
	t.Cell(p.Product.Description)
	t.Cell("%v", queueStatus)
	if verbose {
		freeUse := "No"
		if p.Product.Category.FreeToUse {
			freeUse = "Yes"
		}
		t.Cell(p.Product.MemoryModel)
		t.Cell("%v", freeUse)
		t.Cell(p.Product.Category.Provider)
	}
}

func productsHeader(t *termio.Table, verbose bool) {
	t.AppendHeader("Type")
	t.AppendHeader("Machine type")
	t.AppendHeader("Description")
	t.AppendHeader("Status")
	if verbose {
		t.AppendHeader("Memory model")
		t.AppendHeader("FreeToUse")
		t.AppendHeader("Provider")
	}
}

func (c ComputeProductsCommand) Execute() error {
	shared.InitializeUCloudClient()
	products, httpErr := orcapi.JobsRetrieveProducts.Invoke(util.Empty{})
	if httpErr.AsError() != nil {
		return httpErr.AsError()
	}
	t := termio.Table{}
	productsHeader(&t, c.Verbose)
	for _, product := range products.ProductsByProvider {
		for _, p := range product {
			if p.Product.HiddenInGrantApplications {
				continue
			}
			if c.Provider == p.Product.Category.Provider {
				// Show only products from the specified provider
				printMachineTypeDetails(&t, p, c.Verbose)
			} else if c.Provider == "" { // Show all products
				printMachineTypeDetails(&t, p, c.Verbose)
			}
		}
	}
	t.Print()
	return nil
}
