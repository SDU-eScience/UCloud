package command

import (
	"errors"
	"fmt"
	"os"
	"sort"
	"strings"

	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

const k8sTemplatePoolName = "workers-1"

func (c *K8sClusterCreateCommand) executeTemplate() error {
	if err := c.k8sTemplateValidateFlags(); err != nil {
		return err
	}

	name, err := c.k8sTemplateName()
	if err != nil {
		return err
	}

	_, err = k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}

	products, err := k8sTemplateRetrieveProducts()
	if err != nil {
		return err
	}

	wallets, err := k8sTemplateRetrieveWallets()
	if err != nil {
		return err
	}

	byProvider := k8sTemplateAccessibleProducts(products, wallets)
	provider, err := k8sTemplateChooseProvider(byProvider, c.Provider)
	if err != nil {
		return err
	}

	machine, warning, err := k8sTemplateChooseMachine(byProvider[provider], provider)
	if err != nil {
		return err
	}

	if warning != "" {
		fmt.Fprintln(os.Stderr, termio.Warning("! %s", warning).Render(termio.ColorEnabled(os.Stderr)))
	}

	content, err := k8sTemplateGenerate(name, provider, machine.Id)
	if err != nil {
		return err
	}

	return k8sTemplateWrite(content, name, c.Save, c.Stdout, c.Force)
}

func (c *K8sClusterCreateCommand) k8sTemplateValidateFlags() error {
	if c.File != "" {
		return fmt.Errorf("--template cannot be combined with --file")
	}
	if c.DryRun {
		return fmt.Errorf("--template cannot be combined with --dry-run")
	}
	if c.Wait {
		return fmt.Errorf("--template cannot be combined with --wait")
	}
	if c.Save != "" && c.Stdout {
		return fmt.Errorf("--save cannot be combined with --stdout")
	}
	if c.Output != "" {
		return fmt.Errorf("--template cannot be combined with --output")
	}

	clusterFlagsSet := c.K8sVersion != "" ||
		c.ControlPlaneMachine != "" ||
		c.controlPlaneNodesSet ||
		c.controlPlaneDiskSet ||
		len(c.pools) > 0

	if clusterFlagsSet {
		return fmt.Errorf("--template cannot be combined with --k8s-version or control-plane/worker-pool flags")
	}

	return nil
}

func (c *K8sClusterCreateCommand) k8sTemplateName() (string, error) {
	if c.Name != "" && c.NameFlag != "" && c.Name != c.NameFlag {
		return "", fmt.Errorf("the cluster name is set both positionally and with --name")
	}

	name := c.NameFlag
	if name == "" {
		name = c.Name
	}
	name = strings.TrimSpace(name)
	if name == "" {
		name = "k8s-" + util.RandomTokenNoTs(4)
	}

	err := k8sapi.ValidateClusterName(name)
	if err != nil {
		return "", err
	}

	return name, nil
}

func k8sTemplateRetrieveProducts() (orcapi.SupportByProvider[orcapi.JobSupport], error) {
	var result orcapi.SupportByProvider[orcapi.JobSupport]

	products, httpErr := orcapi.JobsRetrieveProducts.Invoke(util.Empty{})
	if httpErr != nil {
		return result, fmt.Errorf("could not retrieve the compute products: %s", httpErr.Why)
	}

	return products, nil
}

func k8sTemplateRetrieveWallets() ([]accapi.WalletV2, error) {
	response, httpErr := accapi.WalletsBrowse.Invoke(accapi.WalletsBrowseRequest{
		FilterType: util.OptValue(accapi.ProductTypeCompute),
	})
	if httpErr != nil {
		return nil, fmt.Errorf("could not retrieve the workspace wallets: %s", httpErr.Why)
	}

	return response.Items, nil
}

func k8sTemplateAccessibleProducts(
	products orcapi.SupportByProvider[orcapi.JobSupport],
	wallets []accapi.WalletV2,
) map[string][]orcapi.ResolvedSupport[orcapi.JobSupport] {
	usableCategories := map[string]bool{}
	for _, wallet := range wallets {
		if wallet.MaxUsable > 0 {
			usableCategories[k8sTemplateCategoryKey(wallet.PaysFor.Provider, wallet.PaysFor.Name)] = true
		}
	}

	result := map[string][]orcapi.ResolvedSupport[orcapi.JobSupport]{}
	for provider, providerProducts := range products.ProductsByProvider {
		for _, product := range providerProducts {
			if !k8sCreateProductUsable(product, provider) {
				continue
			}

			categoryKey := k8sTemplateCategoryKey(product.Product.Category.Provider, product.Product.Category.Name)
			if !product.Product.Category.FreeToUse && !usableCategories[categoryKey] {
				continue
			}

			result[provider] = append(result[provider], product)
		}

		if len(result[provider]) == 0 {
			delete(result, provider)
		}
	}

	return result
}

