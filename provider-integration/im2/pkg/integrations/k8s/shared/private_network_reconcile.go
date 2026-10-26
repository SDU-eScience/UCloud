package shared

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"net/http"
	"net/netip"
	"runtime/debug"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	nadapi "github.com/k8snetworkplumbingwg/network-attachment-definition-client/pkg/apis/k8s.cni.cncf.io/v1"
	k8score "k8s.io/api/core/v1"
	k8snetwork "k8s.io/api/networking/v1"
	k8sequality "k8s.io/apimachinery/pkg/api/equality"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/intstr"
	"k8s.io/client-go/dynamic"
	kvcore "kubevirt.io/api/core/v1"
	nadclient "kubevirt.io/client-go/networkattachmentdefinitionclient"
	nadtyped "kubevirt.io/client-go/networkattachmentdefinitionclient/typed/k8s.cni.cncf.io/v1"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var (
	privateNetworkVpcGvr = schema.GroupVersionResource{
		Group: "kubeovn.io", Version: "v1", Resource: "vpcs",
	}
	privateNetworkSubnetGvr = schema.GroupVersionResource{
		Group: "kubeovn.io", Version: "v1", Resource: "subnets",
	}
	privateNetworkIpGvr = schema.GroupVersionResource{
		Group: "kubeovn.io", Version: "v1", Resource: "ips",
	}
	privateNetworkIbGvr = schema.GroupVersionResource{
		Group: "mellanox.com", Version: "v1alpha1", Resource: "ipoibnetworks",
	}
)

var privateNetworkDynamicClient dynamic.Interface
var privateNetworkNadClient nadtyped.NetworkAttachmentDefinitionInterface

var privateNetworkVpcTracker *K8sResourceTracker[*unstructured.Unstructured]
var privateNetworkSubnetTracker *K8sResourceTracker[*unstructured.Unstructured]
var privateNetworkIpTracker *K8sResourceTracker[*unstructured.Unstructured]
var privateNetworkNadTracker *K8sResourceTracker[*unstructured.Unstructured]
var privateNetworkIbTracker *K8sResourceTracker[*unstructured.Unstructured]
var privateNetworkVmTracker *K8sResourceTracker[*unstructured.Unstructured]
var privateNetworkVmiTracker *K8sResourceTracker[*unstructured.Unstructured]

var privateNetworkInfinibandReady bool

const privateNetworkManagedBySelector = PrivateNetworkManagedByLabel + "=" + PrivateNetworkManagedBy

const privateNetworkFieldManager = "ucloud.dk/im-private-network"

const privateNetworkReconcileInterval = 5 * time.Second

const privateNetworkRepairInterval = 1 * time.Minute

var privateNetworkLastRepairAttempt = map[string]time.Time{}

var privateNetworkReconcileScheduler = struct {
	sync.Mutex
	running           bool
	pending           bool
	pendingForced     bool
	followUpScheduled bool
	last              time.Time
}{}

type privateNetworkIpRecord struct {
	name      string
	namespace string
	podName   string
	subnet    string
	ip        string
	mac       string
	uid       types.UID
}

type privateNetworkWorkloads struct {
	podsByJobRank      map[string][]*k8score.Pod
	nadsByVmJobRank    map[string]map[string]bool
	nadsByPodJobRank   map[string]map[string]bool
	vmNamesByJobRank   map[string]string
	vmRankWithInstance map[string]bool
	ok                 bool
}

type privateNetworkDesiredObjects struct {
	networkId   string
	networkType orc.NetworkType
	subdomain   string
	subnetName  string
	cidr        string
	gateway     string
	excludeIps  string
	provider    string
	namespace   string
	nadConfig   string
	master      string
}

type privateNetworkEnsuredObjects struct {
	vpc    *privateNetworkKubeOvnVpc
	subnet *privateNetworkKubeOvnSubnet
	nad    *nadapi.NetworkAttachmentDefinition
}

type privateNetworkEnsureMode int

const (
	privateNetworkEnsureCreate privateNetworkEnsureMode = iota
	privateNetworkEnsureRepair
)

func (objects privateNetworkEnsuredObjects) ready(desired privateNetworkDesiredObjects) bool {
	if desired.networkType == orc.NetworkTypeInfiniband {
		return objects.nad != nil
	}

	return objects.vpc != nil && objects.subnet != nil &&
		privateNetworkVpcReady(objects.vpc) && privateNetworkSubnetReady(objects.subnet)
}

type privateNetworkNadConfig struct {
	CniVersion   string `json:"cniVersion"`
	Type         string `json:"type"`
	ServerSocket string `json:"server_socket"`
	Provider     string `json:"provider"`
}

func PrivateNetworkInit() {
	settings := ServiceConfig.Compute.PrivateNetworks
	if !settings.Enabled {
		return
	}

	privateNetworkInfinibandReady = false
	controller.PrivateNetworkConfigureDatabase(settings)

	if err := privateNetworkRequireCrds(); err != nil {
		log.Fatal("Private networks are enabled but required Kubernetes APIs are unavailable: %s", err)
	}

	ibNeeded := settings.Infiniband.Enabled
	if !ibNeeded {
		for _, network := range controller.PrivateNetworkSnapshotNetworks() {
			if network.NetworkType == orc.NetworkTypeInfiniband {
				ibNeeded = true
				break
			}
		}
	}

	ibApiAvailable, ibApiErr := privateNetworkResourceAvailable("mellanox.com/v1alpha1", "ipoibnetworks")
	if settings.Infiniband.Enabled && !ibApiAvailable {
		if ibApiErr != nil {
			log.Fatal("InfiniBand is enabled but the IPoIBNetwork API is unavailable: %s", ibApiErr)
		}
		log.Fatal("InfiniBand is enabled but the IPoIBNetwork CRD is not installed")
	}
	if ibNeeded && !settings.Infiniband.Enabled && !ibApiAvailable {
		if ibApiErr != nil {
			log.Warn("InfiniBand networks exist but the IPoIBNetwork API is unavailable: %s", ibApiErr)
		} else {
			log.Warn("InfiniBand networks exist but the IPoIBNetwork CRD is not installed")
		}
	}

	client, err := dynamic.NewForConfig(K8sConfig)
	if err != nil {
		log.Fatal("Could not create the dynamic Kubernetes client for private networks: %s", err)
	}
	privateNetworkDynamicClient = client

	nadClient, err := nadclient.NewForConfig(K8sConfig)
	if err != nil {
		log.Fatal("Could not create the network attachment client for private networks: %s", err)
	}
	privateNetworkNadClient = nadClient.K8sCniCncfIoV1().NetworkAttachmentDefinitions(ServiceConfig.Compute.Namespace)

	namespace := ServiceConfig.Compute.Namespace
	privateNetworkVpcTracker = NewDynamicResourceTracker(client, privateNetworkVpcGvr, "")
	privateNetworkSubnetTracker = NewDynamicResourceTracker(client, privateNetworkSubnetGvr, "")
	privateNetworkIpTracker = NewDynamicResourceTracker(client, privateNetworkIpGvr, "")
	privateNetworkNadTracker = NewDynamicResourceTracker(client, privateNetworkNadGvr, namespace)

	if ibNeeded && ibApiAvailable {
		privateNetworkIbTracker = NewDynamicResourceTracker(client, privateNetworkIbGvr, "")
	}

	if ServiceConfig.Compute.VirtualMachines.Enabled {
		privateNetworkVmTracker = NewDynamicResourceTracker(client, privateNetworkVmGvr, namespace)
		privateNetworkVmiTracker = NewDynamicResourceTracker(client, privateNetworkVmiGvr, namespace)
	}

	if ibNeeded {
		device, node, deviceErr := privateNetworkResolveInfinibandDevice(settings.Infiniband)
		if deviceErr != nil {
			if settings.Infiniband.Enabled {
				log.Fatal("InfiniBand is enabled but no usable InfiniBand node was found: %s", deviceErr)
			}
			log.Warn("InfiniBand networks exist but no usable InfiniBand node was found: %s", deviceErr)
		} else {
			controller.PrivateNetworkSetInfinibandDevice(device)
			if settings.Infiniband.Enabled {
				privateNetworkInfinibandReady = true
				log.Info("InfiniBand networks are enabled using device %s on node %s", device, node)
			}
		}
	}
}

func privateNetworkResourceAvailable(groupVersion string, resource string) (bool, error) {
	resources, err := K8sClient.Discovery().ServerResourcesForGroupVersion(groupVersion)
	if err != nil {
		return false, err
	}

	for _, item := range resources.APIResources {
		if item.Name == resource {
			return true, nil
		}
	}

	return false, nil
}

