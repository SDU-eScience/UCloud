package shared

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"net/http"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	nadapi "github.com/k8snetworkplumbingwg/network-attachment-definition-client/pkg/apis/k8s.cni.cncf.io/v1"
	k8score "k8s.io/api/core/v1"
	k8snetwork "k8s.io/api/networking/v1"
	k8sequality "k8s.io/apimachinery/pkg/api/equality"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/intstr"
	"k8s.io/client-go/dynamic"
	kvcore "kubevirt.io/api/core/v1"
	nadclient "kubevirt.io/client-go/networkattachmentdefinitionclient"
	nadtyped "kubevirt.io/client-go/networkattachmentdefinitionclient/typed/k8s.cni.cncf.io/v1"
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
)

var privateNetworkDynamicClient dynamic.Interface
var privateNetworkNadClient nadtyped.NetworkAttachmentDefinitionInterface

const privateNetworkManagedBySelector = PrivateNetworkManagedByLabel + "=" + PrivateNetworkManagedBy

const privateNetworkFieldManager = "ucloud.dk/im-private-network"

const privateNetworkReconcileInterval = 30 * time.Second

var privateNetworkReconcileMutex sync.Mutex
var privateNetworkLastReconcile time.Time
var privateNetworkReconcileRequested atomic.Bool

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

	if err := privateNetworkRequireCrds(); err != nil {
		log.Fatal("Private networks are enabled but required Kubernetes APIs are unavailable: %s", err)
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

	controller.PrivateNetworkConfigureDatabase(settings)
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

	if !privateNetworkReconcileMutex.TryLock() {
		return
	}

	go func() {
		defer privateNetworkReconcileMutex.Unlock()

		requested := privateNetworkReconcileRequested.Swap(false)
		now := time.Now()
		if !requested && !privateNetworkLastReconcile.IsZero() && now.Sub(privateNetworkLastReconcile) < privateNetworkReconcileInterval {
			return
		}
		privateNetworkLastReconcile = now

		privateNetworkRunReconcilePass()
	}()
}

func PrivateNetworkReconcileSoon() {
	if privateNetworkDynamicClient == nil || !controller.PrivateNetworksFeatureEnabled() {
		return
	}

	privateNetworkReconcileRequested.Store(true)
	PrivateNetworkReconcile()
}

type privateNetworkObjectSnapshot struct {
	vpcsByName     map[string]*privateNetworkKubeOvnVpc
	subnetsByName  map[string]*privateNetworkKubeOvnSubnet
	nadsByName     map[string]*nadapi.NetworkAttachmentDefinition
	servicesByName map[string]*k8score.Service
	policiesByName map[string]*k8snetwork.NetworkPolicy
	ips            map[string][]privateNetworkIpRecord
	ok             bool
}

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
	vmisByName         map[string]bool
	vmRankWithInstance map[string]bool
	ok                 bool
}

type privateNetworkDesiredObjects struct {
	networkId  string
	subdomain  string
	subnetName string
	cidr       string
	gateway    string
	excludeIps string
	provider   string
	namespace  string
	nadConfig  string
}

func privateNetworkRunReconcilePass() {
	ctx := context.Background()
	started := time.Now()

	networks := controller.PrivateNetworkSnapshotNetworks()
	objects := privateNetworkListKubeOvnObjects(ctx)
	leases := controller.PrivateNetworkLeasesSnapshot()
	workloads := privateNetworkListWorkloads(ctx)

	networksById := map[string]controller.PrivateNetworkSnapshotNetwork{}
	for i := range networks {
		networksById[networks[i].ResourceId] = networks[i]
	}

	privateNetworkSubnetIpCacheBeginPass()

	privateNetworkReconcileNetworks(ctx, networks, objects)
	orphans := privateNetworkReconcileOrphans(ctx, objects)

	if objects.ok && workloads.ok {
		privateNetworkReconcileLeases(ctx, networksById, workloads, leases)
	}

	privateNetworkRetryReservationNotifications()
	privateNetworkMetricsRefresh(networks, leases, orphans)
	metricPrivateNetworkReconcileDuration.Observe(time.Since(started).Seconds())
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
	addrBytes := masked.Addr().As4()
	base := binary.BigEndian.Uint32(addrBytes[:])
	var hostMask uint32
	if masked.Bits() == 0 {
		hostMask = 0xFFFFFFFF
	} else {
		hostMask = ^(uint32(0) << uint32(32-masked.Bits()))
	}

	gateway := base + 1
	lastUsable := (base | hostMask) - 1

	result.networkId = network.ResourceId
	result.subdomain = network.Subdomain
	result.subnetName = PrivateNetworkSubnetName(network.Subdomain, network.ResourceId)
	result.cidr = masked.String()
	result.gateway = privateNetworkIpv4ToString(gateway)
	result.excludeIps = privateNetworkIpv4ToString(gateway) + ".." + privateNetworkIpv4ToString(lastUsable)
	result.namespace = ServiceConfig.Compute.Namespace
	result.provider = PrivateNetworkProviderName(network.Subdomain)

	config, err := json.Marshal(privateNetworkNadConfig{
		CniVersion:   "0.4.0",
		Type:         "kube-ovn",
		ServerSocket: "/run/openvswitch/kube-ovn-daemon.sock",
		Provider:     result.provider,
	})
	if err != nil {
		return result, false
	}
	result.nadConfig = string(config)
	return result, true
}

