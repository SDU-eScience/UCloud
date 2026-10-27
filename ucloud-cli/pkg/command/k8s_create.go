package command

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"sort"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/iapp"
)

type K8sClusterCreateCommand struct {
	Name                string `positional:"name" usage:"Cluster name (omit when --file supplies it)"`
	NameFlag            string `flag:"name" usage:"Override the cluster name when a file is used"`
	File                string `flag:"file" short:"f" usage:"Read the cluster from a YAML file, or - for standard input"`
	Workspace           string `flag:"workspace" usage:"Workspace to use"`
	Provider            string `flag:"provider" usage:"Service provider (required)"`
	K8sVersion          string `flag:"k8s-version" usage:"Kubernetes release (default: the newest supported release)"`
	ControlPlaneMachine string `flag:"control-plane.machine" usage:"Control plane machine type"`
	ControlPlaneNodes   int    `flag:"control-plane.nodes" usage:"Control plane node count, odd from 1 to 7 (default 1)"`
	ControlPlaneDisk    int    `flag:"control-plane.disk" usage:"Control plane disk size in GB, 10 to 1024 (default 50)"`
	DryRun              bool   `flag:"dry-run" usage:"Resolve and validate the request without creating a cluster"`
	Wait                bool   `flag:"wait" usage:"Wait until all nodes are ready"`
	Output              string `flag:"output" usage:"Output format: table or json"`

	controlPlaneNodesSet bool
	controlPlaneDiskSet  bool
	pools                map[int]*k8sCreatePoolFlags
	clusterFlagsSet      bool
}

type k8sCreatePoolFlags struct {
	name     string
	machine  string
	nodes    int
	nodesSet bool
	disk     int
	diskSet  bool
}

type k8sCreateSpec struct {
	Name         string                     `yaml:"name"`
	Provider     string                     `yaml:"provider"`
	K8sVersion   string                     `yaml:"k8sVersion"`
	ControlPlane *k8sCreateControlPlaneSpec `yaml:"controlPlane"`
	WorkerPools  []k8sCreatePoolSpec        `yaml:"workerPools"`
}

type k8sCreateControlPlaneSpec struct {
	Machine string `yaml:"machine"`
	Nodes   *int   `yaml:"nodes"`
	Disk    *int   `yaml:"disk"`
}

type k8sCreatePoolSpec struct {
	Name    string `yaml:"name"`
	Machine string `yaml:"machine"`
	Nodes   *int   `yaml:"nodes"`
	Disk    *int   `yaml:"disk"`
}