func privateNetworkResolveInfinibandDevice(settings config.KubernetesInfiniband) (string, string, error) {
	if settings.RdmaResourceName == "" {
		settings.RdmaResourceName = "nvidia.com/rdma_shared"
	}
	if settings.DeviceLabel == "" {
		settings.DeviceLabel = "ucloud.dk/ibdev"
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	nodes, err := K8sClient.CoreV1().Nodes().List(ctx, k8smeta.ListOptions{})
	if err != nil {
		return "", "", fmt.Errorf("could not list Kubernetes nodes: %w", err)
	}

	deviceNodes := map[string]string{}
	for i := range nodes.Items {
		node := &nodes.Items[i]
		raw, ok := node.Labels[settings.DeviceLabel]
		if !ok {
			continue
		}

		var devices []string
		if err := json.Unmarshal([]byte(raw), &devices); err != nil {
			log.Warn("Ignoring node %s with an invalid %s label: %s", node.Name, settings.DeviceLabel, err)
			continue
		}

		if len(devices) == 0 || strings.TrimSpace(devices[0]) == "" {
			log.Warn("Ignoring node %s with an empty %s label", node.Name, settings.DeviceLabel)
			continue
		}

		quantity, hasResource := node.Status.Allocatable[k8score.ResourceName(settings.RdmaResourceName)]
		if !hasResource || quantity.Value() <= 0 {
			continue
		}

		device := strings.TrimSpace(devices[0])
		if _, found := deviceNodes[device]; found {
			continue
		}

		deviceNodes[device] = node.Name
	}

	if len(deviceNodes) == 0 {
		return "", "", fmt.Errorf(
			"no node advertises the RDMA resource %s together with the label %s",
			settings.RdmaResourceName,
			settings.DeviceLabel,
		)
	}

	if len(deviceNodes) > 1 {
		var names []string
		for device := range deviceNodes {
			names = append(names, device)
		}
		slices.Sort(names)
		return "", "", fmt.Errorf("all nodes must use the same InfiniBand device, found %s", strings.Join(names, ", "))
	}

	for device, node := range deviceNodes {
		return device, node, nil
	}

	return "", "", fmt.Errorf("no usable InfiniBand device was found")
}

var privateNetworkNadGvr = schema.GroupVersionResource{
	Group: "k8s.cni.cncf.io", Version: "v1", Resource: "network-attachment-definitions",
}

var privateNetworkVmGvr = schema.GroupVersionResource{
	Group: "kubevirt.io", Version: "v1", Resource: "virtualmachines",
}

var privateNetworkVmiGvr = schema.GroupVersionResource{
	Group: "kubevirt.io", Version: "v1", Resource: "virtualmachineinstances",
}

func privateNetworkRequireCrds() error {
	requiredKubeOvn := map[string]bool{"vpcs": false, "subnets": false, "ips": false}
	kubeOvnResources, err := K8sClient.Discovery().ServerResourcesForGroupVersion("kubeovn.io/v1")
	if err != nil {
		return fmt.Errorf("could not discover kubeovn.io/v1 resources: %w", err)
	}
	for _, resource := range kubeOvnResources.APIResources {
		if _, wanted := requiredKubeOvn[resource.Name]; wanted {
			requiredKubeOvn[resource.Name] = true
		}
	}

	nadResources, err := K8sClient.Discovery().ServerResourcesForGroupVersion("k8s.cni.cncf.io/v1")
	if err != nil {
		return fmt.Errorf("could not discover k8s.cni.cncf.io/v1 resources: %w", err)
	}

	nadFound := false
	for _, resource := range nadResources.APIResources {
		if resource.Name == "network-attachment-definitions" {
			nadFound = true
		}
	}

	var missing []string
	for _, kind := range []string{"ips", "subnets", "vpcs"} {
		if !requiredKubeOvn[kind] {
			missing = append(missing, "kubeovn.io/v1 "+kind)
		}
	}
	if !nadFound {
		missing = append(missing, "k8s.cni.cncf.io/v1 network-attachment-definitions")
	}
	if len(missing) > 0 {
		return fmt.Errorf("%s", strings.Join(missing, ", "))
	}

	return nil
}

func PrivateNetworkReconcile() {
	if privateNetworkDynamicClient == nil {
		return
	}
	if !controller.PrivateNetworksFeatureEnabled() {
		return
	}

	privateNetworkScheduleReconcile(false)
}

func PrivateNetworkReconcileSoon() {
	if privateNetworkDynamicClient == nil || !controller.PrivateNetworksFeatureEnabled() {
		return
	}

	privateNetworkScheduleReconcile(true)
}

func privateNetworkScheduleReconcile(force bool) {
	state := &privateNetworkReconcileScheduler
	state.Lock()

	if state.running {
		state.pending = true
		if force {
			state.pendingForced = true
		}
		state.Unlock()
		return
	}

	now := time.Now()
	throttled := !state.last.IsZero() && now.Sub(state.last) < privateNetworkReconcileInterval
	if throttled && !force {
		state.Unlock()
		return
	}

	state.running = true
	state.last = now
	state.Unlock()

	go func() {
		defer func() {
			if recovered := recover(); recovered != nil {
				state.Lock()
				state.running = false
				state.pending = false
				state.pendingForced = false
				state.Unlock()
				log.Error("Private network reconcile panicked: %v\n%s", recovered, debug.Stack())
			}
		}()

		privateNetworkRunScheduledReconcile()
	}()
}

func privateNetworkRunScheduledReconcile() {
	state := &privateNetworkReconcileScheduler
	for {
		privateNetworkRunReconcilePass()

		state.Lock()
		state.running = false
		pending := state.pending
		pendingForced := state.pendingForced
		state.pending = false
		state.pendingForced = false

		now := time.Now()
		throttled := !state.last.IsZero() && now.Sub(state.last) < privateNetworkReconcileInterval
		if !pending || (throttled && !pendingForced) {
			state.Unlock()
			return
		}

		state.running = true
		state.last = now
		state.Unlock()
	}
}

func privateNetworkRunReconcilePass() {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()
	started := time.Now()

	networks := controller.PrivateNetworkSnapshotNetworks()

	busy := privateNetworkReconcileNetworks(ctx, networks)
	if busy {
		privateNetworkScheduleFollowUp()
	}

	leases := controller.PrivateNetworkLeasesSnapshot()
	workloads := privateNetworkListWorkloads(ctx)

	networksById := map[string]controller.PrivateNetworkSnapshotNetwork{}
	for i := range networks {
		networksById[networks[i].ResourceId] = networks[i]
	}

	privateNetworkSubnetIpCacheBeginPass()

	orphans := privateNetworkReconcileOrphans(ctx)

	if workloads.ok {
		privateNetworkReconcileLeases(ctx, networksById, workloads, leases)
	}

	privateNetworkRetryReservationNotifications()
	privateNetworkMetricsRefresh(networks, leases, orphans)

	metricPrivateNetworkReconcileDuration.Observe(time.Since(started).Seconds())
}

func privateNetworkScheduleFollowUp() {
	state := &privateNetworkReconcileScheduler
	state.Lock()
	alreadyScheduled := state.followUpScheduled
	state.followUpScheduled = true
	state.Unlock()

	if alreadyScheduled {
		return
	}

	time.AfterFunc(2*time.Second, func() {
		state.Lock()
		state.followUpScheduled = false
		state.Unlock()
		PrivateNetworkReconcile()
	})
}

// privateNetworkReconcileNetworks drives every network one step forward. It
// returns true while at least one network still has work pending.
func privateNetworkReconcileNetworks(
	ctx context.Context,
	networks []controller.PrivateNetworkSnapshotNetwork,
) bool {
	busy := false
	for i := range networks {
		network := &networks[i]

		legacy := !network.CidrBlock.Present || network.CidrBlock.Value == ""
		if legacy && network.State != controller.PrivateNetworkStateDeleting {
			continue
		}

		switch network.State {
		case controller.PrivateNetworkStateProvisioning:
			if privateNetworkProvisionAttempt(ctx, network.ResourceId) {
				busy = true
			}

		case controller.PrivateNetworkStateDeleting:
			if privateNetworkDeleteAttempt(network.ResourceId) {
				busy = true
			}

		case controller.PrivateNetworkStateReady:
			if privateNetworkRepairDue(network.ResourceId) {
				privateNetworkRepairAttempt(ctx, network.ResourceId)
			}
		}
	}
	return busy
}

// privateNetworkRepairDue reports whether the periodic repair of a ready
// network is due. The reconcile pass is single-flight, so the plain map needs
// no further locking.
func privateNetworkRepairDue(networkId string) bool {
	last, attempted := privateNetworkLastRepairAttempt[networkId]
	if attempted && time.Since(last) < privateNetworkRepairInterval {
		return false
	}
	privateNetworkLastRepairAttempt[networkId] = time.Now()
	return true
}

func privateNetworkProvisionAttempt(ctx context.Context, networkId string) bool {
	current, ok := controller.PrivateNetworkSnapshotRetrieve(networkId)
	if !ok || current.State != controller.PrivateNetworkStateProvisioning {
		return false
	}

	desired, desiredOk := privateNetworkComputeDesired(current)
	if !desiredOk {
		log.Warn("Private network %s has an invalid CIDR block %s", current.ResourceId, current.CidrBlock.Value)
		return false
	}

	objects, ensureErr := privateNetworkEnsureNetworkObjects(ctx, desired, privateNetworkEnsureCreate)
	if ensureErr != nil {
		log.Warn("Could not create the objects of private network %s: %s", current.ResourceId, ensureErr)
		return false
	}

	if !objects.ready(desired) {
		return true
	}

	current, ok = controller.PrivateNetworkSnapshotRetrieve(networkId)
	if !ok || current.State != controller.PrivateNetworkStateProvisioning {
		return false
	}

	if err := controller.PrivateNetworkUpdateCoreCidrBlock(current.ResourceId, desired.cidr); err != nil {
		log.Warn("Failed to report the CIDR of private network %s to Core: %s", current.ResourceId, err)
		return false
	}

	if err := controller.PrivateNetworkMarkReady(current.ResourceId); err != nil {
		log.Warn("Failed to mark private network %s as ready: %s", current.ResourceId, err)
		return false
	}

	log.Info("Private network %s (%s) is ready with CIDR %s", current.ResourceId, desired.subdomain, desired.cidr)
	return false
}

func privateNetworkRepairAttempt(ctx context.Context, networkId string) {
	current, ok := controller.PrivateNetworkSnapshotRetrieve(networkId)
	if !ok || current.State != controller.PrivateNetworkStateReady {
		return
	}

	desired, desiredOk := privateNetworkComputeDesired(current)
	if !desiredOk {
		return
	}

	current, ok = controller.PrivateNetworkSnapshotRetrieve(networkId)
	if !ok || current.State != controller.PrivateNetworkStateReady {
		return
	}

	if _, err := privateNetworkEnsureNetworkObjects(ctx, desired, privateNetworkEnsureRepair); err != nil {
		log.Warn("Could not repair the objects of private network %s: %s", current.ResourceId, err)
	}
}

// privateNetworkAdvanceNetworkDeletion moves the deletion of the network one
// step forward. It returns true when the network and all its objects are gone.
// Objects which belong to someone else are logged and skipped.
func privateNetworkAdvanceNetworkDeletion(ctx context.Context, desired privateNetworkDesiredObjects) bool {
	if desired.networkType == orc.NetworkTypeInfiniband {
		return privateNetworkAdvanceInfinibandDeletion(ctx, desired)
	}

	subnet, subnetErr := privateNetworkGetSubnetObject(ctx, desired.subnetName)
	if subnetErr != nil {
		return false
	}

	if subnet == nil || privateNetworkObjectOwnedBy(subnet, desired.networkId) {
		ips, ok := privateNetworkListSubnetIps(ctx, desired)
		if !ok || len(ips) > 0 {
			return false
		}
	}

	nad, nadErr := privateNetworkGetNadObject(ctx, desired.subdomain)
	if nadErr != nil {
		return false
	}
	if nad != nil {
		if privateNetworkObjectOwnedBy(nad, desired.networkId) {
			privateNetworkDeleteNadWithUid(ctx, desired.subdomain, nad.UID)
			return false
		} else {
			log.Warn(
				"The network attachment %s collides with private network %s and will not be deleted",
				desired.subdomain,
				desired.networkId,
			)
		}
	}

	if subnet != nil {
		if privateNetworkObjectOwnedBy(subnet, desired.networkId) {
			privateNetworkDeleteWithUid(
				ctx,
				privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr),
				desired.subnetName,
				subnet.GetUID(),
			)
			return false
		} else {
			log.Warn(
				"The subnet %s collides with private network %s and will not be deleted",
				desired.subnetName,
				desired.networkId,
			)
		}
	}

	vpc, vpcErr := privateNetworkGetVpcObject(ctx, desired.subdomain)
	if vpcErr != nil {
		return false
	}
	if vpc != nil {
		if privateNetworkObjectOwnedBy(vpc, desired.networkId) {
			privateNetworkDeleteWithUid(
				ctx,
				privateNetworkDynamicClient.Resource(privateNetworkVpcGvr),
				desired.subdomain,
				vpc.GetUID(),
			)
			return false
		} else {
			log.Warn(
				"The Vpc %s collides with private network %s and will not be deleted",
				desired.subdomain,
				desired.networkId,
			)
		}
	}

	if !privateNetworkDeleteServiceObject(ctx, desired.subdomain, desired.networkId, false) {
		return false
	}

	if !privateNetworkDeletePolicyObject(ctx, desired.subdomain, desired.networkId, false) {
		return false
	}

	if svc, exists := ComputeServices.Retrieve(desired.subdomain); exists &&
		privateNetworkObjectManagedBy(svc, desired.networkId) {
		return false
	}

	if policy, exists := ComputeNetworkPolicies.Retrieve(desired.subdomain); exists &&
		privateNetworkObjectManagedBy(policy, desired.networkId) {
		return false
	}

	return true
}

