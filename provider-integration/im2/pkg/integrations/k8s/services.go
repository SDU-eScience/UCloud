package k8s

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	k8score "k8s.io/api/core/v1"
	discovery "k8s.io/api/discovery/v1"
	networking "k8s.io/api/networking/v1"
	k8sequality "k8s.io/apimachinery/pkg/api/equality"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	"ucloud.dk/pkg/controller"
	gw "ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/integrations/k8s/shared"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	servicesManagedByLabel = "ucloud.dk/managed-by"
	servicesIdLabel        = "ucloud.dk/service-id"
	servicesManagedBy      = "im"
)

var servicesStateMutex sync.Mutex
var servicesProbeState = map[string]*serviceProbeRecord{}
var servicesDrains = map[string]map[string]time.Time{}
var servicesLastReport = map[string]string{}
var servicesLastClusters = map[string][]string{}

type serviceProbeRecord struct {
	Successes map[string]int
	Failures  map[string]int
	Stable    map[string]bool
}

type serviceBackend struct {
	JobId    string
	Rank     int
	PodName  string
	Ip       string
	Draining bool
}

func ServiceK8sName(serviceId string) string {
	return "svc-" + serviceId
}

func ServiceDnsName(serviceId string) string {
	return fmt.Sprintf("%s.%s.svc.cluster.local", ServiceK8sName(serviceId), sharedNamespace())
}

func sharedNamespace() string {
	return shared.ServiceConfig.Compute.Namespace
}

func servicesObjectLabels(serviceId string) map[string]string {
	return map[string]string{
		servicesManagedByLabel: servicesManagedBy,
		servicesIdLabel:        serviceId,
	}
}

func serviceCreate(service *orc.Service) *util.HttpError {
	service.Status.ProvisioningState = string(orc.ServiceStatePreparing)
	controller.ServiceTrack(*service)
	servicesReconcileCreated(service.Id)
	return nil
}

func serviceDelete(service *orc.Service) *util.HttpError {
	ctx := context.Background()
	name := ServiceK8sName(service.Id)
	namespace := sharedNamespace()

	_ = shared.K8sClient.CoreV1().Services(namespace).Delete(ctx, name, k8smeta.DeleteOptions{})
	_ = shared.K8sClient.DiscoveryV1().EndpointSlices(namespace).DeleteCollection(
		ctx,
		k8smeta.DeleteOptions{},
		k8smeta.ListOptions{LabelSelector: servicesIdLabel + "=" + service.Id},
	)

	servicesStateMutex.Lock()
	for _, clusterName := range servicesLastClusters[service.Id] {
		gw.SendMessage(gw.ConfigurationMessage{
			ClusterDown: &gw.EnvoyCluster{Name: clusterName},
		})
	}
	delete(servicesLastClusters, service.Id)
	delete(servicesDrains, service.Id)
	delete(servicesLastReport, service.Id)
	delete(servicesProbeState, service.Id)
	servicesStateMutex.Unlock()

	controller.ServiceDeleteTracked(*service)
	serviceReconcileLinkTargets()
	return nil
}

func serviceRetrieveProducts() []orc.ServiceSupport {
	return shared.ServiceSupport
}

func serviceClusterName(serviceId string, portName string) string {
	return "svc_" + serviceId + "_" + portName
}

func serviceUpdateMembers(request orc.ServicesProviderUpdateMembersRequest) *util.HttpError {
	tracked, ok := controller.ServiceRetrieveTracked(request.Service.Id)
	if !ok {
		tracked = request.Service
	}

	var drainDeadline time.Time
	for _, port := range tracked.Specification.Ports {
		deadline := time.Now().Add(time.Duration(port.DrainTimeoutSeconds.GetOrDefault(30)) * time.Second)
		if deadline.After(drainDeadline) {
			drainDeadline = deadline
		}
	}
	if drainDeadline.IsZero() {
		drainDeadline = time.Now().Add(30 * time.Second)
	}

	servicesStateMutex.Lock()
	for _, jobId := range request.AddedJobIds {
		if !slices.Contains(tracked.Status.Members, jobId) {
			tracked.Status.Members = append(tracked.Status.Members, jobId)
		}
		if drains, ok := servicesDrains[tracked.Id]; ok {
			delete(drains, jobId)
		}
	}

	for _, jobId := range request.RemovedJobIds {
		tracked.Status.Members = util.RemoveFirst(tracked.Status.Members, jobId)
		drains, ok := servicesDrains[tracked.Id]
		if !ok {
			drains = map[string]time.Time{}
			servicesDrains[tracked.Id] = drains
		}
		drains[jobId] = drainDeadline
	}
	servicesStateMutex.Unlock()

	controller.ServiceTrack(tracked)
	servicesReconcileSoon()
	return nil
}