func (c *K8sClusterCreateCommand) ParseArguments(args []string) error {
	c.pools = map[int]*k8sCreatePoolFlags{}
	var names []string

	for i := 0; i < len(args); i++ {
		arg := args[i]

		if arg == "-f" {
			value, next, err := k8sCreateFlagValue(args, i, "-f", "", false)
			if err != nil {
				return err
			}
			if value == "" {
				return fmt.Errorf("-f needs a path")
			}
			c.File = value
			i = next
			continue
		}
		if strings.HasPrefix(arg, "-f=") {
			value := strings.TrimPrefix(arg, "-f=")
			if value == "" {
				return fmt.Errorf("-f needs a path")
			}
			c.File = value
			continue
		}

		if strings.HasPrefix(arg, "--") {
			name, inline, hasInline := strings.Cut(arg[2:], "=")
			if name == "" {
				return fmt.Errorf("invalid flag %q", arg)
			}

			if strings.HasPrefix(name, "worker-pools[") {
				index, field, err := k8sCreateParsePoolName(name)
				if err != nil {
					return err
				}

				value, next, err := k8sCreateFlagValue(args, i, "--"+name, inline, hasInline)
				if err != nil {
					return err
				}
				if err := c.setPoolFlag(index, field, value); err != nil {
					return err
				}

				c.clusterFlagsSet = true
				i = next
				continue
			}

			switch name {
			case "file":
				value, next, err := k8sCreateFlagValue(args, i, "--file", inline, hasInline)
				if err != nil {
					return err
				}
				if value == "" {
					return fmt.Errorf("--file needs a path")
				}
				c.File = value
				i = next

			case "name":
				value, next, err := k8sCreateFlagValue(args, i, "--name", inline, hasInline)
				if err != nil {
					return err
				}
				c.NameFlag = value
				i = next

			case "workspace":
				value, next, err := k8sCreateFlagValue(args, i, "--workspace", inline, hasInline)
				if err != nil {
					return err
				}
				c.Workspace = value
				i = next

			case "provider":
				value, next, err := k8sCreateFlagValue(args, i, "--provider", inline, hasInline)
				if err != nil {
					return err
				}
				c.Provider = value
				c.clusterFlagsSet = true
				i = next

			case "k8s-version":
				value, next, err := k8sCreateFlagValue(args, i, "--k8s-version", inline, hasInline)
				if err != nil {
					return err
				}
				c.K8sVersion = value
				c.clusterFlagsSet = true
				i = next

			case "control-plane.machine":
				value, next, err := k8sCreateFlagValue(args, i, "--control-plane.machine", inline, hasInline)
				if err != nil {
					return err
				}
				c.ControlPlaneMachine = value
				c.clusterFlagsSet = true
				i = next

			case "control-plane.nodes":
				value, next, err := k8sCreateFlagValue(args, i, "--control-plane.nodes", inline, hasInline)
				if err != nil {
					return err
				}
				nodes, err := strconv.Atoi(value)
				if err != nil {
					return fmt.Errorf("--control-plane.nodes must be an integer: %q", value)
				}
				c.ControlPlaneNodes = nodes
				c.controlPlaneNodesSet = true
				c.clusterFlagsSet = true
				i = next

			case "control-plane.disk":
				value, next, err := k8sCreateFlagValue(args, i, "--control-plane.disk", inline, hasInline)
				if err != nil {
					return err
				}
				disk, err := strconv.Atoi(value)
				if err != nil {
					return fmt.Errorf("--control-plane.disk must be an integer: %q", value)
				}
				c.ControlPlaneDisk = disk
				c.controlPlaneDiskSet = true
				c.clusterFlagsSet = true
				i = next

			case "output":
				value, next, err := k8sCreateFlagValue(args, i, "--output", inline, hasInline)
				if err != nil {
					return err
				}
				c.Output = value
				i = next

			case "dry-run":
				if hasInline {
					value, err := strconv.ParseBool(inline)
					if err != nil {
						return fmt.Errorf("--dry-run must be true or false: %q", inline)
					}
					c.DryRun = value
				} else {
					c.DryRun = true
				}

			case "wait":
				if hasInline {
					value, err := strconv.ParseBool(inline)
					if err != nil {
						return fmt.Errorf("--wait must be true or false: %q", inline)
					}
					c.Wait = value
				} else {
					c.Wait = true
				}

			default:
				return fmt.Errorf("unknown flag --%s", name)
			}
			continue
		}

		if strings.HasPrefix(arg, "-") {
			return fmt.Errorf("unknown flag %s", arg)
		}
		names = append(names, arg)
	}

	if len(names) > 1 {
		return fmt.Errorf("expected one cluster name, got %d arguments", len(names))
	}
	if len(names) == 1 {
		c.Name = names[0]
	}

	return nil
}

func (c *K8sClusterCreateCommand) HelpNotes() []string {
	return []string{
		"-f is an alias for --file. Use -f - to read from standard input.",
		"--file cannot be combined with cluster flags. --name, --workspace, --dry-run, --wait and --output are allowed.",
		"Set worker pool fields with quoted bracketed flags, for example '--worker-pools[0].name' workers-1.",
		"The worker pool fields are name, machine, nodes and disk.",
		"The service provider is required. Use --provider, or set provider in the file.",
		fmt.Sprintf(
			"The control plane needs an odd number of nodes from %d to %d. Disk sizes are %d to %d GB.",
			k8sapi.ControlPlaneMinNodes,
			k8sapi.ControlPlaneMaxNodes,
			k8sapi.MinDiskGb,
			k8sapi.MaxDiskGb,
		),
		"Use --dry-run with --output json to print the resolved request.",
		"Use --wait to block until the cluster and all nodes are ready.",
	}
}

