package shared

import (
	"embed"
	"encoding/json"
	"fmt"
	"log"
	"sort"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

//go:embed scripts/*.sh scripts/*.yml scripts/*.tpl
var scriptFiles embed.FS

const GroupControlPlane = "control-plane"
const GroupWorker = "worker"

const StackGroupingLabel = "ucloud.dk/k8s-node-group"

const KubernetesConfigurationFileName = "kubeconfig"
const KubernetesTokenFileName = "kube-api-token"
const HeadlampTokenFileName = "headlamp-token"

const ApiPort = 6443
const HeadlampPort = 30500

const customUiPort = 43102

var controlPlaneServiceForwardsTcp = marshalPorts([]int{2379, 2380, 2381, 6443, 6444, 10248, 10249, 10250, 10256, 10257, 10258, 10259})
var workerServiceForwardsTcp = marshalPorts([]int{6443, 10250, 10256, 30500})

func Script(name string) string {
	data, err := scriptFiles.ReadFile("scripts/" + name)
	if err != nil {
		log.Fatal(err)
	}
	return string(data)
}

func StackFile(name string) string {
	data, err := scriptFiles.ReadFile("scripts/" + name)
	if err != nil {
		log.Fatal(err)
	}
	return string(data)
}

type ClusterSpec struct {
	Machine           accapi.ProductReference
	WorkerNodes       int
	ControlPlaneNodes int
	Ports             []int
}

type ClusterNodeSpec struct {
	Group              string
	Index              int
	Machine            accapi.ProductReference
	Attachments        []orcapi.AppParameterValue
	ExtraLabels        map[string]string
	CustomUiInitScript string
}

func ClusterCreate(app ucx.Application, stackId string, spec ClusterSpec) (*ucxsvc.Stack, bool) {
	stack, ok := ucxsvc.StackCreate(app, stackId, "Kubernetes")
	if !ok {
		return stack, false
	}

	session := *app.Session()
	linkProducts, err := ucxapi.PublicLinksRetrieveProducts.Invoke(session, util.Empty{})
	if err != nil || len(linkProducts) == 0 {
		ucxsvc.UiSendFailure(app, "Could not find a suitable public link product, but this cluster requires it.")
		return stack, false
	}
	linkDomain := orcapi.IngressSupport{
		Prefix:  linkProducts[0].Support.Prefix,
		Suffix:  linkProducts[0].Support.Suffix,
		Product: linkProducts[0].Product.ToReference(),
	}

	stackIdLower := strings.ToLower(stackId)
	apiLinkDomain := fmt.Sprintf("%s%s-k8s%s", linkDomain.Prefix, stackIdLower, linkDomain.Suffix)

	ucxsvc.StackWriteFile(stack, "join-token.txt", util.SecureToken())
	ucxsvc.StackWriteFile(stack, "admin-token.yml", StackFile("admin-token.yml"))
	ucxsvc.StackWriteFile(stack, "kubeconfig", fmt.Sprintf(
		StackFile("kubeconfig.tpl"),
		apiLinkDomain,
	))

	network := ucxsvc.PrivateNetworkCreate(stack, stackId)

	links := []orcapi.AppParameterValue{
		ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-k8s", stackId), ucxsvc.PublicLinkCreateOptions{
			Port: util.OptValue(ApiPort),
			TLS:  true,
		}),
		ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-dashboard", stackId), ucxsvc.PublicLinkCreateOptions{
			Port: util.OptValue(HeadlampPort),
		}),
	}
	for _, port := range spec.Ports {
		links = append(links, ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-%d", stackId, port), ucxsvc.PublicLinkCreateOptions{
			Port: util.OptValue(port),
		}))
	}

	customUi := ucxsvc.UcxInitCustomUiService(stack, customUiPort, "")

	for i := 1; i <= spec.ControlPlaneNodes; i++ {
		attachments := []orcapi.AppParameterValue{network}
		var extraLabels map[string]string

		if i == 1 {
			attachments = append(attachments, links...)
			extraLabels = customUi.Labels
		}

		vmId := ClusterNodeCreate(stack, ClusterNodeSpec{
			Group:              GroupControlPlane,
			Index:              i,
			Machine:            spec.Machine,
			Attachments:        attachments,
			ExtraLabels:        extraLabels,
			CustomUiInitScript: customUi.InitScript,
		})
		ucxsvc.UiSendSuccess(app, "Created control plane VM "+vmId+"!")
	}

	for i := 1; i <= spec.WorkerNodes; i++ {
		vmId := ClusterNodeCreate(stack, ClusterNodeSpec{
			Group:       GroupWorker,
			Index:       i,
			Machine:     spec.Machine,
			Attachments: []orcapi.AppParameterValue{network},
		})
		ucxsvc.UiSendSuccess(app, "Created worker VM "+vmId+"!")
	}

	ucxsvc.UiSendSuccess(app, fmt.Sprintf("Forwarding ports %v!", spec.Ports))
	ucxsvc.StackConfirmAndOpen(stack)

	return stack, true
}

