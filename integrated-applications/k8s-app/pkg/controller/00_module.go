package controller

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"slices"
	"strings"
	"time"

	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/cache"

	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

const (
	controllerTokenPath  = "/etc/ucloud-k8s/controller/token"
	providerHostnamePath = "/opt/ucloud/provider-hostname.txt"
	reconcileInterval    = 30 * time.Second
	domainFailureBackoff = 5 * time.Minute
)

var hostnamePartRegex = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)
var linkRegex = regexp.MustCompile(`^[a-z]([-_a-z0-9]){4,255}$`)

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

func reconcile(ctx context.Context, k8s *kubernetes.Clientset, client *rpc.Client, token string) {
	auth := ucxapi.StackGrantAuth{Token: token}

	support, herr := ucxapi.StackGrantIngressProducts.InvokeEx(client, ucxapi.StackGrantIngressProductsRequest{
		StackGrantAuth: auth,
	}, rpc.InvokeOpts{})
	if herr != nil {
		log.Warn("k8s controller: could not retrieve the public link products: %s", herr)
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

		_, herr := ucxapi.StackGrantCreateIngress.InvokeEx(client, ucxapi.StackGrantCreateIngressRequest{
			StackGrantAuth: auth,
			Items: []orcapi.IngressSpecification{{
				Domain: domain,
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

	if len(userToken) < 5 {
		return false
	}

	if strings.Contains(userToken, ".") {
		return false
	}

	first := []rune(userToken)[0]
	if first >= '0' && first <= '9' {
		return false
	}

	if strings.HasSuffix(userToken, "-") || strings.HasSuffix(userToken, "_") {
		return false
	}

	if !hostnamePartRegex.MatchString(userToken) {
		return false
	}

	return linkRegex.MatchString(userToken)
}