func privateNetworkIpv4ToString(value uint32) string {
	return fmt.Sprintf("%d.%d.%d.%d", byte(value>>24), byte(value>>16), byte(value>>8), byte(value))
}

func privateNetworkListKubeOvnObjects(ctx context.Context) privateNetworkObjectSnapshot {
	result := privateNetworkObjectSnapshot{
		vpcsByName:     map[string]*privateNetworkKubeOvnVpc{},
		subnetsByName:  map[string]*privateNetworkKubeOvnSubnet{},
		nadsByName:     map[string]*nadapi.NetworkAttachmentDefinition{},
		servicesByName: map[string]*k8score.Service{},
		policiesByName: map[string]*k8snetwork.NetworkPolicy{},
		ips:            map[string][]privateNetworkIpRecord{},
		ok:             true,
	}

	if privateNetworkDynamicClient == nil {
		result.ok = false
		return result
	}

	vpcs, err := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr).
		List(ctx, k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector})
	if err != nil {
		log.Warn("Failed to list managed Vpcs for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range vpcs.Items {
		vpc := &privateNetworkKubeOvnVpc{}
		if !privateNetworkKubeOvnFromUnstructured("Vpc", &vpcs.Items[i], vpc) {
			result.ok = false
			return result
		}
		result.vpcsByName[vpc.Name] = vpc
	}

	subnets, err := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr).
		List(ctx, k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector})
	if err != nil {
		log.Warn("Failed to list managed Subnets for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range subnets.Items {
		subnet := &privateNetworkKubeOvnSubnet{}
		if !privateNetworkKubeOvnFromUnstructured("Subnet", &subnets.Items[i], subnet) {
			result.ok = false
			return result
		}
		result.subnetsByName[subnet.Name] = subnet
	}

	nads, err := privateNetworkNadClient.List(
		ctx,
		k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector},
	)
	if err != nil {
		log.Warn("Failed to list managed network attachments for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range nads.Items {
		result.nadsByName[nads.Items[i].Name] = &nads.Items[i]
	}

	services, err := K8sClient.CoreV1().Services(ServiceConfig.Compute.Namespace).
		List(ctx, k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector})
	if err != nil {
		log.Warn("Failed to list managed services for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range services.Items {
		result.servicesByName[services.Items[i].Name] = &services.Items[i]
	}

	policies, err := K8sClient.NetworkingV1().NetworkPolicies(ServiceConfig.Compute.Namespace).
		List(ctx, k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector})
	if err != nil {
		log.Warn("Failed to list managed network policies for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range policies.Items {
		result.policiesByName[policies.Items[i].Name] = &policies.Items[i]
	}

	ips, err := privateNetworkDynamicClient.Resource(privateNetworkIpGvr).
		List(ctx, k8smeta.ListOptions{})
	if err != nil {
		log.Warn("Failed to list Kube-OVN IPs for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range ips.Items {
		item := &privateNetworkKubeOvnIp{}
		if !privateNetworkKubeOvnFromUnstructured("IP", &ips.Items[i], item) {
			result.ok = false
			return result
		}
		if item.Spec.Subnet == "" || item.Spec.V4IpAddress == "" {
			continue
		}
		record := privateNetworkIpRecordFromKubeOvn(item)
		result.ips[record.subnet] = append(result.ips[record.subnet], record)
	}

	return result
}

func privateNetworkListWorkloads(ctx context.Context) privateNetworkWorkloads {
	result := privateNetworkWorkloads{
		podsByJobRank:      map[string][]*k8score.Pod{},
		nadsByVmJobRank:    map[string]map[string]bool{},
		nadsByPodJobRank:   map[string]map[string]bool{},
		vmNamesByJobRank:   map[string]string{},
		vmisByName:         map[string]bool{},
		vmRankWithInstance: map[string]bool{},
		ok:                 true,
	}

	pods, err := K8sClient.CoreV1().Pods(ServiceConfig.Compute.Namespace).
		List(ctx, k8smeta.ListOptions{})
	if err != nil {
		log.Warn("Failed to list pods for private network reconciliation: %s", err)
		result.ok = false
		return result
	}

	for i := range pods.Items {
		pod := &pods.Items[i]
		jobId := pod.Labels["ucloud.dk/jobId"]
		rank := pod.Labels["ucloud.dk/rank"]

		if jobId != "" {
			result.podsByJobRank[jobId+"/"+rank] = append(result.podsByJobRank[jobId+"/"+rank], pod)
		}

		vmName := pod.Labels["ucloud.dk/vmName"]
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
		vms, err := KubevirtClient.VirtualMachine(ServiceConfig.Compute.Namespace).
			List(ctx, k8smeta.ListOptions{})
		if err != nil {
			log.Info("Failed to list virtual machines for private network reconciliation: %v", err)
			result.ok = false
		} else {
			for i := range vms.Items {
				vm := &vms.Items[i]
				if jobId, rank, ok := privateNetworkVmNameToJobAndRank(vm.Name); ok {
					privateNetworkCollectVmNads(&result, vm, jobId+"/"+strconv.Itoa(rank))
				}
			}
		}

		vmis, err := KubevirtClient.VirtualMachineInstance(ServiceConfig.Compute.Namespace).
			List(ctx, k8smeta.ListOptions{})
		if err != nil {
			log.Info("Failed to list virtual machine instances for private network reconciliation: %v", err)
			result.ok = false
		} else {
			for i := range vmis.Items {
				result.vmisByName[vmis.Items[i].Name] = true
				if jobId, rank, ok := privateNetworkVmNameToJobAndRank(vmis.Items[i].Name); ok {
					result.vmNamesByJobRank[jobId+"/"+strconv.Itoa(rank)] = vmis.Items[i].Name
					result.vmRankWithInstance[jobId+"/"+strconv.Itoa(rank)] = true
				}
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

func privateNetworkReconcileNetworks(
	ctx context.Context,
	networks []controller.PrivateNetworkSnapshotNetwork,
	objects privateNetworkObjectSnapshot,
) {
	if !objects.ok || len(networks) == 0 {
		return
	}

	for i := range networks {
		network := &networks[i]

		legacy := !network.CidrBlock.Present || network.CidrBlock.Value == ""
		if legacy && network.State != controller.PrivateNetworkStateDeleting {
			continue
		}

		controller.PrivateNetworkReconcileSerialized(network.ResourceId, func() {
			current, ok := controller.PrivateNetworkSnapshotRetrieve(network.ResourceId)
			if !ok || current.State != network.State {
				return
			}

			switch current.State {
			case controller.PrivateNetworkStateProvisioning:
				desired, ok := privateNetworkComputeDesired(current)
				if !ok {
					log.Warn("Private network %s has an invalid CIDR block %s", current.ResourceId, current.CidrBlock.Value)
					return
				}
				privateNetworkProvisionNetwork(ctx, current, desired, objects)

			case controller.PrivateNetworkStateDeleting:
				desired, ok := privateNetworkComputeDesired(current)
				if !ok {
					if current.CidrBlock.Present && current.CidrBlock.Value != "" {
						log.Warn("Private network %s has an invalid CIDR block %s", current.ResourceId, current.CidrBlock.Value)
						return
					}
					privateNetworkDeleteLegacyNetwork(ctx, current)
					return
				}
				privateNetworkDeleteNetwork(ctx, current, desired, objects)

			case controller.PrivateNetworkStateReady:
				desired, ok := privateNetworkComputeDesired(current)
				if !ok {
					return
				}
				privateNetworkRepairNetwork(ctx, desired, objects)
			}
		})
	}
}

func privateNetworkProvisionNetwork(
	ctx context.Context,
	network controller.PrivateNetworkSnapshotNetwork,
	desired privateNetworkDesiredObjects,
	objects privateNetworkObjectSnapshot,
) {
	if err := controller.PrivateNetworkUpdateCoreCidrBlock(desired.networkId, desired.cidr); err != nil {
		log.Warn("Failed to report the CIDR of private network %s to Core: %s", desired.networkId, err)
		return
	}

	vpc := objects.vpcsByName[desired.subdomain]
	subnet := objects.subnetsByName[desired.subnetName]
	nad := objects.nadsByName[desired.subdomain]
	service := objects.servicesByName[desired.subdomain]

	vpcOk, vpcCreated := privateNetworkEnsureVpcObject(ctx, desired, vpc)
	if !vpcOk {
		return
	}

	subnetOk, subnetCreated := privateNetworkEnsureSubnetObject(ctx, desired, subnet)
	if !subnetOk {
		return
	}

	nadOk, nadCreated := privateNetworkEnsureNadObject(ctx, desired, nad)
	if !nadOk {
		return
	}

	serviceOk, _ := privateNetworkEnsureServiceAndPolicy(ctx, desired, service, objects.policiesByName[desired.subdomain])
	if !serviceOk {
		return
	}

	if vpcCreated || subnetCreated || nadCreated {
		return
	}

	if vpc == nil || subnet == nil {
		return
	}

	if !privateNetworkVpcReady(vpc) {
		log.Info(
			"Private network %s is waiting for Kube-OVN VPC %s to become standby",
			desired.networkId,
			desired.subdomain,
		)
		return
	}

	if !privateNetworkSubnetReady(subnet) {
		log.Info(
			"Private network %s is waiting for Kube-OVN subnet %s to become ready",
			desired.networkId,
			desired.subnetName,
		)
		return
	}

	if err := controller.PrivateNetworkMarkReady(desired.networkId, desired.cidr); err != nil {
		log.Warn("Failed to mark private network %s as ready: %s", desired.networkId, err)
		return
	}

	log.Info("Private network %s (%s) is ready with CIDR %s", desired.networkId, desired.subdomain, desired.cidr)
}

func privateNetworkRepairNetwork(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	objects privateNetworkObjectSnapshot,
) {
	vpc := objects.vpcsByName[desired.subdomain]
	subnet := objects.subnetsByName[desired.subnetName]
	nad := objects.nadsByName[desired.subdomain]
	service := objects.servicesByName[desired.subdomain]

	vpcOk, _ := privateNetworkEnsureVpcObject(ctx, desired, vpc)
	subnetOk, _ := privateNetworkEnsureSubnetObject(ctx, desired, subnet)
	nadOk, _ := privateNetworkEnsureNadObject(ctx, desired, nad)
	serviceOk, _ := privateNetworkEnsureServiceAndPolicy(ctx, desired, service, objects.policiesByName[desired.subdomain])

	if !vpcOk || !subnetOk || !nadOk || !serviceOk {
		log.Warn(
			"Could not fully repair the Kubernetes objects of private network %s (%s)",
			desired.networkId,
			desired.subdomain,
		)
	}
}

func privateNetworkDeleteNetwork(
	ctx context.Context,
	network controller.PrivateNetworkSnapshotNetwork,
	desired privateNetworkDesiredObjects,
	objects privateNetworkObjectSnapshot,
) {
	ipsRemaining := len(objects.ips[desired.subnetName]) > 0

	if ipsRemaining {
		return
	}

	subnetClient := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr)
	vpcClient := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr)

	nadExists := false
	if nad := objects.nadsByName[desired.subdomain]; nad != nil {
		nadExists = true
		if privateNetworkObjectOwnedBy(nad, desired.networkId) {
			privateNetworkDeleteNadWithUid(ctx, desired.subdomain, nad.UID)
		} else {
			log.Warn(
				"The network attachment %s collides with private network %s and will not be deleted",
				desired.subdomain,
				desired.networkId,
			)
		}
	}

	subnetExists := false
	if subnet := objects.subnetsByName[desired.subnetName]; subnet != nil {
		subnetExists = true
		if privateNetworkObjectOwnedBy(subnet, desired.networkId) {
			privateNetworkDeleteWithUid(ctx, subnetClient, desired.subnetName, subnet.GetUID())
		} else {
			log.Warn(
				"The subnet %s collides with private network %s and will not be deleted",
				desired.subnetName,
				desired.networkId,
			)
		}
	}

	vpcExists := false
	if !subnetExists {
		if vpc := objects.vpcsByName[desired.subdomain]; vpc != nil {
			vpcExists = true
			if privateNetworkObjectOwnedBy(vpc, desired.networkId) {
				privateNetworkDeleteWithUid(ctx, vpcClient, desired.subdomain, vpc.GetUID())
			} else {
				log.Warn(
					"The Vpc %s collides with private network %s and will not be deleted",
					desired.subdomain,
					desired.networkId,
				)
			}
		}
	}

	if nadExists || subnetExists || vpcExists {
		return
	}

	if !privateNetworkDeleteServiceObject(ctx, desired.subdomain, desired.networkId, false) {
		return
	}

	if !privateNetworkDeletePolicyObject(ctx, desired.subdomain, desired.networkId, false) {
		return
	}

	workspace, ok := controller.PrivateNetworkWorkspaceParse(network.WorkspaceId)
	if !ok {
		log.Warn("Private network %s has an invalid workspace reference %s", desired.networkId, network.WorkspaceId)
		return
	}

	if err := controller.PrivateNetworkFinishDelete(desired.networkId, workspace); err != nil {
		log.Warn("Failed to finish the deletion of private network %s: %s", desired.networkId, err)
		return
	}

	log.Info("Private network %s (%s) has been deleted", desired.networkId, desired.subdomain)
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

func privateNetworkDeleteServiceObject(ctx context.Context, subdomain string, networkId string, legacy bool) bool {
	services := K8sClient.CoreV1().Services(ServiceConfig.Compute.Namespace)
	service, err := services.Get(ctx, subdomain, k8smeta.GetOptions{})
	if err != nil {
		if !k8serrors.IsNotFound(err) {
			log.Warn("Failed to read the service of private network %s: %s", networkId, err)
		}
		return k8serrors.IsNotFound(err)
	}

	shouldDelete := service.Labels[PrivateNetworkManagedByLabel] == PrivateNetworkManagedBy &&
		service.Labels[PrivateNetworkIdLabel] == networkId
	if legacy {
		shouldDelete = privateNetworkServiceIsLegacy(service, subdomain)
	}
	if !shouldDelete {
		return true
	}

	if err := services.Delete(ctx, subdomain, k8smeta.DeleteOptions{
		Preconditions: &k8smeta.Preconditions{UID: &service.UID},
	}); err != nil && !k8serrors.IsNotFound(err) && !k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the service of private network %s: %s", networkId, err)
		return false
	}
	return true
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

	shouldDelete := policy.Labels[PrivateNetworkManagedByLabel] == PrivateNetworkManagedBy &&
		policy.Labels[PrivateNetworkIdLabel] == networkId
	if legacy {
		shouldDelete = privateNetworkPolicyIsLegacy(policy, subdomain)
	}
	if !shouldDelete {
		return true
	}

	if err := policies.Delete(ctx, subdomain, k8smeta.DeleteOptions{
		Preconditions: &k8smeta.Preconditions{UID: &policy.UID},
	}); err != nil && !k8serrors.IsNotFound(err) && !k8serrors.IsConflict(err) {
		log.Warn("Failed to delete the network policy of private network %s: %s", networkId, err)
		return false
	}
	return true
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

func privateNetworkReconcileOrphans(
	ctx context.Context,
	objects privateNetworkObjectSnapshot,
) int {
	if !objects.ok {
		return 0
	}

	deleted := 0

	deleteOrphan := func(name string, id string, deleteObject func() bool) {
		if id == "" {
			log.Warn("The managed private network object %s has no network id label and will not be deleted", name)
			return
		}

		if _, tracked := controller.PrivateNetworkSnapshotRetrieve(id); tracked {
			return
		}

		controller.PrivateNetworkReconcileSerialized(id, func() {
			if _, tracked := controller.PrivateNetworkSnapshotRetrieve(id); tracked {
				return
			}

			if deleteObject() {
				deleted++
				log.Warn("Deleted the orphaned private network object %s (network %s is not tracked)", name, id)
			}
		})
	}

	vpcClient := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr)
	for name, vpc := range objects.vpcsByName {
		id := vpc.Labels[PrivateNetworkIdLabel]
		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteWithUid(ctx, vpcClient, name, vpc.UID)
		})
	}

	subnetClient := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr)
	for name, subnet := range objects.subnetsByName {
		id := subnet.Labels[PrivateNetworkIdLabel]
		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteWithUid(ctx, subnetClient, name, subnet.UID)
		})
	}

	for name, nad := range objects.nadsByName {
		id := nad.Labels[PrivateNetworkIdLabel]
		deleteOrphan(name, id, func() bool {
			return privateNetworkDeleteNadWithUid(ctx, name, nad.UID)
		})
	}

	return deleted
}

