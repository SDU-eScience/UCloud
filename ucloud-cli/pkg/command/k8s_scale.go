package command

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/iapp"
)

type K8sClusterScaleCommand struct {
	Name      string `positional:"name" usage:"Cluster name" required:"true"`
	Pool      string `flag:"pool" usage:"Pool name (required)"`
	Nodes     int    `flag:"nodes" usage:"Target number of nodes in the pool (required)"`
	Disk      int    `flag:"disk" usage:"Disk size in GB for new nodes (default: the pool disk)"`
	Wait      bool   `flag:"wait" usage:"Wait until the new nodes are ready"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type K8sClusterPoolCommand struct {
	Action    string `positional:"action" usage:"Pool action: add" required:"true"`
	Name      string `positional:"cluster" usage:"Cluster name" required:"true"`
	PoolName  string `flag:"name" usage:"Name of the new pool (required)"`
	Machine   string `flag:"machine" usage:"Machine type for the pool (required)"`
	Nodes     int    `flag:"nodes" usage:"Number of nodes in the new pool (default 1)"`
	Disk      int    `flag:"disk" usage:"Disk size in GB for each node (default 50)"`
	Wait      bool   `flag:"wait" usage:"Wait until the new nodes are ready"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`
}

type k8sScaleResult struct {
	Cluster      string `json:"cluster"`
	Pool         string `json:"pool"`
	Nodes        int    `json:"nodes"`
	DashboardUrl string `json:"dashboardUrl"`
	Ready        bool   `json:"ready"`
}

func (c *K8sClusterScaleCommand) HelpNotes() []string {
	return []string{
		"--nodes is the target total for the pool, not the number of nodes to add or remove.",
		"The target must be at least 1. The control plane must stay at an odd count from 1 to 7.",
		"Scaling a pool down drains and removes nodes one at a time and always waits for each removal.",
		"Use node remove for custom drain options.",
		"Use --wait to block until new nodes are ready when scaling up.",
	}
}

func (c *K8sClusterScaleCommand) Execute() error {
	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	poolName := strings.TrimSpace(c.Pool)
	if poolName == "" {
		return fmt.Errorf("--pool is required")
	}
	if c.Nodes < 0 {
		return fmt.Errorf("--nodes is required")
	}
	if c.Nodes < 1 {
		return fmt.Errorf("--nodes must be at least 1")
	}

	cfg, err := k8sInitialize(c.Workspace)
	if err != nil {
		return err
	}
	output, err := k8sOutputFormat(c.Output, cfg)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	stack, err := k8sRetrieveCluster(name)
	if err != nil {
		return err
	}

	client, err := k8sConnectCluster(ctx, stack)
	if err != nil {
		return err
	}
	defer client.Close()

	client.SetMessageHandler(k8sPrintProgressMessage)

	status, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not read the status of %s: %w", name, err)
	}

	pool, ok := k8sFindPool(status, poolName)
	if !ok {
		return k8sUnknownPoolError(status, poolName)
	}

	if err := k8sValidateScaleTarget(pool, c.Nodes); err != nil {
		return err
	}

	disk := c.Disk
	if disk < 0 {
		disk = pool.Disk
	}
	if err := k8sValidateDisk(disk); err != nil {
		return err
	}

	before := k8sPoolHostnames(status.Nodes, pool.Name)

	response, err := iapp.Call(context.Background(), client, k8sapi.ScaleOut, k8sapi.ScaleOutRequest{
		Pool:  pool.Name,
		Nodes: c.Nodes,
		Disk:  disk,
	})
	if err != nil {
		return errors.New(k8sIappFailure(client, err))
	}

	ready := false
	if c.Wait && response.Nodes > pool.Nodes {
		if err := k8sWaitForPoolNodes(client, name, pool.Name, before, response.Nodes); err != nil {
			return err
		}
		ready = true
	}

	return k8sPrintScaleResult(name, pool.Name, response.Nodes, ready, output)
}

func (c *K8sClusterPoolCommand) HelpNotes() []string {
	return []string{
		"The only action is add.",
		"The machine type is resolved from the provider that runs the cluster.",
		"Use --wait to block until the new nodes are ready.",
	}
}

func (c *K8sClusterPoolCommand) Execute() error {
	action := strings.TrimSpace(c.Action)
	if action != "add" {
		return fmt.Errorf("unknown pool action %q; use add", action)
	}

	name := strings.TrimSpace(c.Name)
	if name == "" {
		return fmt.Errorf("a cluster name is required")
	}

	poolName := strings.TrimSpace(c.PoolName)
	if poolName == "" {
		return fmt.Errorf("--name is required")
	}
	if len(poolName) > k8sapi.PoolNameMaxLen {
		return fmt.Errorf("the pool name must be at most %d characters", k8sapi.PoolNameMaxLen)
	}
	if !k8sapi.ValidGroupName(poolName) {
		return fmt.Errorf("the pool name may only contain a-z, 0-9 and dashes: %s", poolName)
	}
	if poolName == k8sapi.GroupControlPlane {
		return fmt.Errorf("the name is reserved for the control plane: %s", poolName)
	}

	nodes := c.Nodes
	if nodes < 0 {
		nodes = k8sapi.DefaultWorkerNodes
	}
	if nodes < 1 {
		return fmt.Errorf("--nodes must be at least 1")
	}
	if nodes > k8sapi.MaxPoolNodes {
		return fmt.Errorf("--nodes must be at most %d", k8sapi.MaxPoolNodes)
	}

	disk := c.Disk
	if disk < 0 {
		disk = k8sapi.DefaultWorkerDiskGb
	}
	if err := k8sValidateDisk(disk); err != nil {
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

	ctx, cancel := context.WithTimeout(context.Background(), k8sConnectTimeout)
	defer cancel()

	stack, err := k8sRetrieveCluster(name)
	if err != nil {
		return err
	}

	client, err := k8sConnectCluster(ctx, stack)
	if err != nil {
		return err
	}
	defer client.Close()

	client.SetMessageHandler(k8sPrintProgressMessage)

	status, err := iapp.Call(ctx, client, k8sapi.Status, util.Empty{})
	if err != nil {
		return fmt.Errorf("could not read the status of %s: %w", name, err)
	}

	if _, exists := k8sFindPool(status, poolName); exists {
		return fmt.Errorf("a pool with this name already exists: %s", poolName)
	}

	provider := strings.TrimSpace(status.MachineProvider)
	if provider == "" {
		return fmt.Errorf("the cluster does not report a machine provider")
	}

	byName, available, err := k8sProviderProducts(provider)
	if err != nil {
		return err
	}
	machine, err := k8sResolveMachine(byName, available, provider, "the new worker pool", c.Machine)
	if err != nil {
		return err
	}

	response, err := iapp.Call(context.Background(), client, k8sapi.AddPool, k8sapi.AddPoolRequest{
		Name:    poolName,
		Machine: machine,
		Nodes:   nodes,
		Disk:    disk,
	})
	if err != nil {
		return errors.New(k8sIappFailure(client, err))
	}

	ready := false
	if c.Wait {
		if err := k8sWaitForPoolNodes(client, name, poolName, map[string]bool{}, response.Nodes); err != nil {
			return err
		}
		ready = true
	}

	return k8sPrintScaleResult(name, response.Name, response.Nodes, ready, output)
}

func k8sFindPool(status k8sapi.StatusResponse, name string) (k8sapi.StatusPool, bool) {
	for _, pool := range status.Pools {
		if pool.Name == name {
			return pool, true
		}
	}
	return k8sapi.StatusPool{}, false
}

func k8sUnknownPoolError(status k8sapi.StatusResponse, name string) error {
	available := make([]string, 0, len(status.Pools))
	for _, pool := range status.Pools {
		available = append(available, pool.Name)
	}
	if len(available) == 0 {
		return fmt.Errorf("the cluster has no node pools")
	}
	return fmt.Errorf("unknown node pool %q; available pools: %s", name, strings.Join(available, ", "))
}

func k8sValidateScaleTarget(pool k8sapi.StatusPool, target int) error {
	if target < 1 {
		return fmt.Errorf("--nodes must be at least 1")
	}

	if pool.ControlPlane {
		switch target {
		case 1, 3, 5, 7:
		default:
			return fmt.Errorf(
				"the control plane must have an odd number of nodes from %d to %d",
				k8sapi.ControlPlaneMinNodes,
				k8sapi.ControlPlaneMaxNodes,
			)
		}
		if target < pool.Nodes && target < 3 {
			return fmt.Errorf("the control plane must keep at least three nodes when it is scaled down")
		}
		return nil
	}

	if target > k8sapi.MaxPoolNodes {
		return fmt.Errorf("the pool supports at most %d nodes", k8sapi.MaxPoolNodes)
	}
	return nil
}

func k8sValidateDisk(disk int) error {
	if disk < k8sapi.MinDiskGb || disk > k8sapi.MaxDiskGb {
		return fmt.Errorf("the disk size must be from %d to %d GB", k8sapi.MinDiskGb, k8sapi.MaxDiskGb)
	}
	return nil
}

func k8sPoolHostnames(nodes []k8sapi.StatusNode, pool string) map[string]bool {
	result := map[string]bool{}
	for _, node := range nodes {
		if node.Group == pool {
			result[node.Hostname] = true
		}
	}
	return result
}

func k8sWaitForPoolNodes(
	client *iapp.Client,
	cluster string,
	pool string,
	before map[string]bool,
	target int,
) error {
	ctx, cancel := context.WithTimeout(context.Background(), k8sWaitTimeout)
	defer cancel()

	client.SetMessageHandler(nil)

	progress := termio.NewProgressList(
		os.Stderr,
		fmt.Sprintf("Waiting for pool %s to reach %d node%s", pool, target, k8sCreatePlural(target)),
	)
	progress.Start()
	defer progress.Stop()

	stages := k8sJobStagesNew(ctx)
	defer stages.Stop()

	include := func(node k8sapi.ProvisioningNodeStatus) bool {
		return node.Group == pool && !before[node.Hostname]
	}

	for {
		response, err := iapp.Call(ctx, client, k8sapi.ProvisioningStatus, util.Empty{})
		if err != nil {
			if k8sWaitRpcNotFound(err) {
				return fmt.Errorf(
					"the Kubernetes application does not support live provisioning status; update the application",
				)
			}
			if !k8sWaitSleep(ctx) {
				return k8sPoolWaitTimeoutError(cluster, pool)
			}
			continue
		}

		if err := k8sProvisioningFailure(response, cluster); err != nil {
			return err
		}
		if err := k8sProvisioningNodeFailure(response, cluster, include); err != nil {
			return err
		}

		k8sWaitSetProgress(progress, response, stages, include)

		poolCount := 0
		newTotal := 0
		newReady := 0
		for _, node := range response.Nodes {
			if node.Group == pool {
				poolCount++
			}
			if !include(node) {
				continue
			}

			newTotal++
			if node.State == k8sapi.NodeStateReady {
				newReady++
			}
		}

		if response.NodeHealthKnown &&
			poolCount == target &&
			newTotal > 0 &&
			newReady == newTotal {
			progress.Summary(fmt.Sprintf("Pool %s is ready", pool))
			return nil
		}

		if !k8sWaitSleep(ctx) {
			return k8sPoolWaitTimeoutError(cluster, pool)
		}
	}
}

func k8sPrintScaleResult(cluster string, pool string, nodes int, ready bool, output string) error {
	dashboardUrl := k8sDashboardURL(cluster)

	if output == "json" {
		return k8sPrintJson(k8sScaleResult{
			Cluster:      cluster,
			Pool:         pool,
			Nodes:        nodes,
			DashboardUrl: dashboardUrl,
			Ready:        ready,
		})
	}

	k8sPrintResultSpacer()

	frame := termio.Frame{}
	frame.Title("Cluster " + cluster)
	if ready {
		frame.AppendFieldStyled("Status", termio.Success("Ready"))
	} else {
		frame.AppendFieldStyled("Status", termio.Subtle("Scaling requested"))
	}
	frame.AppendField("Pool", pool)
	frame.AppendField("Nodes", strconv.Itoa(nodes))
	frame.AppendField("Dashboard", k8sValueOrDash(dashboardUrl))
	frame.Print()
	return nil
}

func k8sIappFailure(client *iapp.Client, err error) string {
	message := k8sErrorMessage(err)
	if last, ok := client.LastFailure(); ok {
		message = last
	}
	return k8sClusterMessage(message)
}