func privateNetworkAdvanceInfinibandDeletion(ctx context.Context, desired privateNetworkDesiredObjects) bool {
	if privateNetworkIbTracker != nil {
		object, err := privateNetworkGetIbObject(ctx, desired.subdomain)
		if err != nil {
			return false
		}

		if object != nil {
			if !privateNetworkIbOwnedBy(object, desired.networkId) {
				log.Warn(
					"The IPoIBNetwork %s collides with private network %s and will not be deleted",
					desired.subdomain,
					desired.networkId,
				)
				return true
			}

			if !privateNetworkDeleteWithUid(
				ctx,
				privateNetworkDynamicClient.Resource(privateNetworkIbGvr),
				desired.subdomain,
				object.GetUID(),
			) {
				return false
			}
		}
	}

	nad, err := privateNetworkGetNadObject(ctx, desired.subdomain)
	if err != nil {
		return false
	}

	return nad == nil
}

func privateNetworkDeleteAttempt(networkId string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), time.Minute)
	defer cancel()

	current, ok := controller.PrivateNetworkSnapshotRetrieve(networkId)
	if !ok || current.State != controller.PrivateNetworkStateDeleting {
		return false
	}

	desired, desiredOk := privateNetworkComputeDesired(current)
	if !desiredOk {
		if current.CidrBlock.Present && current.CidrBlock.Value != "" {
			log.Warn("Private network %s has an invalid CIDR block %s", current.ResourceId, current.CidrBlock.Value)
			return false
		}
		privateNetworkDeleteLegacyNetwork(ctx, current)
		return false
	}

	if !privateNetworkAdvanceNetworkDeletion(ctx, desired) {
		return true
	}

	workspace, ok := controller.PrivateNetworkWorkspaceParse(current.WorkspaceId)
	if !ok {
		log.Warn("Private network %s has an invalid workspace reference %s", current.ResourceId, current.WorkspaceId)
		return false
	}

	if err := controller.PrivateNetworkFinishDelete(current.ResourceId, workspace); err != nil {
		log.Warn("Failed to finish the deletion of private network %s: %s", current.ResourceId, err)
		return false
	}

	log.Info("Private network %s (%s) has been deleted", current.ResourceId, desired.subdomain)
	return false
}

func privateNetworkComputeDesired(network controller.PrivateNetworkSnapshotNetwork) (privateNetworkDesiredObjects, bool) {
	result := privateNetworkDesiredObjects{}
	if !network.CidrBlock.Present || network.CidrBlock.Value == "" {
		return result, false
	}

	prefix, err := netip.ParsePrefix(network.CidrBlock.Value)
	if err != nil || !prefix.Addr().Is4() {
		return result, false
	}

	masked := prefix.Masked()
	networkType := network.NetworkType
	if networkType == "" {
		networkType = orc.NetworkTypeEthernet
	}

	if !orc.NetworkTypeIsValid(networkType) {
		return result, false
	}

	result.networkId = network.ResourceId
	result.networkType = networkType
	result.subdomain = network.Subdomain
	result.cidr = masked.String()
	result.namespace = ServiceConfig.Compute.Namespace

	if networkType == orc.NetworkTypeInfiniband {
		if masked.Bits() != orc.PrivateNetworkInfinibandPrefixLength {
			return result, false
		}

		result.master = controller.PrivateNetworkInfinibandDevice()
		return result, true
	}

	if masked.Bits() > 30 {
		return result, false
	}

	addrBytes := masked.Addr().As4()
	base := binary.BigEndian.Uint32(addrBytes[:])
	hostMask := uint32(0xFFFFFFFF) >> uint32(masked.Bits())

	gateway := base + 1
	lastUsable := (base | hostMask) - 1

	result.subnetName = PrivateNetworkSubnetName(network.Subdomain, network.ResourceId)
	result.gateway = privateNetworkIpv4ToString(gateway)
	result.excludeIps = privateNetworkIpv4ToString(gateway) + ".." + privateNetworkIpv4ToString(lastUsable)
	result.provider = PrivateNetworkProviderName(network.Subdomain)

	nadConfig, err := json.Marshal(privateNetworkNadConfig{
		CniVersion:   "0.4.0",
		Type:         "kube-ovn",
		ServerSocket: "/run/openvswitch/kube-ovn-daemon.sock",
		Provider:     result.provider,
	})
	if err != nil {
		return result, false
	}
	result.nadConfig = string(nadConfig)
	return result, true
}

func privateNetworkIpv4ToString(value uint32) string {
	return fmt.Sprintf("%d.%d.%d.%d", byte(value>>24), byte(value>>16), byte(value>>8), byte(value))
}

func privateNetworkEnsureNetworkObjects(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	mode privateNetworkEnsureMode,
) (privateNetworkEnsuredObjects, *util.HttpError) {
	if desired.networkType == orc.NetworkTypeInfiniband {
		return privateNetworkEnsureInfinibandObjects(ctx, desired, mode)
	}

	vpc, err := privateNetworkGetVpcObject(ctx, desired.subdomain)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	subnet, err := privateNetworkGetSubnetObject(ctx, desired.subnetName)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	nad, err := privateNetworkGetNadObject(ctx, desired.subdomain)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	service, err := privateNetworkGetServiceObject(ctx, desired.namespace, desired.subdomain)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	policy, err := privateNetworkGetPolicyObject(ctx, desired.namespace, desired.subdomain)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if err := privateNetworkEnsureVpcObject(ctx, desired, vpc); err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if err := privateNetworkEnsureSubnetObject(ctx, desired, subnet); err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if err := privateNetworkEnsureNadObject(ctx, desired, nad); err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if err := privateNetworkEnsureServiceAndPolicy(ctx, desired, service, policy); err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if vpc == nil || subnet == nil {
		vpc, subnet, err = privateNetworkReadVpcAndSubnet(ctx, desired)
		if err != nil {
			return privateNetworkEnsuredObjects{}, err
		}
	}

	return privateNetworkEnsuredObjects{
		vpc:    vpc,
		subnet: subnet,
		nad:    nad,
	}, nil
}

func privateNetworkEnsureInfinibandObjects(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	mode privateNetworkEnsureMode,
) (privateNetworkEnsuredObjects, *util.HttpError) {
	if privateNetworkIbTracker == nil {
		return privateNetworkEnsuredObjects{}, util.ServerHttpError("The IPoIBNetwork API is unavailable")
	}

	if desired.master == "" {
		return privateNetworkEnsuredObjects{}, util.UserHttpError("No InfiniBand-capable node is configured")
	}

	existing, err := privateNetworkGetIbObject(ctx, desired.subdomain)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if existing != nil && !privateNetworkIbOwnedBy(existing, desired.networkId) {
		return privateNetworkEnsuredObjects{}, util.HttpErr(
			http.StatusConflict,
			"An IPoIBNetwork named %s collides with a private network and will not be modified",
			desired.subdomain,
		)
	}

	nad, err := privateNetworkGetNadObject(ctx, desired.subdomain)
	if err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if mode == privateNetworkEnsureRepair && existing != nil && nad == nil &&
		time.Since(existing.GetCreationTimestamp().Time) >= 2*time.Minute {
		if !privateNetworkDeleteWithUid(
			ctx,
			privateNetworkDynamicClient.Resource(privateNetworkIbGvr),
			desired.subdomain,
			existing.GetUID(),
		) {
			return privateNetworkEnsuredObjects{}, util.ServerHttpError(
				"Failed to delete the IPoIBNetwork named %s",
				desired.subdomain,
			)
		}

		log.Warn(
			"Deleted the IPoIBNetwork named %s because its network attachment is missing. It will be recreated.",
			desired.subdomain,
		)
		return privateNetworkEnsuredObjects{}, nil
	}

	if err := privateNetworkEnsureIbObject(ctx, desired, existing); err != nil {
		return privateNetworkEnsuredObjects{}, err
	}

	if nad == nil {
		nad, err = privateNetworkGetNadObject(ctx, desired.subdomain)
		if err != nil {
			return privateNetworkEnsuredObjects{}, err
		}
	}

	return privateNetworkEnsuredObjects{nad: nad}, nil
}

