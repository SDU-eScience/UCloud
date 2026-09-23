package shared

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	k8score "k8s.io/api/core/v1"
	k8snetwork "k8s.io/api/networking/v1"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/controller"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	PrivateNetworkManagedByLabel          = "ucloud.dk/managed-by"
	PrivateNetworkIdLabel                 = "ucloud.dk/private-network-id"
	PrivateNetworkManagedBy               = "im"
	PrivateNetworkMultusAnnotation        = "k8s.v1.cni.cncf.io/networks"
	PrivateNetworkNetworkStatusAnnotation = "k8s.v1.cni.cncf.io/network-status"

	privateNetworkSubnetSuffix = "-net"
)

func PrivateNetworkSelector(subdomain string) map[string]string {
	return map[string]string{
		PrivateNetworkLabel(subdomain): "true",
	}
}

func PrivateNetworkLabel(subdomain string) string {
	return fmt.Sprintf("ucloud.dk/network-%s", subdomain)
}

func PrivateNetworkMembershipLabel(networkId string) string {
	return fmt.Sprintf("ucloud.dk/private-network-%s", networkId)
}

func PrivateNetworkMemberSelector(networkId string) map[string]string {
	return map[string]string{
		PrivateNetworkMembershipLabel(networkId): "true",
	}
}

func PrivateNetworkName(subdomain string) string {
	return subdomain
}

func PrivateNetworkSubnetName(subdomain string, resourceId string) string {
	preferred := subdomain + privateNetworkSubnetSuffix
	if len(preferred) <= 63 {
		return preferred
	}

	suffix := "-" + resourceId + privateNetworkSubnetSuffix
	prefixLength := 63 - len(suffix)
	if prefixLength > len(subdomain) {
		prefixLength = len(subdomain)
	}
	if prefixLength < 0 {
		prefixLength = 0
	}
	return subdomain[:prefixLength] + suffix
}

func PrivateNetworkProviderName(subdomain string) string {
	return fmt.Sprintf("%s.%s.ovn", subdomain, ServiceConfig.Compute.Namespace)
}

func PrivateNetworkIpAnnotation(provider string) string {
	return provider + ".kubernetes.io/ip_address"
}

func PrivateNetworkMacAnnotation(provider string) string {
	return provider + ".kubernetes.io/mac_address"
}

func PrivateNetworkRetrieveProducts() []orc.PrivateNetworkSupport {
	return PrivateNetworkSupport
}

func PrivateNetworkCreate(network *orc.PrivateNetwork) *util.HttpError {
	if network == nil {
		return util.ServerHttpError("Failed to create private network: network is nil")
	}

	if !controller.PrivateNetworksFeatureEnabled() {
		return util.UserHttpError("Private networks are not supported by this provider")
	}

	baseSubdomain := orc.PrivateNetworkSubdomainFromName(network.Specification.Name)
	assignedSubdomain := network.Status.Subdomain != ""
	if snapshot, found := controller.PrivateNetworkSnapshotRetrieve(network.Id); found && snapshot.Subdomain != "" {
		network.Status.Subdomain = snapshot.Subdomain
		assignedSubdomain = true
	}

	allocated := false
	for attempt := 0; attempt < 16; attempt++ {
		if network.Status.Subdomain == "" {
			network.Status.Subdomain = baseSubdomain
			if attempt > 0 {
				var err error
				network.Status.Subdomain, err = orc.PrivateNetworkSubdomainAddRandomSuffix(baseSubdomain)
				if err != nil {
					return util.ServerHttpError("failed to generate a private network subdomain")
				}
			}
		}

		preflightErr := privateNetworkPreflightNameCollisions(network)
		if preflightErr != nil {
			if assignedSubdomain || preflightErr.StatusCode != http.StatusConflict {
				return preflightErr
			}
			network.Status.Subdomain = ""
			continue
		}

		subdomainConflict, err := controller.PrivateNetworkCreateAllocate(network)
		if err != nil {
			return err
		}
		if subdomainConflict {
			if assignedSubdomain {
				return util.HttpErr(http.StatusConflict, "a network with this subdomain already exists")
			}
			network.Status.Subdomain = ""
			continue
		}

		allocated = true
		break
	}

	if !allocated {
		return util.HttpErr(http.StatusConflict, "unable to allocate a unique private network subdomain")
	}

	_, err := orc.PrivateNetworksControlAddUpdate.Invoke(
		fnd.BulkRequestOf(orc.ResourceUpdateAndId[orc.PrivateNetworkUpdate]{
			Id: network.Id,
			Update: orc.PrivateNetworkUpdate{
				Subdomain: util.OptValue(network.Status.Subdomain),
				Timestamp: fnd.Timestamp(time.Now()),
			},
		}),
	)
	if err != nil {
		return err
	}

	PrivateNetworkReconcileSoon()
	return nil
}