func serviceOnUpdatedLabels(service *orc.Service) *util.HttpError {
	controller.ServiceTrack(*service)
	return nil
}

func serviceUpdateAcl(service *orc.Service) *util.HttpError {
	controller.ServiceTrack(*service)
	return nil
}

func serviceUpdateSpec(request orc.ServicesProviderUpdateRequest) *util.HttpError {
	service := request.Service
	service.Specification.Name = request.Name
	service.Specification.Ports = request.Ports
	controller.ServiceTrack(service)
	servicesReconcileSoon()
	return nil
}

func serviceIngressSetTarget(request orc.IngressesProviderSetTargetRequest) *util.HttpError {
	ingress := request.Ingress
	ingress.Specification.Target = request.Target
	controller.LinkTrack(ingress)
	serviceReconcileLinkTargets()
	return nil
}

func servicesReconcileCreated(serviceId string) {
	defer func() {
		if r := recover(); r != nil {
			log.Warn("services: reconcile panic: %v", r)
		}
	}()

	servicesReconcileMutex.Lock()
	defer servicesReconcileMutex.Unlock()

	service, ok := controller.ServiceRetrieveTracked(serviceId)
	if !ok {
		return
	}

	servicesReconcileOne(context.Background(), service)
	servicesReconcileLinksForService(serviceId)
}

func servicesReconcileLinksForService(serviceId string) {
	referenced := false
	for _, ingress := range controller.LinkRetrieveAll() {
		target := ingress.Specification.Target
		if target.Present && target.Value.ServiceId == serviceId {
			referenced = true
			break
		}
	}

	if referenced {
		serviceReconcileLinkTargets()
	}
}

var servicesReconcileTimerMutex sync.Mutex
var servicesReconcileTimer *time.Timer

func servicesReconcileSoon() {
	servicesReconcileTimerMutex.Lock()
	defer servicesReconcileTimerMutex.Unlock()

	if servicesReconcileTimer != nil {
		return
	}
	servicesReconcileTimer = time.AfterFunc(100*time.Millisecond, func() {
		servicesReconcileTimerMutex.Lock()
		servicesReconcileTimer = nil
		servicesReconcileTimerMutex.Unlock()
		servicesReconcile()
	})
}

var servicesLoopStarted sync.Once

func ServicesStartLoop() {
	servicesLoopStarted.Do(func() {
		go func() {
			for util.IsAlive {
				time.Sleep(5 * time.Second)
				servicesReconcile()
			}
		}()
	})
}

var servicesReconcileMutex sync.Mutex

func servicesReconcile() {
	defer func() {
		if r := recover(); r != nil {
			log.Warn("services: reconcile panic: %v", r)
		}
	}()

	servicesReconcileMutex.Lock()
	defer servicesReconcileMutex.Unlock()

	all := controller.ServicesRetrieveAllTracked()
	ctx := context.Background()

	ids := map[string]util.Empty{}
	for _, svc := range all {
		ids[svc.Id] = util.Empty{}
		servicesReconcileOne(ctx, svc)
	}

	servicesRewriteMemberFirewalls(ctx, all)
	servicesPruneOrphans(ctx, ids)
	serviceReconcileLinkTargets()
}