func privateNetworkGetIbObject(ctx context.Context, name string) (*unstructured.Unstructured, *util.HttpError) {
	if privateNetworkIbTracker == nil {
		return nil, nil
	}

	item, ok := privateNetworkIbTracker.Retrieve(name)
	if !ok {
		return nil, nil
	}
	return item, nil
}

func privateNetworkEnsureIbObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *unstructured.Unstructured,
) *util.HttpError {
	object := privateNetworkIbObject(desired)
	client := privateNetworkDynamicClient.Resource(privateNetworkIbGvr)

	if existing != nil &&
		privateNetworkLabelsEqual(existing.GetLabels(), object.GetLabels()) &&
		privateNetworkIbSpecEqual(existing, object) {
		return nil
	}

	if _, err := client.Apply(ctx, object.GetName(), object, k8smeta.ApplyOptions{
		FieldManager: privateNetworkFieldManager,
		Force:        true,
	}); err != nil {
		return util.HttpErrorFromErr(err)
	}

	return nil
}

func privateNetworkIbSpecEqual(existing *unstructured.Unstructured, desired *unstructured.Unstructured) bool {
	existingSpec, ok := existing.Object["spec"].(map[string]any)
	if !ok {
		return false
	}

	desiredSpec, ok := desired.Object["spec"].(map[string]any)
	if !ok {
		return false
	}

	return k8sequality.Semantic.DeepEqual(existingSpec["networkNamespace"], desiredSpec["networkNamespace"]) &&
		k8sequality.Semantic.DeepEqual(existingSpec["master"], desiredSpec["master"]) &&
		k8sequality.Semantic.DeepEqual(existingSpec["ipam"], desiredSpec["ipam"])
}

func privateNetworkIbObject(desired privateNetworkDesiredObjects) *unstructured.Unstructured {
	object := &unstructured.Unstructured{}
	object.SetAPIVersion("mellanox.com/v1alpha1")
	object.SetKind("IPoIBNetwork")
	object.SetName(desired.subdomain)
	object.SetLabels(privateNetworkIbObjectLabels(desired.networkId))
	object.Object["spec"] = map[string]any{
		"networkNamespace": desired.namespace,
		"master":           desired.master,
		"ipam":             `{"type":"static"}`,
	}
	return object
}

func privateNetworkIbObjectLabels(networkId string) map[string]string {
	return map[string]string{
		PrivateNetworkManagedByLabel: PrivateNetworkIbManagedBy,
		PrivateNetworkIbIdLabel:      networkId,
		PrivateNetworkIbTypeLabel:    string(orc.NetworkTypeInfiniband),
	}
}

func privateNetworkIbOwnedBy(obj k8smeta.Object, networkId string) bool {
	labels := obj.GetLabels()
	return labels[PrivateNetworkManagedByLabel] == PrivateNetworkIbManagedBy &&
		labels[PrivateNetworkIbIdLabel] == networkId
}

func privateNetworkWaitForNad(ctx context.Context, name string) bool {
	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for {
		if nad, err := privateNetworkGetNadObject(ctx, name); err == nil && nad != nil {
			return true
		}

		select {
		case <-ctx.Done():
			return false
		case <-ticker.C:
		}
	}
}

func privateNetworkDeleteInfinibandObjects(ctx context.Context, desired privateNetworkDesiredObjects) {
	if privateNetworkIbTracker == nil || desired.subdomain == "" {
		return
	}

	object, err := privateNetworkGetIbObject(ctx, desired.subdomain)
	if err != nil || object == nil {
		return
	}

	if !privateNetworkIbOwnedBy(object, desired.networkId) {
		return
	}

	privateNetworkDeleteWithUid(
		ctx,
		privateNetworkDynamicClient.Resource(privateNetworkIbGvr),
		desired.subdomain,
		object.GetUID(),
	)
}

func privateNetworkReadVpcAndSubnet(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
) (*privateNetworkKubeOvnVpc, *privateNetworkKubeOvnSubnet, *util.HttpError) {
	vpc, err := privateNetworkGetVpcObject(ctx, desired.subdomain)
	if err != nil {
		return nil, nil, err
	}

	subnet, err := privateNetworkGetSubnetObject(ctx, desired.subnetName)
	if err != nil {
		return nil, nil, err
	}

	return vpc, subnet, nil
}

func privateNetworkGetVpcObject(ctx context.Context, name string) (*privateNetworkKubeOvnVpc, *util.HttpError) {
	item, ok := privateNetworkVpcTracker.Retrieve(name)
	if !ok {
		return nil, nil
	}

	vpc := &privateNetworkKubeOvnVpc{}
	if !privateNetworkKubeOvnFromUnstructured("Vpc", item, vpc) {
		return nil, util.ServerHttpError("Failed to decode the Vpc %s", name)
	}
	return vpc, nil
}

func privateNetworkGetSubnetObject(ctx context.Context, name string) (*privateNetworkKubeOvnSubnet, *util.HttpError) {
	item, ok := privateNetworkSubnetTracker.Retrieve(name)
	if !ok {
		return nil, nil
	}

	subnet := &privateNetworkKubeOvnSubnet{}
	if !privateNetworkKubeOvnFromUnstructured("Subnet", item, subnet) {
		return nil, util.ServerHttpError("Failed to decode the Subnet %s", name)
	}
	return subnet, nil
}

func privateNetworkGetNadObject(ctx context.Context, name string) (*nadapi.NetworkAttachmentDefinition, *util.HttpError) {
	item, ok := privateNetworkNadTracker.Retrieve(name)
	if !ok {
		return nil, nil
	}

	nad := &nadapi.NetworkAttachmentDefinition{}
	if !privateNetworkKubeOvnFromUnstructured("network attachment", item, nad) {
		return nil, util.ServerHttpError("Failed to decode the network attachment %s", name)
	}
	return nad, nil
}

func privateNetworkGetServiceObject(ctx context.Context, namespace string, name string) (*k8score.Service, *util.HttpError) {
	service, ok := ComputeServices.Retrieve(name)
	if !ok {
		return nil, nil
	}
	return service, nil
}

func privateNetworkGetPolicyObject(ctx context.Context, namespace string, name string) (*k8snetwork.NetworkPolicy, *util.HttpError) {
	policy, ok := ComputeNetworkPolicies.Retrieve(name)
	if !ok {
		return nil, nil
	}
	return policy, nil
}

func privateNetworkEnsureVpcObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *privateNetworkKubeOvnVpc,
) *util.HttpError {
	object := privateNetworkVpcObject(desired)
	client := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr)
	if existing == nil {
		if _, err := client.Create(ctx, privateNetworkKubeOvnToUnstructured(object), k8smeta.CreateOptions{}); err != nil &&
			!k8serrors.IsAlreadyExists(err) {
			return util.HttpErrorFromErr(err)
		}
		return nil
	}

	specDrift := !privateNetworkLabelsEqual(existing.Labels, object.Labels) ||
		!k8sequality.Semantic.DeepEqual(existing.Spec, object.Spec)
	return privateNetworkRepairKubeOvnObject(ctx, "Vpc", object, existing, client, false, specDrift)
}

func privateNetworkVpcObject(desired privateNetworkDesiredObjects) *privateNetworkKubeOvnVpc {
	return &privateNetworkKubeOvnVpc{
		TypeMeta: k8smeta.TypeMeta{
			APIVersion: "kubeovn.io/v1",
			Kind:       "Vpc",
		},
		ObjectMeta: k8smeta.ObjectMeta{
			Name:   desired.subdomain,
			Labels: privateNetworkObjectLabels(desired.networkId),
		},
		Spec: privateNetworkKubeOvnVpcSpec{
			Namespaces:     []string{desired.namespace},
			StaticRoutes:   []*privateNetworkKubeOvnStaticRoute{},
			PolicyRoutes:   []*privateNetworkKubeOvnPolicyRoute{},
			VpcPeerings:    []*privateNetworkKubeOvnVpcPeering{},
			EnableExternal: false,
			EnableBfd:      false,
		},
	}
}

func privateNetworkEnsureSubnetObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *privateNetworkKubeOvnSubnet,
) *util.HttpError {
	object := privateNetworkSubnetObject(desired)
	client := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr)
	if existing == nil {
		if _, err := client.Create(ctx, privateNetworkKubeOvnToUnstructured(object), k8smeta.CreateOptions{}); err != nil &&
			!k8serrors.IsAlreadyExists(err) {
			return util.HttpErrorFromErr(err)
		}
		return nil
	}

	immutableDrift := existing.Spec.Protocol != object.Spec.Protocol ||
		existing.Spec.Vpc != object.Spec.Vpc ||
		existing.Spec.CidrBlock != object.Spec.CidrBlock
	specDrift := !privateNetworkLabelsEqual(existing.Labels, object.Labels) ||
		!k8sequality.Semantic.DeepEqual(existing.Spec, object.Spec)
	return privateNetworkRepairKubeOvnObject(ctx, "Subnet", object, existing, client, immutableDrift, specDrift)
}