func k8sTemplateCategoryKey(provider string, category string) string {
	return provider + "/" + category
}

func k8sTemplateChooseProvider(
	byProvider map[string][]orcapi.ResolvedSupport[orcapi.JobSupport],
	requested string,
) (string, error) {
	providers := make([]string, 0, len(byProvider))
	for provider := range byProvider {
		providers = append(providers, provider)
	}
	sort.Strings(providers)

	if len(providers) == 0 {
		return "", fmt.Errorf("no service providers with usable VM products are available for the active workspace")
	}

	requested = strings.TrimSpace(requested)
	if requested != "" {
		if _, ok := byProvider[requested]; !ok {
			return "", fmt.Errorf(
				"service provider %s is not available for the active workspace; available providers:%s",
				termio.QuoteName(requested),
				k8sTemplateProviderList(providers),
			)
		}
		return requested, nil
	}

	if len(providers) == 1 {
		return providers[0], nil
	}

	return "", fmt.Errorf(
		"multiple service providers are available; select one with --provider:%s",
		k8sTemplateProviderList(providers),
	)
}

func k8sTemplateProviderList(providers []string) string {
	builder := strings.Builder{}
	for _, provider := range providers {
		builder.WriteString("\n  - ")
		builder.WriteString(termio.EscapeName(provider))
	}
	return builder.String()
}

func k8sTemplateChooseMachine(
	products []orcapi.ResolvedSupport[orcapi.JobSupport],
	provider string,
) (accapi.ProductReference, string, error) {
	var exact []orcapi.ResolvedSupport[orcapi.JobSupport]
	var candidates []orcapi.ResolvedSupport[orcapi.JobSupport]
	smallestCpu := 0

	for _, product := range products {
		if product.Product.Gpu > 0 {
			continue
		}
		if product.Product.Cpu < 2 {
			continue
		}

		candidates = append(candidates, product)
		if smallestCpu == 0 || product.Product.Cpu < smallestCpu {
			smallestCpu = product.Product.Cpu
		}
		if product.Product.Cpu == 2 {
			exact = append(exact, product)
		}
	}

	if len(candidates) == 0 {
		return accapi.ProductReference{}, "", fmt.Errorf(
			"no VM products with at least 2 vCPU are available for provider %s; available machine types: %s",
			termio.QuoteName(provider),
			k8sTemplateMachineList(products),
		)
	}

	if len(exact) > 0 {
		k8sTemplateSortMachines(exact)
		return exact[0].Product.ToReference(), "", nil
	}

	smallest := make([]orcapi.ResolvedSupport[orcapi.JobSupport], 0, len(candidates))
	for _, product := range candidates {
		if product.Product.Cpu == smallestCpu {
			smallest = append(smallest, product)
		}
	}
	k8sTemplateSortMachines(smallest)

	machine := smallest[0].Product
	warning := fmt.Sprintf(
		"No 2 vCPU machine type is available for provider %s; using %s (%d vCPU).",
		termio.QuoteName(provider),
		termio.QuoteName(machine.Name),
		machine.Cpu,
	)
	return machine.ToReference(), warning, nil
}

func k8sTemplateSortMachines(products []orcapi.ResolvedSupport[orcapi.JobSupport]) {
	sort.SliceStable(products, func(i, j int) bool {
		left := &products[i].Product
		right := &products[j].Product

		if left.Category.FreeToUse != right.Category.FreeToUse {
			return left.Category.FreeToUse
		}

		leftPrice := k8sTemplateMachinePrice(left)
		rightPrice := k8sTemplateMachinePrice(right)
		if leftPrice != rightPrice {
			return leftPrice < rightPrice
		}

		if left.MemoryInGigs != right.MemoryInGigs {
			return left.MemoryInGigs < right.MemoryInGigs
		}

		return left.Name < right.Name
	})
}

func k8sTemplateMachinePrice(product *accapi.ProductV2) float64 {
	fraction := product.Fraction.Normalize()
	return float64(product.Price) * float64(fraction.Numerator) / float64(fraction.Denominator)
}