func servicesReconcileOne(ctx context.Context, service orc.Service) {
	if len(service.Specification.Ports) == 0 {
		return
	}

	members := servicesResolveMembers(service)
	dnsName := ""
	backends := []orc.ServiceBackendStatus{}
	unavailable := ""

	if service.Specification.InternalEndpoint.Present {
		dnsName = ServiceDnsName(service.Id)
		if !servicesEnsureClusterIpService(ctx, service) {
			unavailable = "could not create the internal endpoint in the cluster"
		}
	}

	clusterNames := []string{}
	if unavailable == "" {
		for _, port := range service.Specification.Ports {
			if port.Protocol == orc.ServicePortProtocolUdp {
				backends = servicesReconcileUdpPort(ctx, service, port, members, backends)
				continue
			}
			clusterNames = append(clusterNames, serviceClusterName(service.Id, port.Name))
			backends = servicesReconcilePort(ctx, service, port, members, backends)
		}
	}

	servicesStateMutex.Lock()
	for _, previous := range servicesLastClusters[service.Id] {
		if !slices.Contains(clusterNames, previous) {
			gw.SendMessage(gw.ConfigurationMessage{
				ClusterDown: &gw.EnvoyCluster{Name: previous},
			})
		}
	}
	servicesLastClusters[service.Id] = clusterNames
	servicesStateMutex.Unlock()

	reported := service
	if dnsName != "" && unavailable == "" {
		reported.Status.InternalEndpoint = util.OptValue(orc.ServiceInternalEndpoint{
			PrivateNetworkId: service.Specification.InternalEndpoint.Value.PrivateNetworkId,
			DnsName:          dnsName,
		})
	}
	reported.Status.Backends = backends
	if unavailable != "" {
		reported.Status.ProvisioningState = string(orc.ServiceStateUnavailable)
		reported.Status.Message = util.OptValue(unavailable)
	} else {
		reported.Status.ProvisioningState = string(orc.ServiceStateReady)
	}

	summary := fmt.Sprintf("%v|%v|%v", reported.Status.ProvisioningState, dnsName, backendsSummary(backends))
	servicesStateMutex.Lock()
	changed := servicesLastReport[service.Id] != summary
	servicesLastReport[service.Id] = summary
	servicesStateMutex.Unlock()

	if changed {
		update := orc.ServiceUpdate{
			ProvisioningState: util.OptValue(reported.Status.ProvisioningState),
			InternalDnsName:   util.OptStringIfNotEmpty(dnsName),
			Backends:          util.OptValue(backends),
			Timestamp:         fndapi.Timestamp(time.Now()),
		}
		if unavailable != "" {
			update.Message = util.OptValue(unavailable)
		}
		controller.ServiceReportUpdate(reported, update)
	}
}

func backendsSummary(backends []orc.ServiceBackendStatus) string {
	result := ""
	for _, backend := range backends {
		result += backend.JobId + "/" + strconv.Itoa(backend.Rank) + ":"
		for _, port := range backend.Ports {
			result += port.Name + "=" + port.State + ","
		}
		result += ";"
	}
	return result
}

func servicesReconcilePort(
	ctx context.Context,
	service orc.Service,
	port orc.ServicePort,
	members []serviceBackend,
	backendsIn []orc.ServiceBackendStatus,
) []orc.ServiceBackendStatus {
	backends := backendsIn
	byMember := map[string]int{}
	for i, backend := range backends {
		byMember[backend.JobId+"/"+strconv.Itoa(backend.Rank)] = i
	}

	for _, member := range members {
		key := member.JobId + "/" + strconv.Itoa(member.Rank)
		backendIndex, ok := byMember[key]
		if !ok {
			backends = append(backends, orc.ServiceBackendStatus{
				JobId: member.JobId,
				Rank:  member.Rank,
				Ports: []orc.ServiceBackendPortStatus{},
			})
			backendIndex = len(backends) - 1
			byMember[key] = backendIndex
		}

		healthy, known := servicesProbeMember(service, port, member)
		state := orc.ServiceBackendPortStateUnhealthy
		if member.Draining {
			state = orc.ServiceBackendPortStateDraining
		} else if healthy {
			state = orc.ServiceBackendPortStateHealthy
		}

		if known || member.Draining {
			backends[backendIndex].Ports = servicesReplacePortState(backends[backendIndex].Ports, port.Name, state)
		}
	}

	servicesPushCluster(service, port, members, backends)
	if service.Specification.InternalEndpoint.Present {
		servicesEnsureEndpointSlice(ctx, service, port, members, backends)
	}

	return backends
}