func privateNetworkObjectOwnedBy(obj k8smeta.Object, networkId string) bool {
	id, found := obj.GetLabels()[PrivateNetworkIdLabel]
	return found && id == networkId
}

func privateNetworkEnsureVpcObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *privateNetworkKubeOvnVpc,
) (bool, bool) {
	object := privateNetworkVpcObject(desired)
	client := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr)
	if existing == nil {
		return privateNetworkCreateKubeOvnObject(ctx, "Vpc", object, client)
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
) (bool, bool) {
	object := privateNetworkSubnetObject(desired)
	client := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr)
	if existing == nil {
		return privateNetworkCreateKubeOvnObject(ctx, "Subnet", object, client)
	}

	immutableDrift := existing.Spec.Protocol != object.Spec.Protocol ||
		existing.Spec.Vpc != object.Spec.Vpc ||
		existing.Spec.CidrBlock != object.Spec.CidrBlock
	specDrift := !privateNetworkLabelsEqual(existing.Labels, object.Labels) ||
		!k8sequality.Semantic.DeepEqual(existing.Spec, object.Spec)
	return privateNetworkRepairKubeOvnObject(
		ctx,
		"Subnet",
		object,
		existing,
		client,
		immutableDrift,
		specDrift,
	)
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
) (bool, bool) {
	object := privateNetworkNadObject(desired)
	if existing == nil {
		_, err := privateNetworkNadClient.Create(ctx, object, k8smeta.CreateOptions{})
		if err != nil {
			log.Warn("Failed to create the network attachment of private network: %s", err)
			return false, false
		}
		return true, true
	}

	if !privateNetworkObjectOwnedBy(existing, desired.networkId) {
		log.Warn(
			"A network attachment named %s collides with a private network and will not be modified",
			object.Name,
		)
		return false, false
	}

	specDrift := !privateNetworkLabelsEqual(existing.Labels, object.Labels) ||
		existing.Spec.Config != object.Spec.Config
	if !specDrift {
		return true, false
	}

	data, err := json.Marshal(object)
	if err != nil {
		log.Fatal("Failed to encode a private network attachment: %s", err)
	}
	applyOptions := k8smeta.ApplyOptions{
		FieldManager: privateNetworkFieldManager,
		Force:        true,
	}
	_, err = privateNetworkNadClient.Patch(
		ctx,
		object.Name,
		types.ApplyPatchType,
		data,
		applyOptions.ToPatchOptions(),
	)
	if err != nil {
		log.Warn("Failed to repair the network attachment of a private network: %s", err)
		return false, false
	}
	return true, true
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

func privateNetworkCreateKubeOvnObject(
	ctx context.Context,
	kind string,
	desired k8smeta.Object,
	client dynamic.ResourceInterface,
) (bool, bool) {
	object := privateNetworkKubeOvnToUnstructured(desired)
	_, err := client.Create(ctx, object, k8smeta.CreateOptions{})
	if err != nil {
		log.Warn("Failed to create the %s of private network: %s", kind, err)
		return false, false
	}
	return true, true
}

func privateNetworkRepairKubeOvnObject(
	ctx context.Context,
	kind string,
	desired k8smeta.Object,
	existing k8smeta.Object,
	client dynamic.ResourceInterface,
	immutableDrift bool,
	specDrift bool,
) (bool, bool) {
	name := desired.GetName()
	if !privateNetworkObjectOwnedBy(existing, desired.GetLabels()[PrivateNetworkIdLabel]) {
		log.Warn(
			"A %s named %s collides with a private network and will not be modified",
			kind,
			name,
		)
		return false, false
	}

	if immutableDrift {
		uid := existing.GetUID()
		if !privateNetworkDeleteWithUid(ctx, client, name, uid) {
			return false, false
		}
		log.Warn(
			"Deleted the %s named %s of a private network because of immutable spec drift. It will be recreated.",
			kind,
			name,
		)
		return false, true
	}

	if !specDrift {
		return true, false
	}

	object := privateNetworkKubeOvnToUnstructured(desired)
	if _, err := client.Apply(ctx, name, object, k8smeta.ApplyOptions{
		FieldManager: privateNetworkFieldManager,
		Force:        true,
	}); err != nil {
		log.Warn("Failed to repair the %s of a private network: %s", kind, err)
		return false, false
	}
	return true, true
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
) (bool, bool) {
	services := K8sClient.CoreV1().Services(desired.namespace)

	var svc *k8score.Service
	if existingService != nil {
		if existingService.Labels[PrivateNetworkManagedByLabel] != PrivateNetworkManagedBy ||
			existingService.Labels[PrivateNetworkIdLabel] != desired.networkId {
			log.Warn(
				"A service named %s collides with private network %s and will not be modified",
				desired.subdomain,
				desired.networkId,
			)
			return false, false
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
			log.Warn("Failed to create the service of private network %s: %s", desired.networkId, err)
			return false, false
		}
		svc = createdService
	}

	memberLabel := PrivateNetworkMembershipLabel(desired.networkId)
	selectorValue, selectorFound := svc.Spec.Selector[memberLabel]
	if !selectorFound || selectorValue != "true" ||
		svc.Labels[PrivateNetworkManagedByLabel] != PrivateNetworkManagedBy ||
		svc.Labels[PrivateNetworkIdLabel] != desired.networkId {
		updated := svc.DeepCopy()
		updated.Labels = privateNetworkObjectLabels(desired.networkId)
		updated.Spec.Selector = PrivateNetworkMemberSelector(desired.networkId)
		if _, err := services.Update(ctx, updated, k8smeta.UpdateOptions{}); err != nil {
			log.Warn("Failed to repair the service of private network %s: %s", desired.networkId, err)
			return false, false
		}
	}

	if existingPolicy != nil {
		if existingPolicy.Labels[PrivateNetworkManagedByLabel] != PrivateNetworkManagedBy ||
			existingPolicy.Labels[PrivateNetworkIdLabel] != desired.networkId {
			log.Warn(
				"A network policy named %s collides with private network %s and will not be modified",
				desired.subdomain,
				desired.networkId,
			)
			return false, false
		}

		if privateNetworkPolicyDrift(existingPolicy, desired.networkId, svc) {
			updated := existingPolicy.DeepCopy()
			updated.Labels = privateNetworkObjectLabels(desired.networkId)
			updated.OwnerReferences = []k8smeta.OwnerReference{privateNetworkServiceOwner(svc)}
			updated.Spec = privateNetworkPolicySpec(desired.networkId)
			if _, err := K8sClient.NetworkingV1().NetworkPolicies(desired.namespace).
				Update(ctx, updated, k8smeta.UpdateOptions{}); err != nil {
				log.Warn("Failed to repair the network policy of private network %s: %s", desired.networkId, err)
				return false, false
			}
		}

		return true, false
	}

	selector := k8smeta.LabelSelector{
		MatchLabels: PrivateNetworkMemberSelector(desired.networkId),
	}

	policy := &k8snetwork.NetworkPolicy{
		ObjectMeta: k8smeta.ObjectMeta{
			Name:   desired.subdomain,
			Labels: privateNetworkObjectLabels(desired.networkId),
			OwnerReferences: []k8smeta.OwnerReference{
				privateNetworkServiceOwner(svc),
			},
		},
		Spec: k8snetwork.NetworkPolicySpec{
			PodSelector: selector,
			Ingress: []k8snetwork.NetworkPolicyIngressRule{
				{From: []k8snetwork.NetworkPolicyPeer{{PodSelector: &selector}}},
			},
			Egress: []k8snetwork.NetworkPolicyEgressRule{
				{To: []k8snetwork.NetworkPolicyPeer{{PodSelector: &selector}}},
			},
		},
	}

	if _, err := K8sClient.NetworkingV1().NetworkPolicies(desired.namespace).
		Create(ctx, policy, k8smeta.CreateOptions{}); err != nil {
		log.Warn("Failed to create the network policy of private network %s: %s", desired.networkId, err)
		return false, false
	}
	return true, true
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

func privateNetworkIpMatchesLease(
	record privateNetworkIpRecord,
	lease controller.PrivateNetworkLeaseRow,
	desired privateNetworkDesiredObjects,
) bool {
	if record.ip != lease.Ip {
		return false
	}
	if record.subnet != desired.subnetName {
		return false
	}

	if lease.MacAddress.Present && record.mac != "" && record.mac != lease.MacAddress.Value {
		return false
	}

	podName := privateNetworkExpectedPodName(lease)
	if record.podName != "" && podName != "" && record.podName != podName {
		return false
	}
	if record.namespace != "" && record.namespace != desired.namespace {
		return false
	}

	expectedIpName := privateNetworkExpectedIpName(podName, desired)
	if record.name != "" && expectedIpName != "" && record.name != expectedIpName {
		return false
	}

	return true
}

func privateNetworkExpectedPodName(lease controller.PrivateNetworkLeaseRow) string {
	if lease.MacAddress.Present {
		return "vm-" + lease.JobId + "-" + strconv.Itoa(lease.Rank)
	}
	return fmt.Sprintf("j-%s-job-%d", lease.JobId, lease.Rank)
}

func privateNetworkExpectedIpName(podName string, desired privateNetworkDesiredObjects) string {
	if podName == "" {
		return ""
	}
	return podName + "." + desired.namespace + "." + desired.provider
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

	privateNetworkDeleteLeaseIpRecords(ctx, desired, lease)

	if privateNetworkLeaseIpRecordsRemaining(ctx, desired, lease) {
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
) {
	records, ok := privateNetworkLeaseIpRecords(ctx, desired, lease)
	if !ok {
		return
	}

	var blocking []string
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
				return
			}
			continue
		}

		blocking = append(blocking, record.name)
	}

	if len(blocking) > 0 {
		log.Warn(
			"Private network %s cannot release %s yet: the addresses %v occupy the lease address or port name",
			desired.networkId,
			lease.Ip,
			blocking,
		)
	}
}