func privateNetworkPreflightNameCollisions(network *orc.PrivateNetwork) *util.HttpError {
	if privateNetworkDynamicClient == nil {
		return nil
	}

	subnetName := PrivateNetworkSubnetName(network.Status.Subdomain, network.Id)

	vpc, err := privateNetworkDynamicClient.Resource(privateNetworkVpcGvr).
		Get(context.Background(), network.Status.Subdomain, k8smeta.GetOptions{})
	if err == nil {
		typedVpc := &privateNetworkKubeOvnVpc{}
		owned := privateNetworkKubeOvnFromUnstructured("Vpc", vpc, typedVpc) &&
			privateNetworkObjectOwnedBy(typedVpc, network.Id)
		if !owned {
			return util.HttpErr(
				http.StatusConflict,
				"A private network with this subdomain already exists, try a different one",
			)
		}
	}

	subnet, err := privateNetworkDynamicClient.Resource(privateNetworkSubnetGvr).
		Get(context.Background(), subnetName, k8smeta.GetOptions{})
	if err == nil {
		typedSubnet := &privateNetworkKubeOvnSubnet{}
		owned := privateNetworkKubeOvnFromUnstructured("Subnet", subnet, typedSubnet) &&
			privateNetworkObjectOwnedBy(typedSubnet, network.Id)
		if !owned {
			return util.HttpErr(
				http.StatusConflict,
				"A private network with this subdomain already exists, try a different one",
			)
		}
	}

	nad, err := privateNetworkNadClient.Get(
		context.Background(),
		network.Status.Subdomain,
		k8smeta.GetOptions{},
	)
	if err == nil && !privateNetworkObjectOwnedBy(nad, network.Id) {
		return util.HttpErr(
			http.StatusConflict,
			"A private network with this subdomain already exists, try a different one",
		)
	}

	return nil
}

func PrivateNetworkDelete(network *orc.PrivateNetwork) *util.HttpError {
	if network == nil {
		return util.ServerHttpError("Failed to delete private network: network is nil")
	}

	if len(network.Status.Members) > 0 {
		return util.UserHttpError(
			"This private network is currently in use by job: %v",
			strings.Join(network.Status.Members, ", "),
		)
	}

	featureEnabled := controller.PrivateNetworksFeatureEnabled()
	snapshot, tracked := controller.PrivateNetworkSnapshotRetrieve(network.Id)

	legacy := !tracked || !snapshot.CidrBlock.Present
	if !featureEnabled && tracked && snapshot.CidrBlock.Present {
		return util.UserHttpError(
			"This private network cannot be deleted while private networks are disabled on this provider",
		)
	}

	if !tracked {
		return nil
	}

	if err := controller.PrivateNetworkDeleteRequest(network); err != nil {
		return err
	}

	if legacy && !featureEnabled {
		ctx := context.Background()
		if !privateNetworkDeleteServiceObject(ctx, network.Status.Subdomain, network.Id, true) {
			return util.ServerHttpError("unable to delete the service of the private network")
		}

		if !privateNetworkDeletePolicyObject(ctx, network.Status.Subdomain, network.Id, true) {
			return util.ServerHttpError("unable to delete the network policy of the private network")
		}

		workspace := controller.PrivateNetworkWorkspaceFromOwner(network.Owner)
		return controller.PrivateNetworkFinishDelete(network.Id, workspace)
	}

	return nil
}