func ClusterAddNode(app ucx.Application, stack *ucxsvc.Stack, node ClusterNodeSpec, existingJobs []orcapi.Job) (string, bool) {
	if node.Group != GroupControlPlane && node.Group != GroupWorker {
		ucxsvc.UiSendFailure(app, "Unsupported group")
		return "", false
	}

	if len(existingJobs) == 0 {
		ucxsvc.UiSendFailure(app, "No existing nodes in group "+node.Group)
		return "", false
	}

	network := orcapi.AppParameterValue{}
	for _, resc := range existingJobs[0].Specification.Resources {
		if resc.Type == orcapi.AppParameterValueTypePrivateNetwork {
			network = resc
			break
		}
	}
	if network.Type != orcapi.AppParameterValueTypePrivateNetwork {
		ucxsvc.UiSendFailure(app, "Could not find the private network of the stack")
		return "", false
	}

	vmId := ClusterNodeCreate(stack, ClusterNodeSpec{
		Group:       node.Group,
		Index:       node.Index,
		Machine:     node.Machine,
		Attachments: []orcapi.AppParameterValue{network},
	})
	return vmId, true
}

func ClusterNodeLabels(group string, initScriptLabels map[string]string, extra map[string]string) map[string]string {
	var forwards string
	switch group {
	case GroupControlPlane:
		forwards = controlPlaneServiceForwardsTcp
	case GroupWorker:
		forwards = workerServiceForwardsTcp
	default:
		log.Fatalf("unknown node group %q", group)
	}

	labels := util.MapMerge(initScriptLabels, map[string]string{
		orcapi.ResourceLabelServiceForwardTcp: forwards,
		orcapi.ResourceLabelServiceForwardUdp: marshalPorts([]int{51820, 51821}),
		StackGroupingLabel:                    group,
	})

	return util.MapMerge(labels, extra)
}

func ClusterNodeHostname(group string, index int) string {
	return fmt.Sprintf("%s-%v", group, index)
}

func ClusterNodeGroups(jobs []orcapi.Job) []string {
	groupSet := map[string]struct{}{}
	for _, job := range jobs {
		group := strings.TrimSpace(job.Specification.Labels[StackGroupingLabel])
		if group == "" {
			continue
		}
		groupSet[group] = struct{}{}
	}

	groups := make([]string, 0, len(groupSet))
	for group := range groupSet {
		groups = append(groups, group)
	}

	sort.Strings(groups)
	return groups
}

func ClusterNodesByGroup(jobs []orcapi.Job) map[string][]orcapi.Job {
	m := make(map[string][]orcapi.Job)
	for _, job := range jobs {
		group := strings.TrimSpace(job.Specification.Labels[StackGroupingLabel])
		if group == "" {
			continue
		}
		m[group] = append(m[group], job)
	}
	return m
}

func ClusterNodeCreate(stack *ucxsvc.Stack, node ClusterNodeSpec) string {
	var scriptName string
	switch {
	case node.Group == GroupControlPlane && node.Index == 1:
		scriptName = "prime-control-plane-init.sh"
	case node.Group == GroupControlPlane:
		scriptName = "control-plane-init.sh"
	case node.Group == GroupWorker:
		scriptName = "worker-init.sh"
	default:
		log.Fatalf("unknown node group %q", node.Group)
	}

	script := Script(scriptName)
	if node.Group == GroupControlPlane && node.Index == 1 && node.CustomUiInitScript != "" {
		script += "\n" + node.CustomUiInitScript
	}

	initScriptLabels := ucxsvc.StackWriteInitScript(stack, script)

	return ucxsvc.VirtualMachineCreate(stack, ucxsvc.VirtualMachineSpec{
		Labels:      ClusterNodeLabels(node.Group, initScriptLabels, node.ExtraLabels),
		Product:     node.Machine,
		Image:       ucxsvc.VmImageUbuntu26_04,
		Hostname:    ClusterNodeHostname(node.Group, node.Index),
		Attachments: node.Attachments,
	})
}

func marshalPorts(ports []int) string {
	data, err := json.Marshal(ports)
	if err != nil {
		log.Fatal(err)
	}
	return string(data)
}