func privateNetworkSubnetObject(desired privateNetworkDesiredObjects) *privateNetworkKubeOvnSubnet {
	return &privateNetworkKubeOvnSubnet{
		TypeMeta: k8smeta.TypeMeta{
			APIVersion: "kubeovn.io/v1",
			Kind:       "Subnet",
		},
		ObjectMeta: k8smeta.ObjectMeta{
			Name:   desired.subnetName,
			Labels: privateNetworkObjectLabels(desired.networkId),
		},
		Spec: privateNetworkKubeOvnSubnetSpec{
			Protocol:    "IPv4",
			Vpc:         desired.subdomain,
			CidrBlock:   desired.cidr,
			Gateway:     desired.gateway,
			Provider:    desired.provider,
			NatOutgoing: false,
			EnableDhcp:  false,
			Namespaces:  []string{desired.namespace},
			ExcludeIps:  []string{desired.excludeIps},
		},
	}
}

func privateNetworkEnsureNadObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *nadapi.NetworkAttachmentDefinition,
) *util.HttpError {
	object := privateNetworkNadObject(desired)
	if existing == nil {
		if _, err := privateNetworkNadClient.Create(ctx, object, k8smeta.CreateOptions{}); err != nil &&
			!k8serrors.IsAlreadyExists(err) {
			return util.HttpErrorFromErr(err)
		}
		return nil
	}

	if !privateNetworkObjectOwnedBy(existing, desired.networkId) {
		return util.HttpErr(
			http.StatusConflict,
			"A network attachment named %s collides with a private network and will not be modified",
			object.Name,
		)
	}

	specDrift := !privateNetworkLabelsEqual(existing.Labels, object.Labels) ||
		existing.Spec.Config != object.Spec.Config
	if !specDrift {
		return nil
	}

	data, err := json.Marshal(object)
	if err != nil {
		log.Fatal("Failed to encode a private network attachment: %s", err)
	}
	applyOptions := k8smeta.ApplyOptions{
		FieldManager: privateNetworkFieldManager,
		Force:        true,
	}
	if _, err := privateNetworkNadClient.Patch(
		ctx,
		object.Name,
		types.ApplyPatchType,
		data,
		applyOptions.ToPatchOptions(),
	); err != nil {
		return util.HttpErrorFromErr(err)
	}
	return nil
}

func privateNetworkNadObject(desired privateNetworkDesiredObjects) *nadapi.NetworkAttachmentDefinition {
	return &nadapi.NetworkAttachmentDefinition{
		TypeMeta: k8smeta.TypeMeta{
			APIVersion: "k8s.cni.cncf.io/v1",
			Kind:       "NetworkAttachmentDefinition",
		},
		ObjectMeta: k8smeta.ObjectMeta{
			Name:      desired.subdomain,
			Namespace: desired.namespace,
			Labels:    privateNetworkObjectLabels(desired.networkId),
		},
		Spec: nadapi.NetworkAttachmentDefinitionSpec{
			Config: desired.nadConfig,
		},
	}
}

func privateNetworkRepairKubeOvnObject(
	ctx context.Context,
	kind string,
	desired k8smeta.Object,
	existing k8smeta.Object,
	client dynamic.ResourceInterface,
	immutableDrift bool,
	specDrift bool,
) *util.HttpError {
	name := desired.GetName()
	if !privateNetworkObjectOwnedBy(existing, desired.GetLabels()[PrivateNetworkIdLabel]) {
		return util.HttpErr(
			http.StatusConflict,
			"A %s named %s collides with a private network and will not be modified",
			kind,
			name,
		)
	}

	if immutableDrift {
		uid := existing.GetUID()
		if !privateNetworkDeleteWithUid(ctx, client, name, uid) {
			return util.ServerHttpError("Failed to delete the %s named %s of a private network", kind, name)
		}
		log.Warn(
			"Deleted the %s named %s of a private network because of immutable spec drift. It will be recreated.",
			kind,
			name,
		)
		return nil
	}

	if !specDrift {
		return nil
	}

	object := privateNetworkKubeOvnToUnstructured(desired)
	if _, err := client.Apply(ctx, name, object, k8smeta.ApplyOptions{
		FieldManager: privateNetworkFieldManager,
		Force:        true,
	}); err != nil {
		return util.HttpErrorFromErr(err)
	}
	return nil
}

func privateNetworkLabelsEqual(existing map[string]string, desired map[string]string) bool {
	for key, value := range desired {
		if existing[key] != value {
			return false
		}
	}
	return true
}

func privateNetworkEnsureServiceAndPolicy(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existingService *k8score.Service,
	existingPolicy *k8snetwork.NetworkPolicy,
) *util.HttpError {
	services := K8sClient.CoreV1().Services(desired.namespace)

	var svc *k8score.Service
	if existingService != nil {
		if !privateNetworkObjectManagedBy(existingService, desired.networkId) {
			return util.HttpErr(
				http.StatusConflict,
				"A service named %s collides with private network %s and will not be modified",
				desired.subdomain,
				desired.networkId,
			)
		}
		svc = existingService
	} else {
		created := &k8score.Service{
			ObjectMeta: k8smeta.ObjectMeta{
				Name:   desired.subdomain,
				Labels: privateNetworkObjectLabels(desired.networkId),
			},
			Spec: k8score.ServiceSpec{
				ClusterIP: "None",
				Selector:  PrivateNetworkMemberSelector(desired.networkId),
				Ports: []k8score.ServicePort{
					{
						Name:       "dummy",
						Port:       80,
						TargetPort: intstr.FromInt32(80),
					},
				},
			},
		}

		createdService, err := services.Create(ctx, created, k8smeta.CreateOptions{})
		if err != nil {
			if k8serrors.IsAlreadyExists(err) {
				return nil
			}
			return util.HttpErrorFromErr(err)
		}
		svc = createdService
	}

	memberLabel := PrivateNetworkMembershipLabel(desired.networkId)
	selectorValue, selectorFound := svc.Spec.Selector[memberLabel]
	if !selectorFound || selectorValue != "true" ||
		!privateNetworkObjectManagedBy(svc, desired.networkId) ||
		!privateNetworkLabelsEqual(svc.Labels, privateNetworkObjectLabels(desired.networkId)) {
		updated := svc.DeepCopy()
		updated.Labels = privateNetworkObjectLabels(desired.networkId)
		updated.Spec.Selector = PrivateNetworkMemberSelector(desired.networkId)
		if _, err := services.Update(ctx, updated, k8smeta.UpdateOptions{}); err != nil {
			return util.HttpErrorFromErr(err)
		}
	}

	if existingPolicy != nil {
		if !privateNetworkObjectManagedBy(existingPolicy, desired.networkId) {
			return util.HttpErr(
				http.StatusConflict,
				"A network policy named %s collides with private network %s and will not be modified",
				desired.subdomain,
				desired.networkId,
			)
		}

		if privateNetworkPolicyDrift(existingPolicy, desired.networkId, svc) {
			updated := existingPolicy.DeepCopy()
			updated.Labels = privateNetworkObjectLabels(desired.networkId)
			updated.OwnerReferences = []k8smeta.OwnerReference{privateNetworkServiceOwner(svc)}
			updated.Spec = privateNetworkPolicySpec(desired.networkId)
			if _, err := K8sClient.NetworkingV1().NetworkPolicies(desired.namespace).
				Update(ctx, updated, k8smeta.UpdateOptions{}); err != nil {
				return util.HttpErrorFromErr(err)
			}
		}

		return nil
	}

	policy := &k8snetwork.NetworkPolicy{
		ObjectMeta: k8smeta.ObjectMeta{
			Name:   desired.subdomain,
			Labels: privateNetworkObjectLabels(desired.networkId),
			OwnerReferences: []k8smeta.OwnerReference{
				privateNetworkServiceOwner(svc),
			},
		},
		Spec: privateNetworkPolicySpec(desired.networkId),
	}

	if _, err := K8sClient.NetworkingV1().NetworkPolicies(desired.namespace).
		Create(ctx, policy, k8smeta.CreateOptions{}); err != nil && !k8serrors.IsAlreadyExists(err) {
		return util.HttpErrorFromErr(err)
	}
	return nil
}

func privateNetworkServiceOwner(svc *k8score.Service) k8smeta.OwnerReference {
	return k8smeta.OwnerReference{
		APIVersion: "v1",
		Kind:       "Service",
		Name:       svc.Name,
		UID:        svc.UID,
	}
}

func privateNetworkPolicySpec(networkId string) k8snetwork.NetworkPolicySpec {
	selector := k8smeta.LabelSelector{
		MatchLabels: PrivateNetworkMemberSelector(networkId),
	}

	return k8snetwork.NetworkPolicySpec{
		PodSelector: selector,
		Ingress: []k8snetwork.NetworkPolicyIngressRule{
			{From: []k8snetwork.NetworkPolicyPeer{{PodSelector: &selector}}},
		},
		Egress: []k8snetwork.NetworkPolicyEgressRule{
			{To: []k8snetwork.NetworkPolicyPeer{{PodSelector: &selector}}},
		},
	}
}

func privateNetworkPolicyDrift(policy *k8snetwork.NetworkPolicy, networkId string, svc *k8score.Service) bool {
	memberLabel := PrivateNetworkMembershipLabel(networkId)

	match, matchFound := policy.Spec.PodSelector.MatchLabels[memberLabel]
	if !matchFound || match != "true" {
		return true
	}

	if !privateNetworkLabelsEqual(policy.Labels, privateNetworkObjectLabels(networkId)) {
		return true
	}

	if len(policy.OwnerReferences) != 1 || policy.OwnerReferences[0].UID != svc.UID {
		return true
	}

	return false
}

func privateNetworkObjectLabels(networkId string) map[string]string {
	return map[string]string{
		PrivateNetworkManagedByLabel: PrivateNetworkManagedBy,
		PrivateNetworkIdLabel:        networkId,
	}
}

func privateNetworkObjectOwnedBy(obj k8smeta.Object, networkId string) bool {
	id, found := obj.GetLabels()[PrivateNetworkIdLabel]
	return found && id == networkId
}

func privateNetworkObjectManagedBy(obj k8smeta.Object, networkId string) bool {
	labels := obj.GetLabels()
	return privateNetworkIsManagedByIm(labels) &&
		labels[PrivateNetworkIdLabel] == networkId
}

func privateNetworkVpcReady(vpc *privateNetworkKubeOvnVpc) bool {
	return vpc.Status.Standby
}