func servicesReconcileUdpPort(
	ctx context.Context,
	service orc.Service,
	port orc.ServicePort,
	members []serviceBackend,
	backendsIn []orc.ServiceBackendStatus,
) []orc.ServiceBackendStatus {
	backends := backendsIn
	byMember := map[string]int{}
	for i, backend := range backends {
		byMember[backend.JobId+"/"+strconv.Itoa(backend.Rank)] = i
	}

	for _, member := range members {
		key := member.JobId + "/" + strconv.Itoa(member.Rank)
		backendIndex, ok := byMember[key]
		if !ok {
			backends = append(backends, orc.ServiceBackendStatus{
				JobId: member.JobId,
				Rank:  member.Rank,
				Ports: []orc.ServiceBackendPortStatus{},
			})
			backendIndex = len(backends) - 1
			byMember[key] = backendIndex
		}

		state := orc.ServiceBackendPortStateHealthy
		if member.Draining {
			state = orc.ServiceBackendPortStateDraining
		} else if member.Ip == "" && shared.K8sInCluster {
			state = orc.ServiceBackendPortStateUnhealthy
		}

		backends[backendIndex].Ports = servicesReplacePortState(backends[backendIndex].Ports, port.Name, state)
	}

	if service.Specification.InternalEndpoint.Present {
		servicesEnsureEndpointSlice(ctx, service, port, members, backends)
	}

	return backends
}

func servicesReplacePortState(ports []orc.ServiceBackendPortStatus, name string, state string) []orc.ServiceBackendPortStatus {
	for i := range ports {
		if ports[i].Name == name {
			ports[i].State = state
			return ports
		}
	}
	return append(ports, orc.ServiceBackendPortStatus{Name: name, State: state})
}

func servicesResolveMembers(service orc.Service) []serviceBackend {
	result := []serviceBackend{}
	now := time.Now()

	servicesStateMutex.Lock()
	drains := map[string]time.Time{}
	for jobId, deadline := range servicesDrains[service.Id] {
		drains[jobId] = deadline
	}
	servicesDrainsRef := servicesDrains
	servicesStateMutex.Unlock()

	candidates := []string{}
	for _, jobId := range service.Status.Members {
		if !slices.Contains(candidates, jobId) {
			candidates = append(candidates, jobId)
		}
	}
	for jobId := range drains {
		if !slices.Contains(candidates, jobId) {
			candidates = append(candidates, jobId)
		}
	}

	for _, jobId := range candidates {
		deadline, draining := drains[jobId]
		if draining && now.After(deadline) {
			servicesStateMutex.Lock()
			if serviceDrains, ok := servicesDrainsRef[service.Id]; ok {
				delete(serviceDrains, jobId)
			}
			servicesStateMutex.Unlock()
			draining = false
		}

		job, ok := controller.JobRetrieve(jobId)
		if !ok {
			continue
		}

		if job.Status.State.IsFinal() || job.Status.State == orc.JobStateSuspended {
			continue
		}

		for rank := 0; rank < job.Specification.Replicas; rank++ {
			backend := serviceBackend{
				JobId:    jobId,
				Rank:     rank,
				PodName:  servicesPodName(job, rank),
				Draining: draining,
			}

			backend.Ip = servicesPodIp(job, backend.PodName)
			result = append(result, backend)
		}
	}

	return result
}

func servicesPodIp(job *orc.Job, podName string) string {
	backend := job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value.Description.Backend
	if backend == orc.ToolBackendVirtualMachine {
		if pod, ok := shared.VmPods.Retrieve(podName); ok && pod.Status.Phase == k8score.PodRunning {
			return pod.Status.PodIP
		}
		return ""
	}

	if pod, ok := shared.JobPods.Retrieve(podName); ok && pod.Status.Phase == k8score.PodRunning {
		return pod.Status.PodIP
	}
	return ""
}

func servicesPodName(job *orc.Job, rank int) string {
	backend := job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value.Description.Backend
	if backend == orc.ToolBackendVirtualMachine {
		return fmt.Sprintf("vm-%s-%d", job.Id, rank)
	}
	return fmt.Sprintf("j-%s-job-%d", job.Id, rank)
}

