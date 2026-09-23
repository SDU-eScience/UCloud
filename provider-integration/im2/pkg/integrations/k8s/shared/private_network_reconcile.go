package shared

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"net/http"
	"net/netip"
	"reflect"
	"strconv"
	"strings"
	"sync"
	"time"

	k8score "k8s.io/api/core/v1"
	k8snetwork "k8s.io/api/networking/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/intstr"
	"k8s.io/client-go/dynamic"
	kvcore "kubevirt.io/api/core/v1"
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
	privateNetworkNadGvr = schema.GroupVersionResource{
		Group: "k8s.cni.cncf.io", Version: "v1", Resource: "network-attachment-definitions",
	}
)

var privateNetworkDynamicClient dynamic.Interface

const privateNetworkManagedBySelector = PrivateNetworkManagedByLabel + "=" + PrivateNetworkManagedBy

const privateNetworkFieldManager = "ucloud.dk/im-private-network"

const privateNetworkReconcileInterval = 30 * time.Second

var privateNetworkReconcileMutex sync.Mutex
var privateNetworkLastReconcile time.Time

var privateNetworkUnexpectedIpsMutex sync.Mutex
var privateNetworkUnexpectedIpsByNetwork = map[string]map[string]struct{}{}

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
		log.Fatal("Private networks are enabled but the cluster is missing required CRDs: %s", err)
	}

	client, err := dynamic.NewForConfig(K8sConfig)
	if err != nil {
		log.Fatal("Could not create the dynamic Kubernetes client for private networks: %s", err)
	}
	privateNetworkDynamicClient = client

	controller.PrivateNetworkConfigureDatabase(settings)
}

func privateNetworkRequireCrds() error {
	requiredKubeOvn := map[string]bool{"vpcs": false, "subnets": false, "ips": false}
	kubeOvnResources, err := K8sClient.Discovery().ServerResourcesForGroupVersion("kubeovn.io/v1")
	if err == nil {
		for _, resource := range kubeOvnResources.APIResources {
			if _, wanted := requiredKubeOvn[resource.Name]; wanted {
				requiredKubeOvn[resource.Name] = true
			}
		}
	}

	nadFound := false
	nadResources, err := K8sClient.Discovery().ServerResourcesForGroupVersion("k8s.cni.cncf.io/v1")
	if err == nil {
		for _, resource := range nadResources.APIResources {
			if resource.Name == "network-attachment-definitions" {
				nadFound = true
			}
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

		now := time.Now()
		if !privateNetworkLastReconcile.IsZero() && now.Sub(privateNetworkLastReconcile) < privateNetworkReconcileInterval {
			return
		}
		privateNetworkLastReconcile = now

		privateNetworkRunReconcilePass()
	}()
}

type privateNetworkObjectSnapshot struct {
	vpcsByName     map[string]*unstructured.Unstructured
	subnetsByName  map[string]*unstructured.Unstructured
	nadsByName     map[string]*unstructured.Unstructured
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

	if objects.ok {
		privateNetworkQuarantineScan(networks, leases, objects)
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
		vpcsByName:     map[string]*unstructured.Unstructured{},
		subnetsByName:  map[string]*unstructured.Unstructured{},
		nadsByName:     map[string]*unstructured.Unstructured{},
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
		result.vpcsByName[vpcs.Items[i].GetName()] = &vpcs.Items[i]
	}

	subnets, err := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr).
		List(ctx, k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector})
	if err != nil {
		log.Warn("Failed to list managed Subnets for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range subnets.Items {
		result.subnetsByName[subnets.Items[i].GetName()] = &subnets.Items[i]
	}

	nads, err := privateNetworkDynamicClient.Resource(privateNetworkNadGvr).
		Namespace(ServiceConfig.Compute.Namespace).
		List(ctx, k8smeta.ListOptions{LabelSelector: privateNetworkManagedBySelector})
	if err != nil {
		log.Warn("Failed to list managed network attachments for private network reconciliation: %s", err)
		result.ok = false
		return result
	}
	for i := range nads.Items {
		result.nadsByName[nads.Items[i].GetName()] = &nads.Items[i]
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
		item := &ips.Items[i]
		subnet, _, _ := unstructured.NestedString(item.Object, "spec", "subnet")
		podName, _, _ := unstructured.NestedString(item.Object, "spec", "podName")
		namespace, _, _ := unstructured.NestedString(item.Object, "spec", "namespace")
		ipAddress, _, _ := unstructured.NestedString(item.Object, "spec", "v4IpAddress")
		mac, _, _ := unstructured.NestedString(item.Object, "spec", "macAddress")
		if subnet == "" || ipAddress == "" {
			continue
		}
		record := privateNetworkIpRecord{
			name:      item.GetName(),
			namespace: namespace,
			podName:   podName,
			subnet:    subnet,
			ip:        ipAddress,
			mac:       mac,
			uid:       item.GetUID(),
		}
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
		return
	}

	if !privateNetworkSubnetReady(subnet) {
		return
	}

	if err := controller.PrivateNetworkUpdateCoreCidrBlock(desired.networkId, desired.cidr); err != nil {
		log.Warn("Failed to report the CIDR of private network %s to Core: %s", desired.networkId, err)
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

	nadClient := privateNetworkDynamicClient.Resource(privateNetworkNadGvr).
		Namespace(ServiceConfig.Compute.Namespace)
	subnetClient := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr)
	vpcClient := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr)

	nadExists := false
	if nad := objects.nadsByName[desired.subdomain]; nad != nil {
		nadExists = true
		if privateNetworkObjectOwnedBy(nad, desired.networkId) {
			privateNetworkDeleteWithUid(ctx, nadClient, desired.subdomain, nad.GetUID())
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

func privateNetworkReconcileOrphans(
	ctx context.Context,
	objects privateNetworkObjectSnapshot,
) int {
	if !objects.ok {
		return 0
	}

	deleted := 0

	deleteOrphan := func(gvr schema.GroupVersionResource, namespace string, name string, id string, uid types.UID) {
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

			var client dynamic.ResourceInterface
			if namespace != "" {
				client = privateNetworkDynamicClient.Resource(gvr).Namespace(namespace)
			} else {
				client = privateNetworkDynamicClient.Resource(gvr)
			}

			if privateNetworkDeleteWithUid(ctx, client, name, uid) {
				deleted++
				log.Warn("Deleted the orphaned private network object %s (network %s is not tracked)", name, id)
			}
		})
	}

	for name, vpc := range objects.vpcsByName {
		id, _, _ := unstructured.NestedString(vpc.Object, "metadata", "labels", PrivateNetworkIdLabel)
		deleteOrphan(privateNetworkVpcGvr, "", name, id, vpc.GetUID())
	}

	for name, subnet := range objects.subnetsByName {
		id, _, _ := unstructured.NestedString(subnet.Object, "metadata", "labels", PrivateNetworkIdLabel)
		deleteOrphan(privateNetworkSubnetGvr, "", name, id, subnet.GetUID())
	}

	for name, nad := range objects.nadsByName {
		id, _, _ := unstructured.NestedString(nad.Object, "metadata", "labels", PrivateNetworkIdLabel)
		deleteOrphan(privateNetworkNadGvr, ServiceConfig.Compute.Namespace, name, id, nad.GetUID())
	}

	return deleted
}