func privateNetworkSubnetReady(subnet *privateNetworkKubeOvnSubnet) bool {
	validated := false
	ready := false
	for _, condition := range subnet.Status.Conditions {
		if condition.Type == "Validated" && condition.Status == k8score.ConditionTrue {
			validated = true
		}
		if condition.Type == "Ready" && condition.Status == k8score.ConditionTrue {
			ready = true
		}
	}

	return validated && ready
}

func privateNetworkListSubnetIps(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
) ([]privateNetworkIpRecord, bool) {
	var result []privateNetworkIpRecord
	for _, item := range privateNetworkIpTracker.List() {
		if item.GetLabels()["ovn.kubernetes.io/subnet"] != desired.subnetName {
			continue
		}

		ip := &privateNetworkKubeOvnIp{}
		if !privateNetworkKubeOvnFromUnstructured("IP", item, ip) {
			log.Warn(
				"Failed to decode the Kube-OVN IP %s, it will be ignored",
				item.GetName(),
			)
			continue
		}
		if ip.Spec.Subnet == desired.subnetName {
			result = append(result, privateNetworkIpRecordFromKubeOvn(ip))
		}
	}
	return result, true
}

func privateNetworkDeleteLegacyNetwork(ctx context.Context, network controller.PrivateNetworkSnapshotNetwork) {
	subdomain := network.Subdomain

	if !privateNetworkDeleteServiceObject(ctx, subdomain, network.ResourceId, true) {
		return
	}

	if !privateNetworkDeletePolicyObject(ctx, subdomain, network.ResourceId, true) {
		return
	}

	workspace, ok := controller.PrivateNetworkWorkspaceParse(network.WorkspaceId)
	if !ok {
		log.Warn("Legacy private network %s has an invalid workspace reference %s", network.ResourceId, network.WorkspaceId)
		return
	}

	if err := controller.PrivateNetworkFinishDelete(network.ResourceId, workspace); err != nil {
		log.Warn("Failed to finish the deletion of legacy private network %s: %s", network.ResourceId, err)
		return
	}

	log.Info("Legacy private network %s (%s) has been deleted", network.ResourceId, subdomain)
}

func privateNetworkDeleteOwnedObject(
	kind string,
	networkId string,
	labels map[string]string,
	legacy bool,
	isLegacy func() bool,
	delete func() error,
) bool {
	shouldDelete := privateNetworkIsManagedByIm(labels) &&
		labels[PrivateNetworkIdLabel] == networkId
	if legacy {
		shouldDelete = isLegacy()
	}
	if !shouldDelete {
		return true
	}

	if err := delete(); err != nil && !k8serrors.IsNotFound(err) && !k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the %s of private network %s: %s", kind, networkId, err)
		return false
	}
	return true
}

func privateNetworkDeleteServiceObject(ctx context.Context, subdomain string, networkId string, legacy bool) bool {
	services := K8sClient.CoreV1().Services(ServiceConfig.Compute.Namespace)
	service, err := services.Get(ctx, subdomain, k8smeta.GetOptions{})
	if err != nil {
		if !k8serrors.IsNotFound(err) {
			log.Warn("Failed to read the service of private network %s: %s", networkId, err)
		}
		return k8serrors.IsNotFound(err)
	}

	uid := service.UID
	return privateNetworkDeleteOwnedObject(
		"service",
		networkId,
		service.Labels,
		legacy,
		func() bool { return privateNetworkServiceIsLegacy(service, subdomain) },
		func() error {
			return services.Delete(ctx, subdomain, k8smeta.DeleteOptions{
				Preconditions: &k8smeta.Preconditions{UID: &uid},
			})
		},
	)
}

func privateNetworkDeletePolicyObject(ctx context.Context, subdomain string, networkId string, legacy bool) bool {
	policies := K8sClient.NetworkingV1().NetworkPolicies(ServiceConfig.Compute.Namespace)
	policy, err := policies.Get(ctx, subdomain, k8smeta.GetOptions{})
	if err != nil {
		if !k8serrors.IsNotFound(err) {
			log.Warn("Failed to read the network policy of private network %s: %s", networkId, err)
		}
		return k8serrors.IsNotFound(err)
	}

	uid := policy.UID
	return privateNetworkDeleteOwnedObject(
		"network policy",
		networkId,
		policy.Labels,
		legacy,
		func() bool { return privateNetworkPolicyIsLegacy(policy, subdomain) },
		func() error {
			return policies.Delete(ctx, subdomain, k8smeta.DeleteOptions{
				Preconditions: &k8smeta.Preconditions{UID: &uid},
			})
		},
	)
}

func privateNetworkDeleteWithUid(
	ctx context.Context,
	client dynamic.ResourceInterface,
	name string,
	uid types.UID,
) bool {
	options := k8smeta.DeleteOptions{}
	if uid != "" {
		options.Preconditions = &k8smeta.Preconditions{UID: &uid}
	}

	if err := client.Delete(ctx, name, options); err != nil && !k8serrors.IsNotFound(err) &&
		!k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the object %s of a private network: %s", name, err)
		return false
	}
	return true
}

func privateNetworkDeleteNadWithUid(ctx context.Context, name string, uid types.UID) bool {
	options := k8smeta.DeleteOptions{}
	if uid != "" {
		options.Preconditions = &k8smeta.Preconditions{UID: &uid}
	}

	err := privateNetworkNadClient.Delete(ctx, name, options)
	if err != nil && !k8serrors.IsNotFound(err) && !k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the object %s of a private network: %s", name, err)
		return false
	}
	return true
}

func privateNetworkDeleteServiceWithUid(ctx context.Context, name string, uid types.UID) bool {
	options := k8smeta.DeleteOptions{}
	if uid != "" {
		options.Preconditions = &k8smeta.Preconditions{UID: &uid}
	}

	err := K8sClient.CoreV1().Services(ServiceConfig.Compute.Namespace).Delete(ctx, name, options)
	if err != nil && !k8serrors.IsNotFound(err) && !k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the object %s of a private network: %s", name, err)
		return false
	}
	return true
}

func privateNetworkDeletePolicyWithUid(ctx context.Context, name string, uid types.UID) bool {
	options := k8smeta.DeleteOptions{}
	if uid != "" {
		options.Preconditions = &k8smeta.Preconditions{UID: &uid}
	}

	err := K8sClient.NetworkingV1().NetworkPolicies(ServiceConfig.Compute.Namespace).Delete(ctx, name, options)
	if err != nil && !k8serrors.IsNotFound(err) && !k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the object %s of a private network: %s", name, err)
		return false
	}
	return true
}

func privateNetworkReconcileOrphans(ctx context.Context) int {
	deleted := 0

	deleteOrphan := func(name string, id string, deleteObject func() bool) {
		if id == "" {
			log.Warn("The managed private network object %s has no network id label and will not be deleted", name)
			return
		}

		if _, tracked := controller.PrivateNetworkSnapshotRetrieve(id); tracked {
			return
		}

		if deleteObject() {
			deleted++
			log.Warn("Deleted the orphaned private network object %s (network %s is not tracked)", name, id)
		}
	}

	trackedNetworks := map[string]util.Empty{}
	for _, network := range controller.PrivateNetworkSnapshotNetworks() {
		trackedNetworks[network.ResourceId] = util.Empty{}
	}

	for _, item := range privateNetworkVpcTracker.List() {
		if !privateNetworkIsManagedByIm(item.GetLabels()) {
			continue
		}

		vpc := &privateNetworkKubeOvnVpc{}
		if !privateNetworkKubeOvnFromUnstructured("Vpc", item, vpc) {
			log.Warn("Failed to decode the Vpc %s, it will not be checked for orphan status", item.GetName())
			continue
		}

		name := vpc.Name
		id := vpc.Labels[PrivateNetworkIdLabel]
		uid := vpc.UID
		if _, tracked := trackedNetworks[id]; tracked {
			continue
		}

		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteWithUid(ctx, privateNetworkDynamicClient.Resource(privateNetworkVpcGvr), name, uid)
		})
	}

	for _, item := range privateNetworkSubnetTracker.List() {
		if !privateNetworkIsManagedByIm(item.GetLabels()) {
			continue
		}

		subnet := &privateNetworkKubeOvnSubnet{}
		if !privateNetworkKubeOvnFromUnstructured("Subnet", item, subnet) {
			log.Warn("Failed to decode the Subnet %s, it will not be checked for orphan status", item.GetName())
			continue
		}

		name := subnet.Name
		id := subnet.Labels[PrivateNetworkIdLabel]
		uid := subnet.UID
		if _, tracked := trackedNetworks[id]; tracked {
			continue
		}

		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteWithUid(ctx, privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr), name, uid)
		})
	}

	if privateNetworkIbTracker != nil {
		for _, item := range privateNetworkIbTracker.List() {
			labels := item.GetLabels()
			if labels[PrivateNetworkManagedByLabel] != PrivateNetworkIbManagedBy {
				continue
			}

			name := item.GetName()
			id := labels[PrivateNetworkIbIdLabel]
			uid := item.GetUID()
			if _, tracked := trackedNetworks[id]; tracked {
				continue
			}

			deleteOrphan(name, id, func() bool {
				return privateNetworkDeleteWithUid(ctx, privateNetworkDynamicClient.Resource(privateNetworkIbGvr), name, uid)
			})
		}
	}

	for _, item := range privateNetworkNadTracker.List() {
		labels := item.GetLabels()
		id := ""
		if privateNetworkIsManagedByIm(labels) {
			id = labels[PrivateNetworkIdLabel]
		} else if labels[PrivateNetworkManagedByLabel] == PrivateNetworkIbManagedBy {
			id = labels[PrivateNetworkIbIdLabel]
		} else {
			continue
		}

		name := item.GetName()
		uid := item.GetUID()
		if _, tracked := trackedNetworks[id]; tracked {
			continue
		}

		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteNadWithUid(ctx, name, uid)
		})
	}

	for _, item := range ComputeServices.List() {
		if !privateNetworkIsManagedByIm(item.Labels) {
			continue
		}

		name := item.Name
		id := item.Labels[PrivateNetworkIdLabel]
		uid := item.UID
		if id == "" {
			continue
		}
		if _, tracked := trackedNetworks[id]; tracked {
			continue
		}

		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteServiceWithUid(ctx, name, uid)
		})
	}

	for _, item := range ComputeNetworkPolicies.List() {
		if !privateNetworkIsManagedByIm(item.Labels) {
			continue
		}

		name := item.Name
		id := item.Labels[PrivateNetworkIdLabel]
		uid := item.UID
		if id == "" {
			continue
		}
		if _, tracked := trackedNetworks[id]; tracked {
			continue
		}

		deleteOrphan(name, id, func() bool {
			return privateNetworkDeletePolicyWithUid(ctx, name, uid)
		})
	}

	return deleted
}