func privateNetworkServiceIsLegacy(svc *k8score.Service, subdomain string) bool {
	if svc.Labels[PrivateNetworkManagedByLabel] == PrivateNetworkManagedBy {
		return false
	}

	return svc.Spec.Selector[PrivateNetworkLabel(subdomain)] == "true"
}

func privateNetworkPolicyIsLegacy(policy *k8snetwork.NetworkPolicy, subdomain string) bool {
	if policy.Labels[PrivateNetworkManagedByLabel] == PrivateNetworkManagedBy {
		return false
	}

	match, ok := policy.Spec.PodSelector.MatchLabels[PrivateNetworkLabel(subdomain)]
	return ok && match == "true"
}

type PrivateNetworkDnsConfig struct {
	Hostname  string
	Subdomain string
	PodDns    *k8score.PodDNSConfig
	Labels    map[string]string
}

func PrivateNetworkCreateDnsConfig(job *orc.Job) (PrivateNetworkDnsConfig, *util.HttpError) {
	result := PrivateNetworkDnsConfig{}
	result.Labels = map[string]string{}
	result.Hostname = job.Specification.Hostname.GetOrDefault(fmt.Sprintf("j-%v", job.Id))

	values, err := controller.PrivateNetworkJobValues(job)
	if err != nil {
		return result, err
	}

	type attachedNetwork struct {
		id        string
		subdomain string
		allocated bool
	}

	var networks []attachedNetwork
	for _, value := range values {
		network, ok := controller.PrivateNetworkRetrieve(value.Id)
		if !ok {
			continue
		}
		networks = append(networks, attachedNetwork{
			id:        network.Id,
			subdomain: network.Status.Subdomain,
			allocated: network.Status.CidrBlock.Present,
		})
	}

	toolBackend := job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value.Description.Backend

	if len(networks) > 0 {
		result.Subdomain = networks[0].subdomain
		result.PodDns = &k8score.PodDNSConfig{}

		baseDomain := fmt.Sprintf("%s.svc.cluster.local", ServiceConfig.Compute.Namespace)

		for _, network := range networks {
			result.PodDns.Searches = append(result.PodDns.Searches, fmt.Sprintf("%s.%s", network.subdomain, baseDomain))
			if network.allocated {
				result.Labels[PrivateNetworkMembershipLabel(network.id)] = "true"
			} else {
				result.Labels[PrivateNetworkLabel(network.subdomain)] = "true"
			}
		}

		result.PodDns.Searches = append(result.PodDns.Searches, baseDomain)
		result.PodDns.Searches = append(result.PodDns.Searches, "svc.cluster.local")
		result.PodDns.Searches = append(result.PodDns.Searches, "cluster.local")
	} else if toolBackend == orc.ToolBackendVirtualMachine {
		result.Subdomain = fmt.Sprintf("j-%v", job.Id)
		result.PodDns = &k8score.PodDNSConfig{}
		baseDomain := fmt.Sprintf("%s.svc.cluster.local", ServiceConfig.Compute.Namespace)

		result.PodDns.Searches = append(result.PodDns.Searches, fmt.Sprintf("%s.%s", result.Subdomain, baseDomain))
		result.PodDns.Searches = append(result.PodDns.Searches, baseDomain)
		result.PodDns.Searches = append(result.PodDns.Searches, "svc.cluster.local")
		result.PodDns.Searches = append(result.PodDns.Searches, "cluster.local")
	}

	return result, nil
}