func (c *K8sClusterCreateCommand) Execute() error {
	if c.DryRun && c.Wait {
		return fmt.Errorf("--wait cannot be combined with --dry-run")
	}

	input, err := c.readInput()
	if err != nil {
		return err
	}

	cfg, err := k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}
	output, err := k8sOutputFormat(c.Output, cfg)
	if err != nil {
		return err
	}

	request, err := k8sResolveCreateRequest(input)
	if err != nil {
		return err
	}
	if err := k8sapi.ValidateDeployRequest(request); err != nil {
		return err
	}

	connectCtx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	client, err := k8sConnectApp(connectCtx, util.OptValue(request.Provider))
	if err != nil {
		return err
	}
	defer client.Close()

	client.SetMessageHandler(func(message string, success bool) {
		if success && message != "" {
			fmt.Fprintln(os.Stderr, message)
		}
	})

	releases, err := iapp.Call(connectCtx, client, k8sapi.Releases, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not list the Kubernetes releases: %w", err)
	}

	request.K8sVersion, err = k8sCreateChooseRelease(request.K8sVersion, releases.Releases)
	if err != nil {
		return err
	}

	if c.DryRun {
		return k8sCreatePrintRequest(request, output)
	}

	response, err := iapp.Call(context.Background(), client, k8sapi.Deploy, request)
	if err != nil {
		return errors.New(k8sCreateFailureMessage(client, err))
	}
	if !response.Ok {
		return errors.New(k8sCreateFailureMessage(client, errors.New("the cluster could not be created")))
	}

	name := response.Name
	if name == "" {
		name = request.Name
	}

	if c.Wait {
		client.Close()

		if err := k8sWaitForCluster(name); err != nil {
			return err
		}
	}

	return k8sCreatePrintResult(name, c.Wait, output)
}

func (c *K8sClusterCreateCommand) readInput() (k8sCreateSpec, error) {
	var input k8sCreateSpec

	if c.File != "" {
		if c.clusterFlagsSet {
			return input, fmt.Errorf(
				"--file cannot be combined with --provider, --k8s-version or control-plane/worker-pool flags; --name, --workspace, --dry-run, --wait and --output are allowed",
			)
		}
		if c.Name != "" {
			return input, fmt.Errorf("use --name to override the cluster name when --file is used")
		}

		parsed, err := k8sCreateReadFile(c.File)
		if err != nil {
			return input, err
		}
		input = parsed
	} else {
		input = k8sCreateSpec{
			Name:       c.Name,
			Provider:   c.Provider,
			K8sVersion: c.K8sVersion,
			ControlPlane: &k8sCreateControlPlaneSpec{
				Machine: c.ControlPlaneMachine,
			},
		}
		if c.controlPlaneNodesSet {
			value := c.ControlPlaneNodes
			input.ControlPlane.Nodes = &value
		}
		if c.controlPlaneDiskSet {
			value := c.ControlPlaneDisk
			input.ControlPlane.Disk = &value
		}

		pools, err := c.workerPools()
		if err != nil {
			return input, err
		}
		input.WorkerPools = pools
	}

	if c.NameFlag != "" {
		if c.File == "" && input.Name != "" && input.Name != c.NameFlag {
			return input, fmt.Errorf("the cluster name is set both positionally and with --name")
		}
		input.Name = c.NameFlag
	}

	input.Name = strings.TrimSpace(input.Name)
	input.Provider = strings.TrimSpace(input.Provider)
	input.K8sVersion = strings.TrimSpace(input.K8sVersion)
	if input.ControlPlane != nil {
		input.ControlPlane.Machine = strings.TrimSpace(input.ControlPlane.Machine)
	}
	for i := range input.WorkerPools {
		input.WorkerPools[i].Name = strings.TrimSpace(input.WorkerPools[i].Name)
		input.WorkerPools[i].Machine = strings.TrimSpace(input.WorkerPools[i].Machine)
	}

	if input.Name == "" {
		return input, fmt.Errorf("a cluster name is required")
	}
	return input, nil
}

func (c *K8sClusterCreateCommand) workerPools() ([]k8sCreatePoolSpec, error) {
	if len(c.pools) == 0 {
		return nil, nil
	}

	indices := make([]int, 0, len(c.pools))
	for index := range c.pools {
		indices = append(indices, index)
	}
	sort.Ints(indices)

	for expected, index := range indices {
		if index != expected {
			return nil, fmt.Errorf("worker pool indexes must start at 0 and be contiguous; missing worker-pools[%d]", expected)
		}
	}

	pools := make([]k8sCreatePoolSpec, 0, len(indices))
	for _, index := range indices {
		flags := c.pools[index]
		pool := k8sCreatePoolSpec{
			Name:    flags.name,
			Machine: flags.machine,
		}
		if flags.nodesSet {
			value := flags.nodes
			pool.Nodes = &value
		}
		if flags.diskSet {
			value := flags.disk
			pool.Disk = &value
		}
		pools = append(pools, pool)
	}
	return pools, nil
}