func servicesProbeMember(service orc.Service, port orc.ServicePort, member serviceBackend) (bool, bool) {
	if member.Ip == "" {
		return false, true
	}

	check := orc.ServiceHealthCheck{
		Type:               orc.ServiceHealthCheckTypeTcp,
		IntervalSeconds:    5,
		TimeoutSeconds:     2,
		HealthyThreshold:   2,
		UnhealthyThreshold: 2,
	}
	if port.HealthCheck.Present {
		check = port.HealthCheck.Value
	}

	key := service.Id + "/" + port.Name + "/" + member.JobId + "/" + strconv.Itoa(member.Rank)

	address := member.Ip
	probePort := port.Port
	if !shared.K8sInCluster {
		address = "127.0.0.1"
		probePort = shared.EstablishTunnel(member.PodName, probePort)
	}

	servicesStateMutex.Lock()
	record, ok := servicesProbeState[service.Id]
	if !ok {
		record = &serviceProbeRecord{
			Successes: map[string]int{},
			Failures:  map[string]int{},
			Stable:    map[string]bool{},
		}
		servicesProbeState[service.Id] = record
	}

	if member.Draining {
		delete(record.Successes, key)
		delete(record.Failures, key)
		delete(record.Stable, key)
		servicesStateMutex.Unlock()
		return true, true
	}

	healthyThreshold := check.HealthyThreshold
	if healthyThreshold < 1 {
		healthyThreshold = 1
	}
	unhealthyThreshold := check.UnhealthyThreshold
	if unhealthyThreshold < 1 {
		unhealthyThreshold = 1
	}
	servicesStateMutex.Unlock()

	serverName := ""
	if port.BackendTls.Present {
		serverName = port.BackendTls.Value.ServerName
	}

	healthy := servicesProbeOnce(check, address, probePort, serverName)

	servicesStateMutex.Lock()
	defer servicesStateMutex.Unlock()

	if healthy {
		record.Successes[key]++
		record.Failures[key] = 0
	} else {
		record.Failures[key]++
		record.Successes[key] = 0
	}

	previouslyHealthy := record.Stable[key]
	var result bool
	if healthy && record.Successes[key] >= healthyThreshold {
		record.Stable[key] = true
		result = true
	} else if !healthy && record.Failures[key] >= unhealthyThreshold {
		record.Stable[key] = false
		result = false
	} else {
		result = previouslyHealthy
	}

	return result, true
}

var servicesProbeClient = &http.Client{
	Transport: &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	},
}

func servicesProbeOnce(check orc.ServiceHealthCheck, address string, port int, serverName string) bool {
	timeout := time.Duration(check.TimeoutSeconds) * time.Second
	if timeout <= 0 {
		timeout = 2 * time.Second
	}

	probePort := port
	if check.Port.Present {
		probePort = check.Port.Value
	}

	switch check.Type {
	case orc.ServiceHealthCheckTypeHttp, orc.ServiceHealthCheckTypeHttps:
		scheme := "http"
		if check.Type == orc.ServiceHealthCheckTypeHttps {
			scheme = "https"
		}

		client := servicesProbeClient
		if client.Timeout != timeout {
			client = &http.Client{
				Timeout:   timeout,
				Transport: client.Transport,
			}
		}

		host := serverName
		if host == "" {
			host = address
		}

		request, err := http.NewRequest(http.MethodGet, scheme+"://"+net.JoinHostPort(address, strconv.Itoa(probePort))+check.Path, nil)
		if err != nil {
			return false
		}
		request.Host = host

		resp, err := client.Do(request)
		if err != nil {
			return false
		}
		util.SilentClose(resp.Body)
		return resp.StatusCode >= 200 && resp.StatusCode < 400

	default:
		conn, err := net.DialTimeout("tcp", net.JoinHostPort(address, strconv.Itoa(probePort)), timeout)
		if err != nil {
			return false
		}
		util.SilentClose(conn)
		return true
	}
}

