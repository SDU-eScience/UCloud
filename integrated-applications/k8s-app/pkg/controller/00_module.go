package controller

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"slices"
	"strings"
	"sync"
	"time"

	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/cache"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const (
	controllerTokenPath  = "/etc/ucloud-k8s/controller/token"
	providerHostnamePath = "/opt/ucloud/provider-hostname.txt"
	reconcileInterval    = 5 * time.Minute
	productCacheInterval = 1 * time.Hour
	domainFailureBackoff = 5 * time.Minute
)

var linkRegex = regexp.MustCompile(`^[a-z][a-z0-9-]{3,61}[a-z0-9]$`)

func Launch() {
	tokenBytes, err := os.ReadFile(controllerTokenPath)
	if err != nil {
		log.Fatal("k8s controller: could not read the registration token: %s", err)
	}

	token := strings.TrimSpace(string(tokenBytes))
	if token == "" {
		log.Fatal("k8s controller: the registration token is empty")
	}

	hostBytes, err := os.ReadFile(providerHostnamePath)
	if err != nil {
		log.Fatal("k8s controller: could not read the provider hostname: %s", err)
	}

	host := strings.TrimSpace(string(hostBytes))
	if host == "" {
		log.Fatal("k8s controller: the provider hostname is empty")
	}

	client := &rpc.Client{
		BasePath: fmt.Sprintf("http://%s:42000", host),
		Client:   &http.Client{Timeout: 30 * time.Second},
	}

	config, err := rest.InClusterConfig()
	if err != nil {
		log.Fatal("k8s controller: could not load the in-cluster configuration: %s", err)
	}
	config.QPS = 10
	config.Burst = 20

	k8s, err := kubernetes.NewForConfig(config)
	if err != nil {
		log.Fatal("k8s controller: could not create the kubernetes client: %s", err)
	}

	ctx := context.Background()

	informer := informers.NewSharedInformerFactory(k8s, 30*time.Minute)
	ingressInformer := informer.Networking().V1().Ingresses().Informer()
	ingressInformer.AddEventHandler(cache.ResourceEventHandlerFuncs{
		AddFunc:    func(any) { triggerReconcile() },
		UpdateFunc: func(any, any) { triggerReconcile() },
		DeleteFunc: func(any) {},
	})

	informer.Start(ctx.Done())

	for {
		reconcile(ctx, k8s, client, token)

		select {
		case <-reconcileNow:
		case <-time.After(reconcileInterval):
		}
	}
}

var reconcileNow = make(chan util.Empty, 1)

func triggerReconcile() {
	select {
	case reconcileNow <- util.Empty{}:
	default:
	}
}

var domainFailures map[string]time.Time

type productCacheEntry[Support any] struct {
	sync.Mutex
	Support     []Support
	RefreshedAt time.Time
}

var ingressProductCache productCacheEntry[orcapi.IngressSupport]
var serviceProductCache productCacheEntry[orcapi.ServiceSupport]

func cachedProducts[Support any](
	cache *productCacheEntry[Support],
	fetch func() ([]Support, *util.HttpError),
) ([]Support, bool) {
	cache.Lock()
	defer cache.Unlock()

	if time.Since(cache.RefreshedAt) < productCacheInterval {
		return cache.Support, true
	}

	support, herr := fetch()
	if herr != nil {
		return nil, false
	}

	cache.Support = support
	cache.RefreshedAt = time.Now()
	return support, true
}

const ingressServiceName = "k8s-ingress"