func (c *K8sClusterCreateCommand) setPoolFlag(index int, field string, value string) error {
	flags := c.pools[index]
	if flags == nil {
		flags = &k8sCreatePoolFlags{}
		c.pools[index] = flags
	}

	switch field {
	case "name":
		flags.name = value
	case "machine":
		flags.machine = value
	case "nodes":
		nodes, err := strconv.Atoi(value)
		if err != nil {
			return fmt.Errorf("--worker-pools[%d].nodes must be an integer: %q", index, value)
		}
		flags.nodes = nodes
		flags.nodesSet = true
	case "disk":
		disk, err := strconv.Atoi(value)
		if err != nil {
			return fmt.Errorf("--worker-pools[%d].disk must be an integer: %q", index, value)
		}
		flags.disk = disk
		flags.diskSet = true
	}

	return nil
}

func k8sCreateParsePoolName(name string) (int, string, error) {
	rest, ok := strings.CutPrefix(name, "worker-pools[")
	if !ok {
		return 0, "", fmt.Errorf("invalid worker pool flag --%s", name)
	}

	closeIndex := strings.Index(rest, "]")
	if closeIndex < 0 {
		return 0, "", fmt.Errorf("invalid worker pool flag --%s", name)
	}

	indexText := rest[:closeIndex]
	index, err := strconv.Atoi(indexText)
	if err != nil || index < 0 {
		return 0, "", fmt.Errorf("invalid worker pool index in --%s", name)
	}

	suffix := rest[closeIndex:]
	if !strings.HasPrefix(suffix, "].") {
		return 0, "", fmt.Errorf("invalid worker pool flag --%s", name)
	}

	field := suffix[2:]
	switch field {
	case "name", "machine", "nodes", "disk":
	default:
		return 0, "", fmt.Errorf("unknown worker pool field %q; use name, machine, nodes or disk", field)
	}

	return index, field, nil
}

func k8sCreateFlagValue(args []string, index int, flagName string, inline string, hasInline bool) (string, int, error) {
	if hasInline {
		return inline, index, nil
	}
	if index+1 >= len(args) {
		return "", index, fmt.Errorf("flag %s needs a value", flagName)
	}
	return args[index+1], index + 1, nil
}

func k8sCreateReadFile(path string) (k8sCreateSpec, error) {
	var input k8sCreateSpec

	var reader io.Reader
	if path == "-" {
		reader = os.Stdin
	} else {
		file, err := os.Open(path)
		if err != nil {
			return input, fmt.Errorf("could not open %s: %w", path, err)
		}
		defer file.Close()
		reader = file
	}

	source := path
	if source == "-" {
		source = "standard input"
	}

	decoder := yaml.NewDecoder(reader)
	decoder.KnownFields(true)

	if err := decoder.Decode(&input); err != nil {
		if errors.Is(err, io.EOF) {
			return input, fmt.Errorf("%s is empty", source)
		}
		return input, fmt.Errorf("could not read %s: %w", source, err)
	}

	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return input, fmt.Errorf("%s must contain a single YAML document", source)
		}
		return input, fmt.Errorf("could not read %s: %w", source, err)
	}

	return input, nil
}