func servicesEnsureClusterIpService(ctx context.Context, service orc.Service) bool {
	name := ServiceK8sName(service.Id)
	namespace := sharedNamespace()
	servicesClient := shared.K8sClient.CoreV1().Services(namespace)

	desiredPorts := []k8score.ServicePort{}
	for _, port := range service.Specification.Ports {
		protocol := k8score.ProtocolTCP
		if port.Protocol == orc.ServicePortProtocolUdp {
			protocol = k8score.ProtocolUDP
		}

		desiredPorts = append(desiredPorts, k8score.ServicePort{
			Name:       port.Name,
			Protocol:   protocol,
			Port:       int32(port.Port),
			TargetPort: intstr.FromInt32(int32(port.Port)),
		})
	}

	existing, ok := shared.ComputeServices.Retrieve(name)
	if !ok {
		created := &k8score.Service{
			ObjectMeta: k8smeta.ObjectMeta{
				Name:   name,
				Labels: servicesObjectLabels(service.Id),
			},
			Spec: k8score.ServiceSpec{
				Type:  k8score.ServiceTypeClusterIP,
				Ports: desiredPorts,
			},
		}
		if _, createErr := servicesClient.Create(ctx, created, k8smeta.CreateOptions{}); createErr != nil {
			log.Warn("services: failed to create service %s: %s", service.Id, createErr)
			return false
		}
		return true
	}

	if !servicesPortsEqual(existing.Spec.Ports, desiredPorts) {
		updated := existing.DeepCopy()
		updated.Spec.Ports = desiredPorts
		if _, updateErr := servicesClient.Update(ctx, updated, k8smeta.UpdateOptions{}); updateErr != nil {
			log.Warn("services: failed to update service %s: %s", service.Id, updateErr)
			return false
		}
	}

	return true
}

func servicesPortsEqual(a, b []k8score.ServicePort) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i].Name != b[i].Name || a[i].Protocol != b[i].Protocol || a[i].Port != b[i].Port {
			return false
		}
	}
	return true
}

func servicesRewriteMemberFirewalls(ctx context.Context, all []orc.Service) {
	if !shared.K8sInCluster {
		return
	}

	sorted := append([]orc.Service(nil), all...)
	slices.SortStableFunc(sorted, func(a, b orc.Service) int {
		return strings.Compare(a.Id, b.Id)
	})

	desiredByJob := map[string][]networking.NetworkPolicyIngressRule{}
	for _, service := range sorted {
		if !service.Specification.InternalEndpoint.Present {
			continue
		}

		rule := servicesMemberFirewallRule(service)
		for _, jobId := range service.Status.Members {
			desiredByJob[jobId] = append(desiredByJob[jobId], rule)
		}
	}
	policies := shared.K8sClient.NetworkingV1().NetworkPolicies(sharedNamespace())
	for _, policy := range shared.ComputeNetworkPolicies.List() {
		desired := desiredByJob[shared.FirewallJobId(policy.Name)]

		kept := []networking.NetworkPolicyIngressRule{}
		serviceOwned := false
		for _, rule := range policy.Spec.Ingress {
			if servicesFirewallRuleIsServiceOwned(rule) {
				serviceOwned = true
				continue
			}
			kept = append(kept, rule)
		}

		if !serviceOwned && len(desired) == 0 {
			continue
		}

		updated := policy.DeepCopy()
		updated.Spec.Ingress = append(kept, desired...)
		if k8sequality.Semantic.DeepEqual(updated.Spec.Ingress, policy.Spec.Ingress) {
			continue
		}

		if _, updateErr := policies.Update(ctx, updated, k8smeta.UpdateOptions{}); updateErr != nil {
			log.Warn("services: failed to rewrite firewall rules of %s: %s", policy.Name, updateErr)
		}
	}
}

func servicesMemberFirewallRule(service orc.Service) networking.NetworkPolicyIngressRule {
	networkId := service.Specification.InternalEndpoint.Value.PrivateNetworkId

	var ports []networking.NetworkPolicyPort
	for _, port := range service.Specification.Ports {
		protocol := k8score.ProtocolTCP
		if port.Protocol == orc.ServicePortProtocolUdp {
			protocol = k8score.ProtocolUDP
		}

		ports = append(ports, networking.NetworkPolicyPort{
			Protocol: util.Pointer(protocol),
			Port:     &intstr.IntOrString{Type: intstr.Int, IntVal: int32(port.Port)},
		})
	}

	return networking.NetworkPolicyIngressRule{
		Ports: ports,
		From: []networking.NetworkPolicyPeer{{
			PodSelector: &k8smeta.LabelSelector{
				MatchLabels: map[string]string{
					shared.PrivateNetworkMembershipLabel(networkId): "true",
				},
			},
		}},
	}
}

func servicesFirewallRuleIsServiceOwned(rule networking.NetworkPolicyIngressRule) bool {
	if len(rule.Ports) == 0 {
		return false
	}

	for _, peer := range rule.From {
		if peer.PodSelector == nil {
			continue
		}

		for label, value := range peer.PodSelector.MatchLabels {
			if value == "true" && strings.HasPrefix(label, shared.PrivateNetworkMembershipLabelPrefix) {
				return true
			}
		}
	}
	return false
}