func privateNetworkObjectOwnedBy(obj *unstructured.Unstructured, networkId string) bool {
	id, found, _ := unstructured.NestedString(obj.Object, "metadata", "labels", PrivateNetworkIdLabel)
	return found && id == networkId
}

func privateNetworkEnsureVpcObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *unstructured.Unstructured,
) (bool, bool) {
	return privateNetworkApplyObject(
		ctx,
		"Vpc",
		privateNetworkVpcObject(desired),
		existing,
		privateNetworkDynamicClient.Resource(privateNetworkVpcGvr),
		nil,
	)
}

func privateNetworkVpcObject(desired privateNetworkDesiredObjects) *unstructured.Unstructured {
	result := &unstructured.Unstructured{}
	result.SetAPIVersion("kubeovn.io/v1")
	result.SetKind("Vpc")
	result.SetName(desired.subdomain)
	result.SetLabels(privateNetworkObjectLabels(desired.networkId))
	result.Object["spec"] = map[string]any{
		"staticRoutes":   []any{},
		"policyRoutes":   []any{},
		"vpcPeerings":    []any{},
		"enableExternal": false,
		"enableBfd":      false,
	}
	return result
}

func privateNetworkEnsureSubnetObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *unstructured.Unstructured,
) (bool, bool) {
	return privateNetworkApplyObject(
		ctx,
		"Subnet",
		privateNetworkSubnetObject(desired),
		existing,
		privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr),
		[]string{"protocol", "vpc", "cidrBlock"},
	)
}