func k8sResolveCreateRequest(input k8sCreateSpec) (k8sapi.DeployRequest, error) {
	var request k8sapi.DeployRequest

	provider := strings.TrimSpace(input.Provider)
	if provider == "" {
		return request, fmt.Errorf("a service provider is required; use --provider")
	}

	byName, available, err := k8sProviderProducts(provider)
	if err != nil {
		return request, err
	}

	controlPlane := k8sCreateControlPlaneSpec{}
	if input.ControlPlane != nil {
		controlPlane = *input.ControlPlane
	}

	controlPlaneMachine, err := k8sResolveMachine(byName, available, provider, "the control plane", controlPlane.Machine)
	if err != nil {
		return request, err
	}

	request = k8sapi.DeployRequest{
		Name:       input.Name,
		Provider:   provider,
		K8sVersion: input.K8sVersion,
		ControlPlane: k8sapi.ControlPlaneSpec{
			Machine: controlPlaneMachine,
			Nodes:   k8sCreateIntOr(controlPlane.Nodes, k8sapi.DefaultControlPlaneNodes),
			Disk:    k8sCreateIntOr(controlPlane.Disk, k8sapi.DefaultControlPlaneDiskGb),
		},
		WorkerPools: []k8sapi.PoolSpec{},
	}

	for i, pool := range input.WorkerPools {
		role := fmt.Sprintf("worker pool %d", i+1)
		if strings.TrimSpace(pool.Name) != "" {
			role = "worker pool " + strings.TrimSpace(pool.Name)
		}

		machine, err := k8sResolveMachine(byName, available, provider, role, pool.Machine)
		if err != nil {
			return request, err
		}

		request.WorkerPools = append(request.WorkerPools, k8sapi.PoolSpec{
			Name:    pool.Name,
			Machine: machine,
			Nodes:   k8sCreateIntOr(pool.Nodes, k8sapi.DefaultWorkerNodes),
			Disk:    k8sCreateIntOr(pool.Disk, k8sapi.DefaultWorkerDiskGb),
		})
	}

	return request, nil
}

func k8sCreateIntOr(value *int, fallback int) int {
	if value == nil {
		return fallback
	}
	return *value
}

func k8sCreateProductUsable(product orcapi.ResolvedSupport[orcapi.JobSupport], provider string) bool {
	return !product.Product.HiddenInGrantApplications &&
		product.Support.VirtualMachine.Enabled &&
		product.Product.Category.Provider == provider
}

func k8sProviderProducts(provider string) (map[string]accapi.ProductReference, []string, error) {
	products, httpErr := orcapi.JobsRetrieveProducts.Invoke(util.Empty{})
	if httpErr != nil {
		return nil, nil, fmt.Errorf("could not retrieve the compute products: %s", httpErr.Why)
	}

	byName := map[string]accapi.ProductReference{}
	for _, product := range products.ProductsByProvider[provider] {
		if !k8sCreateProductUsable(product, provider) {
			continue
		}
		byName[product.Product.Name] = product.Product.ToReference()
	}
	if len(byName) == 0 {
		return nil, nil, fmt.Errorf("no VM-capable compute products are available for provider %s", provider)
	}

	available := make([]string, 0, len(byName))
	for name := range byName {
		available = append(available, name)
	}
	sort.Strings(available)

	return byName, available, nil
}

func k8sResolveMachine(
	byName map[string]accapi.ProductReference,
	available []string,
	provider string,
	role string,
	name string,
) (accapi.ProductReference, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		return accapi.ProductReference{}, fmt.Errorf("select a machine type for %s", role)
	}

	machine, ok := byName[name]
	if !ok {
		return accapi.ProductReference{}, fmt.Errorf(
			"unknown machine type %q for provider %s; available machine types: %s",
			name,
			provider,
			strings.Join(available, ", "),
		)
	}
	return machine, nil
}

func k8sCreateFailureMessage(client *iapp.Client, err error) string {
	message := k8sErrorMessage(err)
	if message == "the cluster could not be created" {
		if last, ok := client.LastFailure(); ok {
			message = last
		}
	}
	return k8sClusterMessage(message)
}