type PrivateNetworkAttachmentInfo struct {
	NetworkId   string
	Subdomain   string
	CidrBlock   util.Option[string]
	Ip          string
	MacAddress  util.Option[string]
	Interface   string
	NadName     string
	Provider    string
	NetworkRank int
}

func PrivateNetworkAttachmentInfosValidated(all []controller.PrivateNetworkJobLeases, rank int) ([]PrivateNetworkAttachmentInfo, *util.HttpError) {
	var result []PrivateNetworkAttachmentInfo
	for index, entry := range all {
		var match *controller.PrivateNetworkLeaseRow
		for i := range entry.Leases {
			if entry.Leases[i].Rank == rank {
				match = &entry.Leases[i]
				break
			}
		}
		if match == nil {
			return nil, util.ServerHttpError(
				"The private network %s has no address for rank %d of the job",
				entry.NetworkId,
				rank,
			)
		}

		result = append(result, PrivateNetworkAttachmentInfo{
			NetworkId:   entry.NetworkId,
			Subdomain:   entry.Subdomain,
			CidrBlock:   entry.CidrBlock,
			Ip:          match.Ip,
			MacAddress:  match.MacAddress,
			Interface:   fmt.Sprintf("net%d", index+1),
			NadName:     entry.Subdomain,
			Provider:    PrivateNetworkProviderName(entry.Subdomain),
			NetworkRank: index,
		})
	}
	return result, nil
}

func PrivateNetworkAttachmentAnnotations(attachments []PrivateNetworkAttachmentInfo) (map[string]string, *util.HttpError) {
	if len(attachments) == 0 {
		return nil, nil
	}

	type multusNetwork struct {
		Name      string `json:"name"`
		Namespace string `json:"namespace"`
		Interface string `json:"interface"`
	}

	entries := make([]multusNetwork, 0, len(attachments))
	for _, attachment := range attachments {
		entries = append(entries, multusNetwork{
			Name:      attachment.NadName,
			Namespace: ServiceConfig.Compute.Namespace,
			Interface: attachment.Interface,
		})
	}

	jsonified, err := json.Marshal(entries)
	if err != nil {
		return nil, util.HttpErrorFromErr(err)
	}

	annotations := map[string]string{
		PrivateNetworkMultusAnnotation: string(jsonified),
	}

	for _, attachment := range attachments {
		annotations[PrivateNetworkIpAnnotation(attachment.Provider)] = attachment.Ip
		if attachment.MacAddress.Present {
			annotations[PrivateNetworkMacAnnotation(attachment.Provider)] = attachment.MacAddress.Value
		}
	}

	return annotations, nil
}

func PrivateNetworkJobAttachments(job *orc.Job, rank int) ([]PrivateNetworkAttachmentInfo, *util.HttpError) {
	if !controller.PrivateNetworksFeatureEnabled() {
		return nil, nil
	}

	values, err := controller.PrivateNetworkJobValues(job)
	if err != nil {
		return nil, err
	}

	if len(values) > 0 && privateNetworkJobIsLegacyOnly(values) {
		return nil, nil
	}

	leases, err := controller.PrivateNetworkJobAllocateLeases(job)
	if err != nil {
		return nil, err
	}

	return PrivateNetworkAttachmentInfosValidated(leases, rank)
}

func privateNetworkJobIsLegacyOnly(values []orc.AppParameterValue) bool {
	for _, value := range values {
		network, ok := controller.PrivateNetworkRetrieve(value.Id)
		if !ok || network.Status.CidrBlock.Present {
			return false
		}
	}
	return true
}

func PrivateNetworkAttachmentEnvironmentVariables(attachments []PrivateNetworkAttachmentInfo) map[string]string {
	if len(attachments) == 0 {
		return nil
	}

	result := map[string]string{}
	for _, attachment := range attachments {
		name := strings.ToUpper(strings.ReplaceAll(attachment.Subdomain, "-", "_"))
		result["UCLOUD_PRIVATE_NET_"+name] = attachment.Ip
	}
	return result
}