func privateNetworkLeaseIpRecordsRemaining(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	lease controller.PrivateNetworkLeaseRow,
) bool {
	records, ok := privateNetworkLeaseIpRecords(ctx, desired, lease)
	return !ok || len(records) > 0
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
	seen := map[string]struct{}{}
	for _, record := range records {
		if privateNetworkIpRecordBlocksLease(record, lease, desired) {
			result = append(result, record)
			seen[record.name] = struct{}{}
		}
	}

	expectedName := privateNetworkExpectedIpName(privateNetworkExpectedPodName(lease), desired)
	if expectedName == "" {
		return result, true
	}

	if _, known := seen[expectedName]; known {
		return result, true
	}

	item, err := privateNetworkDynamicClient.Resource(privateNetworkIpGvr).
		Get(ctx, expectedName, k8smeta.GetOptions{})
	if err == nil {
		ip := &privateNetworkKubeOvnIp{}
		if !privateNetworkKubeOvnFromUnstructured("IP", item, ip) {
			return nil, false
		}
		record := privateNetworkIpRecordFromKubeOvn(ip)
		if privateNetworkIpRecordBlocksLease(record, lease, desired) {
			result = append(result, record)
		}
		return result, true
	}
	if !k8serrors.IsNotFound(err) {
		log.Warn(
			"Failed to read the address record %s of private network %s: %s",
			expectedName,
			desired.networkId,
			err,
		)
		return nil, false
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

	selector := "ovn.kubernetes.io/subnet=" + desired.subnetName
	list, err := privateNetworkDynamicClient.Resource(privateNetworkIpGvr).
		List(ctx, k8smeta.ListOptions{LabelSelector: selector})
	if err != nil {
		log.Warn(
			"Failed to list the addresses of subnet %s for lease release: %s",
			desired.subnetName,
			err,
		)
		return nil, false
	}

	records := make([]privateNetworkIpRecord, 0, len(list.Items))
	for i := range list.Items {
		ip := &privateNetworkKubeOvnIp{}
		if !privateNetworkKubeOvnFromUnstructured("IP", &list.Items[i], ip) {
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

	expectedName := privateNetworkExpectedIpName(privateNetworkExpectedPodName(lease), desired)
	return record.name == expectedName
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

		if _, hasVm := workloads.vmNamesByJobRank[jobKey]; !hasVm {
			for name := range workloads.vmisByName {
				if jobId, rank, ok := privateNetworkVmNameToJobAndRank(name); ok {
					if jobId == lease.JobId && rank == lease.Rank {
						return false
					}
				}
			}
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
		LabelSelector: "ucloud.dk/vmName=" + vmName,
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
			Subdomain:   network.Specification.Subdomain,
			CidrBlock:   network.Status.CidrBlock,
			State:       controller.PrivateNetworkStateReady,
			WorkspaceId: "",
		}
		networksById[lease.NetworkId] = snapshot
		toRelease = append(toRelease, lease)
	}

	if len(toRelease) == 0 {
		return nil
	}

	ctx := context.Background()
	privateNetworkSubnetIpCacheBeginPass()

	for _, lease := range toRelease {
		desired, ok := privateNetworkComputeDesired(networksById[lease.NetworkId])
		if !ok {
			continue
		}

		controller.PrivateNetworkLeasesMarkReleasingForJobNetwork(job.Id, lease.NetworkId)

		workloads := privateNetworkListWorkloads(ctx)
		if !workloads.ok {
			return util.ServerHttpError("could not list the workloads of the job")
		}

		jobKey := job.Id + "/" + strconv.Itoa(lease.Rank)
		if privateNetworkLeaseWorkloadGone(ctx, desired, workloads, lease, jobKey) {
			privateNetworkDeleteLeaseIpRecords(ctx, desired, lease)
		}
	}

	return nil
}