func k8sWaitForCluster(name string) error {
	ctx, cancel := context.WithTimeout(context.Background(), k8sWaitTimeout)
	defer cancel()

	progress := termio.NewProgressList(os.Stderr, fmt.Sprintf("Waiting for cluster %s", name))
	progress.Start()
	defer progress.Stop()

	stages := k8sJobStagesNew(ctx)
	defer stages.Stop()

	var client *iapp.Client
	defer func() {
		if client != nil {
			client.Close()
		}
	}()

	waitingAnnounced := false
	dashboardAnnounced := false

	for {
		if client == nil {
			stack, err := k8sRetrieveCluster(name)
			if err != nil {
				return err
			}

			k8sWaitSetJobProgress(progress, stack, stages)

			client, err = k8sConnectCluster(ctx, stack)
			if err != nil {
				if !waitingAnnounced {
					progress.Milestone("Waiting for the cluster dashboard to become available")
					waitingAnnounced = true
				}
				if !k8sWaitSleep(ctx) {
					return k8sWaitTimeoutError(name)
				}
				continue
			}

			if !dashboardAnnounced {
				progress.Milestone("The cluster dashboard is available")
				dashboardAnnounced = true
			}
		}

		response, err := iapp.Call(ctx, client, k8sapi.ProvisioningStatus, util.Empty{})
		if err != nil {
			if k8sWaitRpcNotFound(err) {
				return fmt.Errorf(
					"the Kubernetes application does not support live provisioning status; update the application",
				)
			}

			client.Close()
			client = nil
			if !k8sWaitSleep(ctx) {
				return k8sWaitTimeoutError(name)
			}
			continue
		}

		if err := k8sProvisioningFailure(response, name); err != nil {
			return err
		}
		if err := k8sProvisioningNodeFailure(response, name, nil); err != nil {
			return err
		}

		k8sWaitSetProgress(progress, response, stages, nil)

		if response.AllReady {
			progress.Milestone(fmt.Sprintf("Cluster %s is ready", name))
			return nil
		}

		if !k8sWaitSleep(ctx) {
			return k8sWaitTimeoutError(name)
		}
	}
}

func k8sCreateChooseRelease(requested string, releases []k8sapi.Release) (string, error) {
	if len(releases) == 0 {
		return "", fmt.Errorf("the Kubernetes application returned no supported releases")
	}

	requested = strings.TrimSpace(requested)
	if requested == "" {
		for _, release := range releases {
			if release.Default {
				return release.Version, nil
			}
		}
		return releases[0].Version, nil
	}

	versions := make([]string, 0, len(releases))
	for _, release := range releases {
		if release.Version == requested {
			return release.Version, nil
		}
		versions = append(versions, release.Version)
	}

	return "", fmt.Errorf("unknown Kubernetes version %s; supported versions: %s", requested, strings.Join(versions, ", "))
}

func k8sCreatePrintRequest(request k8sapi.DeployRequest, output string) error {
	if output == "json" {
		return k8sPrintJson(request)
	}

	summary := termio.Table{}
	summary.AppendHeader("Field")
	summary.AppendHeader("Value")
	summary.Cell("Cluster")
	summary.Cell("%s", request.Name)
	summary.Cell("Provider")
	summary.Cell("%s", request.Provider)
	summary.Cell("Kubernetes")
	summary.Cell("%s", request.K8sVersion)
	summary.Cell("Control plane")
	summary.Cell("%s (%d node%s, %d GB disk)", request.ControlPlane.Machine.Id, request.ControlPlane.Nodes, k8sCreatePlural(request.ControlPlane.Nodes), request.ControlPlane.Disk)
	summary.Print()

	if len(request.WorkerPools) == 0 {
		fmt.Println("Worker pools: none")
		return nil
	}

	pools := termio.Table{}
	pools.AppendHeader("Worker pool")
	pools.AppendHeader("Machine")
	pools.AppendHeader("Nodes")
	pools.AppendHeader("Disk (GB)")
	for _, pool := range request.WorkerPools {
		pools.Cell("%s", pool.Name)
		pools.Cell("%s", pool.Machine.Id)
		pools.Cell("%d", pool.Nodes)
		pools.Cell("%d", pool.Disk)
	}
	pools.Print()

	return nil
}

func k8sCreatePrintResult(name string, ready bool, output string) error {
	dashboardUrl := k8sDashboardURL(name)

	if output == "json" {
		return k8sPrintJson(struct {
			Name         string `json:"name"`
			DashboardUrl string `json:"dashboardUrl"`
			Ready        bool   `json:"ready"`
		}{
			Name:         name,
			DashboardUrl: dashboardUrl,
			Ready:        ready,
		})
	}

	frame := termio.Frame{}
	frame.Title("Cluster")
	frame.SetNaturalWidth(true)
	frame.SetDimBorders(true)
	frame.AppendField("Name", name)
	frame.AppendField("Dashboard", k8sValueOrDash(dashboardUrl))
	frame.Print()
	return nil
}

func k8sCreatePlural(count int) string {
	if count == 1 {
		return ""
	}
	return "s"
}
