package shared

import (
	"context"
	"fmt"
	"net/http"
	"sync"

	k8score "k8s.io/api/core/v1"
	k8snetwork "k8s.io/api/networking/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	"ucloud.dk/pkg/controller"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var privateNetworkCreationMutex = sync.Mutex{}

func PrivateNetworkSelector(subdomain string) map[string]string {
	return map[string]string{
		PrivateNetworkLabel(subdomain): "true",
	}
}

func PrivateNetworkLabel(subdomain string) string {
	return fmt.Sprintf("ucloud.dk/network-%s", subdomain)
}

func PrivateNetworkName(subdomain string) string {
	return fmt.Sprintf("network-%s", subdomain)
}

func PrivateNetworkRetrieveProducts() []orc.PrivateNetworkSupport {
	return PrivateNetworkSupport
}

func PrivateNetworkCreate(network *orc.PrivateNetwork) *util.HttpError {
	if network == nil {
		return util.ServerHttpError("Failed to create private network: network is nil")
	}

	privateNetworkCreationMutex.Lock()
	networkCount := db.NewTx(func(tx *db.Transaction) int {
		row, _ := db.Get[struct {
			Count int
		}](
			tx,
			`
				select count(*) as count
				from tracked_private_networks 
				where lower(resource->'specification'->>'subdomain') = lower(:domain)
		    `,
			db.Params{
				"domain": network.Specification.Subdomain,
			},
		)

		return row.Count
	})
	privateNetworkCreationMutex.Unlock()

	if networkCount != 1 {
		return util.HttpErr(http.StatusConflict, "a network with this subdomain already exists, try a different one")
	}

	networkSvc := &k8score.Service{
		ObjectMeta: k8smeta.ObjectMeta{
			Name: PrivateNetworkName(network.Specification.Subdomain),
		},
		Spec: k8score.ServiceSpec{
			ClusterIP: "None",
			Selector:  PrivateNetworkSelector(network.Specification.Subdomain),
			Ports: []k8score.ServicePort{
				{
					Name:       "dummy",
					Port:       80,
					TargetPort: intstr.FromInt32(80),
				},
			},
		},
	}

	svc, err := K8sClient.CoreV1().Services(ServiceConfig.Compute.Namespace).
		Create(context.Background(), networkSvc, k8smeta.CreateOptions{})

	if err != nil {
		log.Warn("Failed to create private network: %s %s", network.Specification.Subdomain, err)
		return util.HttpErr(http.StatusInternalServerError, "unable to create a private network")
	}

	selector := k8smeta.LabelSelector{
		MatchLabels: PrivateNetworkSelector(network.Specification.Subdomain),
	}

	policy := &k8snetwork.NetworkPolicy{
		ObjectMeta: k8smeta.ObjectMeta{
			Name: PrivateNetworkName(network.Specification.Subdomain),
			OwnerReferences: []k8smeta.OwnerReference{{
				APIVersion: "v1",
				Kind:       "Service",
				Name:       svc.Name,
				UID:        svc.UID,
			}},
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

	_, err = K8sClient.NetworkingV1().NetworkPolicies(ServiceConfig.Compute.Namespace).
		Create(context.Background(), policy, k8smeta.CreateOptions{})

	if err != nil {
		log.Warn("Failed to create private network policy: %s %s", network.Specification.Subdomain, err)
		return util.HttpErr(http.StatusInternalServerError, "unable to create a private network policy")
	}

	return nil
}

func PrivateNetworkDelete(network *orc.PrivateNetwork) *util.HttpError {
	if network == nil {
		return util.ServerHttpError("Failed to delete private network: network is nil")
	}

	err := K8sClient.CoreV1().Services(ServiceConfig.Compute.Namespace).
		Delete(context.Background(), PrivateNetworkName(network.Specification.Subdomain), k8smeta.DeleteOptions{})

	if err != nil && !k8serrors.IsNotFound(err) {
		log.Warn("Failed to delete a private network: %s %s", network.Specification.Subdomain, err)
		return util.HttpErr(http.StatusInternalServerError, "unable to delete a private network")
	}

	return controller.PrivateNetworkDelete(network)
}