func servicesEnsureEndpointSlice(
	ctx context.Context,
	service orc.Service,
	port orc.ServicePort,
	members []serviceBackend,
	backends []orc.ServiceBackendStatus,
) {
	name := ServiceK8sName(service.Id) + "-" + port.Name
	namespace := sharedNamespace()
	client := shared.K8sClient.DiscoveryV1().EndpointSlices(namespace)

	protocol := k8score.ProtocolTCP
	if port.Protocol == orc.ServicePortProtocolUdp {
		protocol = k8score.ProtocolUDP
	}

	var endpoints []discovery.Endpoint
	for _, member := range members {
		if member.Ip == "" || member.Draining {
			continue
		}
		if !servicesBackendHealthy(backends, member.JobId, member.Rank, port.Name) {
			continue
		}

		endpoints = append(endpoints, discovery.Endpoint{
			Addresses: []string{member.Ip},
			Conditions: discovery.EndpointConditions{
				Ready: util.BoolPointer(true),
			},
		})
	}

	desiredPorts := []discovery.EndpointPort{{
		Port:     util.Pointer(int32(port.Port)),
		Protocol: &protocol,
		Name:     util.Pointer(port.Name),
	}}

	existing, ok := shared.ComputeEndpointSlices.Retrieve(name)
	if !ok {
		if len(endpoints) == 0 {
			return
		}
		created := &discovery.EndpointSlice{
			ObjectMeta: k8smeta.ObjectMeta{
				Name: name,
				Labels: util.MapMerge(servicesObjectLabels(service.Id), map[string]string{
					discovery.LabelServiceName: ServiceK8sName(service.Id),
				}),
			},
			AddressType: discovery.AddressTypeIPv4,
			Ports:       desiredPorts,
			Endpoints:   endpoints,
		}
		if _, createErr := client.Create(ctx, created, k8smeta.CreateOptions{}); createErr != nil {
			log.Warn("services: failed to create endpoint slice %s: %s", name, createErr)
		}
		return
	}

	updated := existing.DeepCopy()
	updated.Labels = util.MapMerge(servicesObjectLabels(service.Id), map[string]string{
		discovery.LabelServiceName: ServiceK8sName(service.Id),
	})
	updated.AddressType = discovery.AddressTypeIPv4
	updated.Ports = desiredPorts
	updated.Endpoints = endpoints
	if len(endpoints) == 0 {
		updated.Endpoints = []discovery.Endpoint{}
	}
	if _, updateErr := client.Update(ctx, updated, k8smeta.UpdateOptions{}); updateErr != nil {
		log.Warn("services: failed to update endpoint slice %s: %s", name, updateErr)
	}
}

func servicesBackendHealthy(backends []orc.ServiceBackendStatus, jobId string, rank int, portName string) bool {
	for _, backend := range backends {
		if backend.JobId == jobId && backend.Rank == rank {
			for _, port := range backend.Ports {
				if port.Name == portName {
					return port.State == orc.ServiceBackendPortStateHealthy
				}
			}
		}
	}
	return false
}