func k8sTemplateMachineList(products []orcapi.ResolvedSupport[orcapi.JobSupport]) string {
	if len(products) == 0 {
		return "none"
	}

	items := make([]string, 0, len(products))
	for _, product := range products {
		items = append(items, fmt.Sprintf(
			"%s (%d vCPU, %d GB)",
			termio.EscapeName(product.Product.Name),
			product.Product.Cpu,
			product.Product.MemoryInGigs,
		))
	}
	sort.Strings(items)

	return strings.Join(items, ", ")
}

func k8sTemplateGenerate(name string, provider string, machine string) (string, error) {
	nameValue, err := k8sTemplateYamlValue(name)
	if err != nil {
		return "", err
	}

	providerValue, err := k8sTemplateYamlValue(provider)
	if err != nil {
		return "", err
	}

	machineValue, err := k8sTemplateYamlValue(machine)
	if err != nil {
		return "", err
	}

	poolValue, err := k8sTemplateYamlValue(k8sTemplatePoolName)
	if err != nil {
		return "", err
	}

	builder := strings.Builder{}
	builder.WriteString("# A simple Kubernetes cluster with one control-plane node and one worker node.\n")
	builder.WriteString("# All machines come from the selected service provider.\n")
	builder.WriteString("# Create the cluster with: ucloud k8s create --file <file>\n")
	builder.WriteString("\n")
	fmt.Fprintf(&builder, "name: %s\n", nameValue)
	fmt.Fprintf(&builder, "provider: %s\n", providerValue)
	builder.WriteString("# Optional. Defaults to the newest supported release (see 'ucloud k8s releases').\n")
	builder.WriteString("# k8sVersion: <version>\n")
	builder.WriteString("\n")
	builder.WriteString("controlPlane:\n")
	fmt.Fprintf(&builder, "  machine: %s\n", machineValue)
	builder.WriteString("  nodes: 1\n")
	fmt.Fprintf(
		&builder,
		"  # Optional. Defaults to %d GB. Valid range is %d to %d GB.\n",
		k8sapi.DefaultControlPlaneDiskGb,
		k8sapi.MinDiskGb,
		k8sapi.MaxDiskGb,
	)
	fmt.Fprintf(&builder, "  # disk: %d\n", k8sapi.DefaultControlPlaneDiskGb)
	builder.WriteString("\n")
	builder.WriteString("workerPools:\n")
	fmt.Fprintf(&builder, "  - name: %s\n", poolValue)
	fmt.Fprintf(&builder, "    machine: %s\n", machineValue)
	builder.WriteString("    nodes: 1\n")
	fmt.Fprintf(
		&builder,
		"    # Optional. Defaults to %d GB. Valid range is %d to %d GB.\n",
		k8sapi.DefaultWorkerDiskGb,
		k8sapi.MinDiskGb,
		k8sapi.MaxDiskGb,
	)
	fmt.Fprintf(&builder, "    # disk: %d\n", k8sapi.DefaultWorkerDiskGb)

	return builder.String(), nil
}

func k8sTemplateYamlValue(value string) (string, error) {
	encoded, err := yaml.Marshal(value)
	if err != nil {
		return "", err
	}
	return strings.TrimSuffix(string(encoded), "\n"), nil
}

func k8sTemplateWrite(content string, name string, save string, stdout bool, force bool) error {
	save = strings.TrimSpace(save)
	if stdout || save == "-" {
		fmt.Print(content)
		return nil
	}

	path := save
	if path == "" {
		path = name + ".yaml"
	}

	flags := os.O_WRONLY | os.O_CREATE | os.O_TRUNC
	if !force {
		flags = os.O_WRONLY | os.O_CREATE | os.O_EXCL
	}

	file, err := os.OpenFile(path, flags, 0644)
	if err != nil {
		if errors.Is(err, os.ErrExist) {
			return fmt.Errorf("the template file %s already exists; use --force to overwrite it", termio.QuoteName(path))
		}
		return fmt.Errorf("could not create %s: %w", termio.QuoteName(path), err)
	}

	if _, err := file.WriteString(content); err != nil {
		file.Close()
		os.Remove(path)
		return fmt.Errorf("could not write %s: %w", termio.QuoteName(path), err)
	}

	if err := file.Close(); err != nil {
		return fmt.Errorf("could not write %s: %w", termio.QuoteName(path), err)
	}

	fmt.Fprintln(os.Stdout, outputSuccessText(os.Stdout, fmt.Sprintf("Template written to %s", termio.QuoteName(path))))
	return nil
}