func privateNetworkSubnetObject(desired privateNetworkDesiredObjects) *unstructured.Unstructured {
	result := &unstructured.Unstructured{}
	result.SetAPIVersion("kubeovn.io/v1")
	result.SetKind("Subnet")
	result.SetName(desired.subnetName)
	result.SetLabels(privateNetworkObjectLabels(desired.networkId))
	result.Object["spec"] = map[string]any{
		"protocol":    "IPv4",
		"vpc":         desired.subdomain,
		"cidrBlock":   desired.cidr,
		"gateway":     desired.gateway,
		"provider":    desired.provider,
		"natOutgoing": false,
		"enableDHCP":  false,
		"namespaces":  []any{desired.namespace},
		"excludeIps":  []any{desired.excludeIps},
	}
	return result
}

func privateNetworkEnsureNadObject(
	ctx context.Context,
	desired privateNetworkDesiredObjects,
	existing *unstructured.Unstructured,
) (bool, bool) {
	return privateNetworkApplyObject(
		ctx,
		"network attachment",
		privateNetworkNadObject(desired),
		existing,
		privateNetworkDynamicClient.Resource(privateNetworkNadGvr).
			Namespace(ServiceConfig.Compute.Namespace),
		nil,
	)
}

func privateNetworkNadObject(desired privateNetworkDesiredObjects) *unstructured.Unstructured {
	result := &unstructured.Unstructured{}
	result.SetAPIVersion("k8s.cni.cncf.io/v1")
	result.SetKind("NetworkAttachmentDefinition")
	result.SetName(desired.subdomain)
	result.SetNamespace(desired.namespace)
	result.SetLabels(privateNetworkObjectLabels(desired.networkId))
	result.Object["spec"] = map[string]any{
		"config": desired.nadConfig,
	}
	return result
}