func servicesPushCluster(
	service orc.Service,
	port orc.ServicePort,
	members []serviceBackend,
	backends []orc.ServiceBackendStatus,
) {
	cluster := &gw.EnvoyCluster{
		Name: serviceClusterName(service.Id, port.Name),
	}

	cluster.BackendTlsEnabled = strings.EqualFold(port.ApplicationProtocol.GetOrDefault(""), "HTTPS") ||
		port.BackendTls.Present

	if port.BackendTls.Present {
		cluster.BackendTlsInsecureSkipVerify = port.BackendTls.Value.InsecureSkipVerify
		cluster.BackendTlsServerName = port.BackendTls.Value.ServerName
		cluster.BackendTlsTrustBundle = port.BackendTls.Value.TrustBundlePem.GetOrDefault("")
	}

	if shared.K8sInCluster {
		check := orc.ServiceHealthCheck{
			Type:               orc.ServiceHealthCheckTypeTcp,
			IntervalSeconds:    5,
			TimeoutSeconds:     2,
			HealthyThreshold:   2,
			UnhealthyThreshold: 2,
		}
		if port.HealthCheck.Present {
			check = port.HealthCheck.Value
		}

		cluster.HealthCheck = &gw.EnvoyHealthCheck{
			Protocol:           string(check.Type),
			Path:               check.Path,
			Host:               cluster.BackendTlsServerName,
			Port:               check.Port.GetOrDefault(0),
			IntervalSeconds:    check.IntervalSeconds,
			TimeoutSeconds:     check.TimeoutSeconds,
			HealthyThreshold:   check.HealthyThreshold,
			UnhealthyThreshold: check.UnhealthyThreshold,
		}
	}

	for _, member := range members {
		if !shared.K8sInCluster {
			if member.Ip == "" || member.Draining {
				continue
			}
			if !servicesBackendHealthy(backends, member.JobId, member.Rank, port.Name) {
				continue
			}
			cluster.Endpoints = append(cluster.Endpoints, gw.EnvoyEndpoint{
				Address: "127.0.0.1",
				Port:    shared.EstablishTunnel(member.PodName, port.Port),
			})
			continue
		}

		if member.Ip == "" {
			continue
		}

		if member.Draining || servicesBackendHealthy(backends, member.JobId, member.Rank, port.Name) {
			cluster.Endpoints = append(cluster.Endpoints, gw.EnvoyEndpoint{
				Address:  member.Ip,
				Port:     port.Port,
				Draining: member.Draining,
			})
		}
	}

	gw.SendMessage(gw.ConfigurationMessage{ClusterUp: cluster})
}

func servicesPruneOrphans(ctx context.Context, ids map[string]util.Empty) {
	namespace := sharedNamespace()

	for _, item := range shared.ComputeServices.List() {
		if item.Labels[servicesManagedByLabel] != servicesManagedBy {
			continue
		}
		if _, keep := ids[item.Labels[servicesIdLabel]]; !keep {
			_ = shared.K8sClient.CoreV1().Services(namespace).Delete(ctx, item.Name, k8smeta.DeleteOptions{})
		}
	}

	for _, item := range shared.ComputeEndpointSlices.List() {
		if item.Labels[servicesManagedByLabel] != servicesManagedBy {
			continue
		}
		if _, keep := ids[item.Labels[servicesIdLabel]]; !keep {
			_ = shared.K8sClient.DiscoveryV1().EndpointSlices(namespace).Delete(ctx, item.Name, k8smeta.DeleteOptions{})
		}
	}

	servicesStateMutex.Lock()
	for serviceId, clusterNames := range servicesLastClusters {
		if _, keep := ids[serviceId]; !keep {
			for _, clusterName := range clusterNames {
				gw.SendMessage(gw.ConfigurationMessage{
					ClusterDown: &gw.EnvoyCluster{Name: clusterName},
				})
			}
			delete(servicesLastClusters, serviceId)
			delete(servicesDrains, serviceId)
			delete(servicesLastReport, serviceId)
			delete(servicesProbeState, serviceId)
		}
	}
	servicesStateMutex.Unlock()
}

var servicesLastRoutes = map[string]*gw.EnvoyRoute{}

func serviceReconcileLinkTargets() {
	servicesStateMutex.Lock()
	defer servicesStateMutex.Unlock()

	desired := map[string]*gw.EnvoyRoute{}

	for _, ingress := range controller.LinkRetrieveAll() {
		if !ingress.Specification.Target.Present {
			continue
		}

		target := ingress.Specification.Target.Value
		service, ok := controller.ServiceRetrieveTracked(target.ServiceId)
		if !ok {
			continue
		}

		for _, port := range service.Specification.Ports {
			if port.Name != target.Port {
				continue
			}

			route := &gw.EnvoyRoute{
				Cluster:      serviceClusterName(service.Id, port.Name),
				CustomDomain: ingress.Specification.Domain,
				Type:         gw.RouteTypeIngress,
			}
			desired[routeKey(route)] = route
		}
	}

	for key, route := range servicesLastRoutes {
		if _, keep := desired[key]; !keep {
			gw.SendMessage(gw.ConfigurationMessage{
				RouteDown: route,
			})
		}
	}
	servicesLastRoutes = desired

	for _, route := range desired {
		gw.SendMessage(gw.ConfigurationMessage{RouteUp: route})
	}
}

func routeKey(route *gw.EnvoyRoute) string {
	return route.Cluster + "|" + route.CustomDomain
}