func privateNetworkListWorkloads(ctx context.Context) privateNetworkWorkloads {
	result := privateNetworkWorkloads{
		podsByJobRank:      map[string][]*k8score.Pod{},
		nadsByVmJobRank:    map[string]map[string]bool{},
		nadsByPodJobRank:   map[string]map[string]bool{},
		vmNamesByJobRank:   map[string]string{},
		vmRankWithInstance: map[string]bool{},
		ok:                 true,
	}

	for _, pod := range JobPods.List() {
		jobId := pod.Labels["ucloud.dk/jobId"]
		rank := pod.Labels["ucloud.dk/rank"]

		if jobId != "" {
			result.podsByJobRank[jobId+"/"+rank] = append(result.podsByJobRank[jobId+"/"+rank], pod)
		}

		vmName := pod.Labels[VmNameLabel]
		if vmName != "" {
			if vmJobId, vmRank, ok := privateNetworkVmNameToJobAndRank(vmName); ok {
				vmKey := vmJobId + "/" + strconv.Itoa(vmRank)
				result.vmNamesByJobRank[vmKey] = vmName
				if jobId != "" {
					privateNetworkCollectPodNads(&result, pod, vmKey)
				}
			}
		} else if jobId != "" {
			privateNetworkCollectPodNads(&result, pod, jobId+"/"+rank)
		}
	}

	if ServiceConfig.Compute.VirtualMachines.Enabled {
		for _, item := range privateNetworkVmTracker.List() {
			vm := &kvcore.VirtualMachine{}
			if !privateNetworkKubeOvnFromUnstructured("VirtualMachine", item, vm) {
				continue
			}
			if jobId, rank, ok := privateNetworkVmNameToJobAndRank(vm.Name); ok {
				privateNetworkCollectVmNads(&result, vm, jobId+"/"+strconv.Itoa(rank))
			}
		}

		for _, item := range privateNetworkVmiTracker.List() {
			name := item.GetName()
			if jobId, rank, ok := privateNetworkVmNameToJobAndRank(name); ok {
				result.vmNamesByJobRank[jobId+"/"+strconv.Itoa(rank)] = name
				result.vmRankWithInstance[jobId+"/"+strconv.Itoa(rank)] = true
			}
		}
	}

	return result
}

func privateNetworkCollectPodNads(workloads *privateNetworkWorkloads, pod *k8score.Pod, jobKey string) {
	nads := privateNetworkMultusNadNames(pod.Annotations[PrivateNetworkMultusAnnotation])
	if len(nads) == 0 {
		return
	}

	set := workloads.nadsByPodJobRank[jobKey]
	if set == nil {
		set = map[string]bool{}
		workloads.nadsByPodJobRank[jobKey] = set
	}
	for _, name := range nads {
		set[name] = true
	}
}

func privateNetworkCollectVmNads(workloads *privateNetworkWorkloads, vm *kvcore.VirtualMachine, jobKey string) {
	if vm.Spec.Template == nil {
		return
	}

	var set map[string]bool
	for i := range vm.Spec.Template.Spec.Networks {
		network := &vm.Spec.Template.Spec.Networks[i]
		if network.Multus == nil {
			continue
		}

		if set == nil {
			set = map[string]bool{}
			workloads.nadsByVmJobRank[jobKey] = set
		}

		_, name, found := strings.Cut(network.Multus.NetworkName, "/")
		if !found {
			name = network.Multus.NetworkName
		}
		set[name] = true
	}
}

func privateNetworkMultusNadNames(annotation string) []string {
	if annotation == "" {
		return nil
	}

	var entries []struct {
		Name      string `json:"name"`
		Namespace string `json:"namespace"`
	}
	if err := json.Unmarshal([]byte(annotation), &entries); err != nil {
		return nil
	}

	var result []string
	for _, entry := range entries {
		result = append(result, entry.Name)
	}
	return result
}

func privateNetworkVmNameToJobAndRank(name string) (string, int, bool) {
	if !strings.HasPrefix(name, "vm-") {
		return "", 0, false
	}

	tokens := strings.Split(name, "-")
	if len(tokens) != 3 {
		return "", 0, false
	}

	rank, err := strconv.Atoi(tokens[2])
	if err != nil {
		return "", 0, false
	}

	return tokens[1], rank, true
}

func privateNetworkIpRecordBelongsToLease(
	record privateNetworkIpRecord,
	lease controller.PrivateNetworkLeaseRow,
	desired privateNetworkDesiredObjects,
) bool {
	if record.subnet != desired.subnetName || record.namespace != desired.namespace {
		return false
	}

	if lease.MacAddress.Present {
		vmName := "vm-" + lease.JobId + "-" + strconv.Itoa(lease.Rank)
		return record.podName == vmName || strings.HasPrefix(record.podName, "virt-launcher-"+vmName+"-")
	}

	return record.podName == privateNetworkExpectedPodName(lease)
}

func privateNetworkIpMatchesLease(
	record privateNetworkIpRecord,
	lease controller.PrivateNetworkLeaseRow,
	desired privateNetworkDesiredObjects,
) bool {
	if !privateNetworkIpRecordBelongsToLease(record, lease, desired) {
		return false
	}

	if lease.MacAddress.Present && record.mac != lease.MacAddress.Value {
		return false
	}

	return record.name == record.podName+"."+desired.namespace+"."+desired.provider
}

func privateNetworkExpectedPodName(lease controller.PrivateNetworkLeaseRow) string {
	if lease.MacAddress.Present {
		return "vm-" + lease.JobId + "-" + strconv.Itoa(lease.Rank)
	}
	return fmt.Sprintf("j-%s-job-%d", lease.JobId, lease.Rank)
}

func privateNetworkReconcileLeases(
	ctx context.Context,
	networksById map[string]controller.PrivateNetworkSnapshotNetwork,
	workloads privateNetworkWorkloads,
	leases []controller.PrivateNetworkLeaseRow,
) {
	jobReleaseDecided := map[string]bool{}

	for _, lease := range leases {
		network, ok := networksById[lease.NetworkId]
		if !ok {
			log.Warn("The lease %s of private network %s has no tracked network", lease.Ip, lease.NetworkId)
			continue
		}

		desired, ok := privateNetworkComputeDesired(network)
		if !ok {
			continue
		}

		jobKey := lease.JobId + "/" + strconv.Itoa(lease.Rank)

		if lease.State == controller.PrivateNetworkLeaseStateReleasing {
			privateNetworkFinishReleaseIfGone(ctx, desired, workloads, lease, jobKey)
			continue
		}

		release, decided := jobReleaseDecided[lease.JobId]
		if !decided {
			release = privateNetworkShouldReleaseJob(lease.JobId)
			jobReleaseDecided[lease.JobId] = release
		}

		if !release {
			continue
		}

		controller.PrivateNetworkLeasesMarkReleasing(lease.JobId)
		lease.State = controller.PrivateNetworkLeaseStateReleasing
		privateNetworkFinishReleaseIfGone(ctx, desired, workloads, lease, jobKey)
	}
}

func privateNetworkShouldReleaseJob(jobId string) bool {
	job, ok := controller.JobRetrieve(jobId)
	if ok {
		if job.Status.State == orc.JobStateSuspended {
			return false
		}
		if job.Status.State.IsFinal() {
			return true
		}

		if privateNetworkLocalJobStale(jobId, job) {
			return privateNetworkConfirmReleaseWithCore(jobId)
		}

		return false
	}

	return privateNetworkConfirmReleaseWithCore(jobId)
}

func privateNetworkLocalJobStale(jobId string, job *orc.Job) bool {
	activeJobs := controller.JobRetrieveAll()
	if _, active := activeJobs[jobId]; active {
		return false
	}
	return true
}

func privateNetworkConfirmReleaseWithCore(jobId string) bool {
	request := orc.JobsControlRetrieveRequest{Id: jobId}
	request.IncludeParameters = true
	request.IncludeApplication = true
	request.IncludeProduct = true
	job, err := orc.JobsControlRetrieve.Invoke(request)
	if err != nil {
		if err.StatusCode == http.StatusNotFound {
			log.Info("The job %s of a private network lease no longer exists", jobId)
			return true
		}

		log.Warn("Failed to confirm the state of job %s for lease release: %s", jobId, err)
		return false
	}

	controller.JobTrackNew(job)

	if job.Status.State == orc.JobStateSuspended {
		return false
	}

	return job.Status.State.IsFinal()
}

func privateNetworkFinishReleaseIfGone(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	workloads privateNetworkWorkloads,
	lease controller.PrivateNetworkLeaseRow,
	jobKey string,
) {
	if !privateNetworkLeaseWorkloadGone(ctx, desired, workloads, lease, jobKey) {
		return
	}

	if !privateNetworkDeleteLeaseIpRecords(ctx, desired, lease) {
		return
	}

	if err := controller.PrivateNetworkLeaseFinishRelease(lease.NetworkId, lease.Ip); err != nil {
		log.Warn("Failed to release the lease %s of private network %s: %s", lease.Ip, lease.NetworkId, err)
	}
}