func privateNetworkApplyObject(
	ctx context.Context,
	kind string,
	desired *unstructured.Unstructured,
	existing *unstructured.Unstructured,
	client dynamic.ResourceInterface,
	immutableFields []string,
) (bool, bool) {
	name := desired.GetName()

	if existing == nil {
		if _, err := client.Create(ctx, desired, k8smeta.CreateOptions{}); err != nil {
			log.Warn("Failed to create the %s of private network: %s", kind, err)
			return false, false
		}
		return true, true
	}

	if !privateNetworkObjectOwnedBy(existing, desired.GetLabels()[PrivateNetworkIdLabel]) {
		log.Warn(
			"A %s named %s collides with a private network and will not be modified",
			kind,
			name,
		)
		return false, false
	}

	if len(immutableFields) > 0 && privateNetworkImmutableDrift(existing, desired, immutableFields) {
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

	if !privateNetworkSpecDrift(existing, desired) {
		return true, false
	}

	if _, err := client.Apply(ctx, name, desired, k8smeta.ApplyOptions{
		FieldManager: privateNetworkFieldManager,
		Force:        true,
	}); err != nil {
		log.Warn("Failed to repair the %s of a private network: %s", kind, err)
		return false, false
	}
	return true, true
}

func privateNetworkImmutableDrift(
	existing *unstructured.Unstructured,
	desired *unstructured.Unstructured,
	immutableFields []string,
) bool {
	for _, field := range immutableFields {
		current, _, _ := unstructured.NestedString(existing.Object, "spec", field)
		wanted, _, _ := unstructured.NestedString(desired.Object, "spec", field)
		if current != wanted {
			return true
		}
	}
	return false
}

func privateNetworkSpecDrift(existing *unstructured.Unstructured, desired *unstructured.Unstructured) bool {
	existingLabels, _, _ := unstructured.NestedMap(existing.Object, "metadata", "labels")
	if !privateNetworkLabelsEqual(existingLabels, desired.GetLabels()) {
		return true
	}

	return !privateNetworkValueEqual(existing.Object["spec"], desired.Object["spec"])
}

func privateNetworkLabelsEqual(existing map[string]any, desired map[string]string) bool {
	for key, value := range desired {
		if existing[key] != value {
			return false
		}
	}
	return true
}

func privateNetworkValueEqual(current any, desired any) bool {
	switch desiredValue := desired.(type) {
	case map[string]any:
		currentMap, ok := current.(map[string]any)
		if !ok {
			return false
		}
		for key, wanted := range desiredValue {
			if !privateNetworkValueEqual(currentMap[key], wanted) {
				return false
			}
		}
		return true

	case []any:
		if current == nil {
			current = []any{}
		}
		currentList, ok := current.([]any)
		if !ok || len(currentList) != len(desiredValue) {
			return false
		}
		for i := range desiredValue {
			if !privateNetworkValueEqual(currentList[i], desiredValue[i]) {
				return false
			}
		}
		return true

	case []string:
		if current == nil {
			current = []any{}
		}
		currentList, ok := current.([]any)
		if !ok || len(currentList) != len(desiredValue) {
			return false
		}
		for i := range desiredValue {
			if !privateNetworkValueEqual(currentList[i], desiredValue[i]) {
				return false
			}
		}
		return true

	case string:
		currentString, ok := current.(string)
		return ok && currentString == desiredValue

	case bool:
		if current == nil {
			return !desiredValue
		}
		currentBool, ok := current.(bool)
		return ok && currentBool == desiredValue

	default:
		return reflect.DeepEqual(current, desired)
	}
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

func privateNetworkVpcReady(vpc *unstructured.Unstructured) bool {
	standby, found, _ := unstructured.NestedBool(vpc.Object, "status", "standby")
	return found && standby
}

func privateNetworkSubnetReady(subnet *unstructured.Unstructured) bool {
	conditions, found, _ := unstructured.NestedSlice(subnet.Object, "status", "conditions")
	if !found {
		return false
	}

	validated := false
	ready := false
	for _, entry := range conditions {
		condition, ok := entry.(map[string]any)
		if !ok {
			continue
		}

		conditionType, _ := condition["type"].(string)
		status, _ := condition["status"].(string)
		if conditionType == "Validated" && status == "True" {
			validated = true
		}
		if conditionType == "Ready" && status == "True" {
			ready = true
		}
	}

	return validated && ready
}

func privateNetworkQuarantineScan(
	networks []controller.PrivateNetworkSnapshotNetwork,
	leases []controller.PrivateNetworkLeaseRow,
	objects privateNetworkObjectSnapshot,
) bool {
	leasesByKey := map[string]controller.PrivateNetworkLeaseRow{}
	for _, lease := range leases {
		leasesByKey[lease.NetworkId+"|"+lease.Ip] = lease
	}

	allOk := true
	for i := range networks {
		network := &networks[i]
		desired, ok := privateNetworkComputeDesired(*network)
		if !ok {
			continue
		}

		var unexpected []string
		for _, record := range objects.ips[desired.subnetName] {
			lease, leased := leasesByKey[network.ResourceId+"|"+record.ip]
			if leased && privateNetworkIpMatchesLease(record, lease, desired) {
				continue
			}
			unexpected = append(unexpected, record.ip)
		}

		privateNetworkRememberUnexpectedIps(network.ResourceId, unexpected)

		if err := controller.PrivateNetworkQuarantineUpdate(network.ResourceId, unexpected); err != nil {
			log.Warn("Failed to quarantine the unexpected addresses of private network %s: %s", network.ResourceId, err)
			allOk = false
		}
	}

	return allOk
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

func privateNetworkRememberUnexpectedIps(networkId string, unexpected []string) {
	privateNetworkUnexpectedIpsMutex.Lock()
	defer privateNetworkUnexpectedIpsMutex.Unlock()

	existing := privateNetworkUnexpectedIpsByNetwork[networkId]
	next := map[string]struct{}{}
	for _, ip := range unexpected {
		next[ip] = struct{}{}
		if _, seen := existing[ip]; !seen {
			log.Warn(
				"Private network %s has the unexpected Kube-OVN address %s. It is excluded from allocation.",
				networkId,
				ip,
			)
		}
	}

	for ip := range existing {
		if _, kept := next[ip]; !kept {
			log.Info("The unexpected Kube-OVN address %s of private network %s is gone", ip, networkId)
		}
	}

	if len(next) == 0 {
		delete(privateNetworkUnexpectedIpsByNetwork, networkId)
	} else {
		privateNetworkUnexpectedIpsByNetwork[networkId] = next
	}
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
		record := privateNetworkIpRecordFromUnstructured(item)
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
		records = append(records, privateNetworkIpRecordFromUnstructured(&list.Items[i]))
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

func privateNetworkIpRecordFromUnstructured(item *unstructured.Unstructured) privateNetworkIpRecord {
	subnet, _, _ := unstructured.NestedString(item.Object, "spec", "subnet")
	podName, _, _ := unstructured.NestedString(item.Object, "spec", "podName")
	namespace, _, _ := unstructured.NestedString(item.Object, "spec", "namespace")
	ipAddress, _, _ := unstructured.NestedString(item.Object, "spec", "v4IpAddress")
	mac, _, _ := unstructured.NestedString(item.Object, "spec", "macAddress")

	return privateNetworkIpRecord{
		name:      item.GetName(),
		namespace: namespace,
		podName:   podName,
		subnet:    subnet,
		ip:        ipAddress,
		mac:       mac,
		uid:       item.GetUID(),
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