func reconcile(ctx context.Context, k8s *kubernetes.Clientset, client *rpc.Client, token string) {
	auth := ucxapi.StackGrantAuth{Token: token}

	support, ok := cachedProducts(&ingressProductCache, func() ([]orcapi.IngressSupport, *util.HttpError) {
		return ucxapi.StackGrantIngressProducts.InvokeEx(client, ucxapi.StackGrantIngressProductsRequest{
			StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
		}, rpc.InvokeOpts{})
	})
	if !ok {
		log.Warn("k8s controller: could not retrieve the public link products")
		return
	}

	if len(support) == 0 {
		log.Warn("k8s controller: the provider has no public link products")
		return
	}

	owned, herr := ucxapi.StackGrantBrowseIngresses.InvokeEx(client, ucxapi.StackGrantBrowseIngressesRequest{
		StackGrantAuth: auth,
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not browse the owned public links: %s", herr)
		return
	}

	ownedDomains := map[string]util.Empty{}
	for _, ingress := range owned {
		ownedDomains[ingress.Specification.Domain] = util.Empty{}
	}

	hosts, err := collectIngressHosts(ctx, k8s)
	if err != nil {
		log.Warn("k8s controller: could not list the cluster ingresses: %s", err)
		return
	}

	service, serviceOk := ensureIngressService(client, token)
	if !serviceOk {
		return
	}

	syncServiceMembers(client, token, service)

	now := time.Now()
	nextFailures := map[string]time.Time{}
	for _, host := range hosts {
		domain := strings.ToLower(host)

		matched := -1
		for i := range support {
			if eligibleDomain(support[i], domain) {
				matched = i
				break
			}
		}
		if matched == -1 {
			continue
		}

		if _, exists := ownedDomains[domain]; exists {
			continue
		}

		if failureAt, failed := domainFailures[domain]; failed && now.Before(failureAt.Add(domainFailureBackoff)) {
			nextFailures[domain] = failureAt
			continue
		}

		_, herr := ucxapi.StackGrantCreateIngress.InvokeEx(client, ucxapi.StackGrantCreateRequest[orcapi.IngressSpecification]{
			StackGrantAuth: auth,
			Items: []orcapi.IngressSpecification{{
				Domain: domain,
				Target: util.OptValue(orcapi.PublicLinkServiceTarget{
					ServiceId: service.Id,
					Port:      "http",
				}),
				ResourceSpecification: orcapi.ResourceSpecification{
					Product: support[matched].Product,
				},
			}},
		}, rpc.InvokeOpts{})
		if herr != nil {
			log.Warn("k8s controller: could not create the public link for %s: %s", domain, herr)
			nextFailures[domain] = now
			continue
		}

		log.Info("k8s controller: created the public link for %s", domain)
	}

	domainFailures = nextFailures

	hostSet := map[string]util.Empty{}
	for _, host := range hosts {
		hostSet[strings.ToLower(host)] = util.Empty{}
	}

	collectOrphanedLinks(client, token, service, owned, hostSet)
}

func ensureIngressService(client *rpc.Client, token string) (orcapi.Service, bool) {
	services, herr := ucxapi.StackGrantBrowseServices.InvokeEx(client, ucxapi.StackGrantBrowseServicesRequest{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not browse the stack services: %s", herr)
		return orcapi.Service{}, false
	}

	for _, service := range services {
		if service.Specification.Name == ingressServiceName {
			return service, true
		}
	}

	products, ok := cachedProducts(&serviceProductCache, func() ([]orcapi.ServiceSupport, *util.HttpError) {
		return ucxapi.StackGrantServiceProducts.InvokeEx(client, ucxapi.StackGrantServiceProductsRequest{
			StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
		}, rpc.InvokeOpts{})
	})
	if !ok {
		log.Warn("k8s controller: could not retrieve the service products")
		return orcapi.Service{}, false
	}

	if len(products) == 0 {
		log.Warn("k8s controller: the provider has no service products")
		return orcapi.Service{}, false
	}

	networkId := ""
	for _, service := range services {
		if endpoint := service.Specification.InternalEndpoint; endpoint.Present {
			networkId = endpoint.Value.PrivateNetworkId
			break
		}
	}

	if networkId == "" {
		log.Warn("k8s controller: could not determine the private network of the stack")
		return orcapi.Service{}, false
	}

	spec := orcapi.ServiceSpecification{
		Name: ingressServiceName,
		Ports: []orcapi.ServicePort{{
			Name:                "http",
			Port:                shared.IngressNodePort,
			Protocol:            orcapi.ServicePortProtocolTcp,
			ApplicationProtocol: util.OptValue("HTTP"),
			HealthCheck: util.OptValue(orcapi.ServiceHealthCheck{
				Type:               orcapi.ServiceHealthCheckTypeTcp,
				IntervalSeconds:    5,
				TimeoutSeconds:     2,
				HealthyThreshold:   2,
				UnhealthyThreshold: 2,
			}),
		}},
		InternalEndpoint: util.OptValue(orcapi.ServiceInternalEndpointSpec{
			PrivateNetworkId: networkId,
		}),
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: products[0].Product,
		},
	}

	response, herr := ucxapi.StackGrantCreateService.InvokeEx(client, ucxapi.StackGrantCreateRequest[orcapi.ServiceSpecification]{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
		Items:          []orcapi.ServiceSpecification{spec},
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not create the ingress service: %s", herr)
		return orcapi.Service{}, false
	}

	if len(response.Responses) == 0 {
		log.Warn("k8s controller: the ingress service creation returned no id")
		return orcapi.Service{}, false
	}

	return orcapi.Service{Resource: orcapi.Resource{Id: response.Responses[0].Id}}, true
}

func syncServiceMembers(client *rpc.Client, token string, service orcapi.Service) {
	jobs, herr := ucxapi.StackGrantBrowseJobs.InvokeEx(client, ucxapi.StackGrantBrowseJobsRequest{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not browse the stack jobs: %s", herr)
		return
	}

	desired := []string{}
	for _, job := range jobs {
		isControlPlane := job.Specification.Labels[shared.StackGroupingLabel] == shared.GroupControlPlane
		if isControlPlane && !job.Status.State.IsFinal() {
			if !slices.Contains(desired, job.Id) {
				desired = append(desired, job.Id)
			}
		}
	}

	adding := []string{}
	for _, jobId := range desired {
		if !slices.Contains(service.Status.Members, jobId) {
			adding = append(adding, jobId)
		}
	}

	removing := []string{}
	for _, jobId := range service.Status.Members {
		if !slices.Contains(desired, jobId) {
			removing = append(removing, jobId)
		}
	}

	if len(adding) == 0 && len(removing) == 0 {
		return
	}

	_, herr = ucxapi.StackGrantServiceUpdateMembers.InvokeEx(client, ucxapi.StackGrantServiceUpdateMembersRequest{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
		Id:             service.Id,
		AddedJobIds:    adding,
		RemovedJobIds:  removing,
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not update the ingress service members: %s", herr)
		return
	}

	log.Info("k8s controller: updated the ingress service members (+%d/-%d)", len(adding), len(removing))
}

func collectOrphanedLinks(
	client *rpc.Client,
	token string,
	service orcapi.Service,
	owned []orcapi.Ingress,
	hostSet map[string]util.Empty,
) {
	orphaned := []string{}
	for _, ingress := range owned {
		target := ingress.Specification.Target
		targetsOurService := target.Present && target.Value.ServiceId == service.Id
		if !targetsOurService {
			continue
		}

		if _, stillPresent := hostSet[ingress.Specification.Domain]; stillPresent {
			continue
		}

		orphaned = append(orphaned, ingress.Id)
	}

	if len(orphaned) == 0 {
		return
	}

	_, herr := ucxapi.StackGrantDeleteIngress.InvokeEx(client, ucxapi.StackGrantDeleteIngressRequest{
		StackGrantAuth: ucxapi.StackGrantAuth{Token: token},
		ServiceId:      service.Id,
		IngressIds:     orphaned,
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not delete the orphaned public links: %s", herr)
		return
	}

	for _, id := range orphaned {
		log.Info("k8s controller: deleted the orphaned public link %s", id)
	}
}

func collectIngressHosts(ctx context.Context, k8s *kubernetes.Clientset) ([]string, error) {
	list, err := k8s.NetworkingV1().Ingresses(meta.NamespaceAll).List(ctx, meta.ListOptions{})
	if err != nil {
		return nil, err
	}

	seen := map[string]util.Empty{}
	for i := range list.Items {
		ingress := &list.Items[i]
		for _, rule := range ingress.Spec.Rules {
			if rule.Host != "" {
				seen[rule.Host] = util.Empty{}
			}
		}

		for _, tls := range ingress.Spec.TLS {
			for _, host := range tls.Hosts {
				if host != "" {
					seen[host] = util.Empty{}
				}
			}
		}
	}

	result := make([]string, 0, len(seen))
	for host := range seen {
		result = append(result, host)
	}
	slices.Sort(result)
	return result, nil
}

func eligibleDomain(support orcapi.IngressSupport, domain string) bool {
	if len(domain) > 253 {
		return false
	}

	withoutPrefix, okPrefix := strings.CutPrefix(domain, support.Prefix)
	userToken, okSuffix := strings.CutSuffix(withoutPrefix, support.Suffix)
	userToken = strings.ToLower(userToken)

	if !okPrefix || !okSuffix {
		return false
	}

	return linkRegex.MatchString(userToken)
}