func privateNetworkDeleteLeaseIpRecords(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	lease controller.PrivateNetworkLeaseRow,
) bool {
	records, ok := privateNetworkLeaseIpRecords(ctx, desired, lease)
	if !ok {
		return false
	}

	var blocking []string
	deletedAny := false
	for _, record := range records {
		if privateNetworkIpMatchesLease(record, lease, desired) {
			options := k8smeta.DeleteOptions{}
			if record.uid != "" {
				options.Preconditions = &k8smeta.Preconditions{UID: &record.uid}
			}

			if err := privateNetworkDynamicClient.Resource(privateNetworkIpGvr).
				Delete(ctx, record.name, options); err != nil && !k8serrors.IsNotFound(err) {
				log.Warn(
					"Failed to delete the address %s of private network %s: %s",
					lease.Ip,
					desired.networkId,
					err,
				)
				return false
			}
			deletedAny = true
			continue
		}

		blocking = append(blocking, record.name)
	}

	if deletedAny {
		privateNetworkSubnetIpCacheInvalidate(desired.subnetName)
		remaining, remainingOk := privateNetworkLeaseIpRecords(ctx, desired, lease)
		if !remainingOk || len(remaining) > 0 {
			return false
		}
	}

	if len(blocking) > 0 {
		log.Warn(
			"Private network %s cannot release %s yet: the addresses %v occupy the lease address or port name",
			desired.networkId,
			lease.Ip,
			blocking,
		)
		return false
	}

	return true
}

func privateNetworkLeaseIpRecords(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	lease controller.PrivateNetworkLeaseRow,
) ([]privateNetworkIpRecord, bool) {
	records, ok := privateNetworkSubnetIpSnapshot(ctx, desired)
	if !ok {
		return nil, false
	}

	var result []privateNetworkIpRecord
	for _, record := range records {
		if privateNetworkIpRecordBlocksLease(record, lease, desired) {
			result = append(result, record)
		}
	}

	return result, true
}

var privateNetworkSubnetIpCache = struct {
	sync.Mutex
	pass    int
	subnets map[string][]privateNetworkIpRecord
}{}

var privateNetworkSubnetIpCachePass int

func privateNetworkSubnetIpSnapshot(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
) ([]privateNetworkIpRecord, bool) {
	privateNetworkSubnetIpCache.Lock()
	defer privateNetworkSubnetIpCache.Unlock()

	if privateNetworkSubnetIpCache.pass == privateNetworkSubnetIpCachePass {
		if records, cached := privateNetworkSubnetIpCache.subnets[desired.subnetName]; cached {
			return records, true
		}
	}

	var records []privateNetworkIpRecord
	for _, item := range privateNetworkIpTracker.List() {
		if item.GetLabels()["ovn.kubernetes.io/subnet"] != desired.subnetName {
			continue
		}

		ip := &privateNetworkKubeOvnIp{}
		if !privateNetworkKubeOvnFromUnstructured("IP", item, ip) {
			return nil, false
		}
		records = append(records, privateNetworkIpRecordFromKubeOvn(ip))
	}

	if privateNetworkSubnetIpCache.pass != privateNetworkSubnetIpCachePass {
		privateNetworkSubnetIpCache.pass = privateNetworkSubnetIpCachePass
		privateNetworkSubnetIpCache.subnets = map[string][]privateNetworkIpRecord{}
	}
	privateNetworkSubnetIpCache.subnets[desired.subnetName] = records
	return records, true
}

func privateNetworkSubnetIpCacheBeginPass() {
	privateNetworkSubnetIpCache.Lock()
	privateNetworkSubnetIpCachePass++
	privateNetworkSubnetIpCache.pass = privateNetworkSubnetIpCachePass
	privateNetworkSubnetIpCache.subnets = map[string][]privateNetworkIpRecord{}
	privateNetworkSubnetIpCache.Unlock()
}

func privateNetworkSubnetIpCacheInvalidate(subnetName string) {
	privateNetworkSubnetIpCache.Lock()
	delete(privateNetworkSubnetIpCache.subnets, subnetName)
	privateNetworkSubnetIpCache.Unlock()
}

func privateNetworkIpRecordFromKubeOvn(item *privateNetworkKubeOvnIp) privateNetworkIpRecord {
	return privateNetworkIpRecord{
		name:      item.Name,
		namespace: item.Spec.Namespace,
		podName:   item.Spec.PodName,
		subnet:    item.Spec.Subnet,
		ip:        item.Spec.V4IpAddress,
		mac:       item.Spec.MacAddress,
		uid:       item.UID,
	}
}

func privateNetworkIpRecordBlocksLease(
	record privateNetworkIpRecord,
	lease controller.PrivateNetworkLeaseRow,
	desired privateNetworkDesiredObjects,
) bool {
	if record.subnet == desired.subnetName && record.ip == lease.Ip {
		return true
	}

	return privateNetworkIpRecordBelongsToLease(record, lease, desired)
}

func privateNetworkLeaseWorkloadGone(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	workloads privateNetworkWorkloads,
	lease controller.PrivateNetworkLeaseRow,
	jobKey string,
) bool {
	if lease.MacAddress.Present {
		if nads, hasVm := workloads.nadsByVmJobRank[jobKey]; hasVm && nads[desired.subdomain] {
			return false
		}

		if workloads.vmRankWithInstance[jobKey] {
			return false
		}

		if nads, hasPods := workloads.nadsByPodJobRank[jobKey]; hasPods && nads[desired.subdomain] {
			return false
		}

		if !privateNetworkVmWorkloadFreshAbsent(ctx, desired, lease) {
			return false
		}

		return true
	}

	if nads, hasPods := workloads.nadsByPodJobRank[jobKey]; hasPods && nads[desired.subdomain] {
		return false
	}

	if !privateNetworkPodWorkloadFreshAbsent(ctx, desired, lease) {
		return false
	}

	return true
}

func privateNetworkVmWorkloadFreshAbsent(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	lease controller.PrivateNetworkLeaseRow,
) bool {
	vmName := "vm-" + lease.JobId + "-" + strconv.Itoa(lease.Rank)
	namespace := ServiceConfig.Compute.Namespace

	pods, err := K8sClient.CoreV1().Pods(namespace).List(ctx, k8smeta.ListOptions{
		LabelSelector: VmNameLabel + "=" + vmName,
	})
	if err != nil {
		log.Warn("Failed to list the launcher pods of %s for lease release: %s", vmName, err)
		return false
	}
	if len(pods.Items) > 0 {
		return false
	}

	if !ServiceConfig.Compute.VirtualMachines.Enabled {
		return true
	}

	_, vmiErr := KubevirtClient.VirtualMachineInstance(namespace).
		Get(ctx, vmName, k8smeta.GetOptions{})
	if vmiErr == nil {
		return false
	}
	if !k8serrors.IsNotFound(vmiErr) {
		return false
	}

	vm, vmErr := KubevirtClient.VirtualMachine(namespace).
		Get(ctx, vmName, k8smeta.GetOptions{})
	if vmErr == nil {
		if vm.Spec.Template == nil {
			return false
		}

		for i := range vm.Spec.Template.Spec.Networks {
			network := &vm.Spec.Template.Spec.Networks[i]
			if network.Multus == nil {
				continue
			}

			_, name, found := strings.Cut(network.Multus.NetworkName, "/")
			if !found {
				name = network.Multus.NetworkName
			}
			if name == desired.subdomain {
				return false
			}
		}

		return true
	}
	if !k8serrors.IsNotFound(vmErr) {
		return false
	}

	return true
}

func privateNetworkPodWorkloadFreshAbsent(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	lease controller.PrivateNetworkLeaseRow,
) bool {
	podName := privateNetworkExpectedPodName(lease)
	if podName == "" {
		return true
	}

	_, err := K8sClient.CoreV1().Pods(desired.namespace).
		Get(ctx, podName, k8smeta.GetOptions{})
	if err == nil {
		return false
	}
	if !k8serrors.IsNotFound(err) {
		return false
	}

	return true
}

func privateNetworkRetryReservationNotifications() {
	pending := controller.PrivateNetworkReservationPendingNotificationSnapshot()
	for _, reservation := range pending {
		controller.PrivateNetworkReservationNotifyCore(reservation)
	}
}

func PrivateNetworkCleanupDetachedJob(job *orc.Job) *util.HttpError {
	if job == nil {
		return util.ServerHttpError("Private network cleanup requires a job")
	}

	leases := controller.PrivateNetworkLeasesForJob(job.Id)
	if len(leases) == 0 {
		return nil
	}

	values, err := controller.PrivateNetworkJobValues(job)
	if err != nil {
		return err
	}

	attached := map[string]struct{}{}
	for _, value := range values {
		attached[value.Id] = struct{}{}
	}

	var toRelease []controller.PrivateNetworkLeaseRow
	networksById := map[string]controller.PrivateNetworkSnapshotNetwork{}
	for _, lease := range leases {
		if _, stillAttached := attached[lease.NetworkId]; stillAttached {
			continue
		}

		network, ok := controller.PrivateNetworkRetrieve(lease.NetworkId)
		if !ok {
			continue
		}

		snapshot := controller.PrivateNetworkSnapshotNetwork{
			ResourceId:  network.Id,
			Subdomain:   network.Status.Subdomain,
			CidrBlock:   network.Status.CidrBlock,
			State:       controller.PrivateNetworkStateReady,
			WorkspaceId: "",
			NetworkType: network.Specification.EffectiveType(),
		}
		networksById[lease.NetworkId] = snapshot
		toRelease = append(toRelease, lease)
	}

	if len(toRelease) == 0 {
		return nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Minute)
	defer cancel()
	privateNetworkSubnetIpCacheBeginPass()

	workloads := privateNetworkListWorkloads(ctx)
	if !workloads.ok {
		return util.ServerHttpError("could not list the workloads of the job")
	}

	for _, lease := range toRelease {
		desired, ok := privateNetworkComputeDesired(networksById[lease.NetworkId])
		if !ok {
			continue
		}

		controller.PrivateNetworkLeasesMarkReleasingForJobNetwork(job.Id, lease.NetworkId)

		jobKey := job.Id + "/" + strconv.Itoa(lease.Rank)
		privateNetworkFinishReleaseIfGone(ctx, desired, workloads, lease, jobKey)
	}

	return nil
}
