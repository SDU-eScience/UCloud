package ucxsvc

import (
	"fmt"
	"path/filepath"
	"slices"
	"strings"

	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"
)

// Stacks
// =====================================================================================================================

type Stack struct {
	InstanceId string
	MountPath  string
	baseMount  orcapi.AppParameterValue
	baseLabels map[string]string
	app        ucx.Application
	Ok         bool
}

const (
	stackLabelInstance    = "ucloud.dk/stackinstance"
	stackLabelStateFolder = "ucloud.dk/stack-state-folder"
)

func (s *Stack) Labels() map[string]string {
	if !s.Ok {
		return map[string]string{}
	}

	return s.baseLabels
}

func (s *Stack) Mount() orcapi.AppParameterValue {
	if !s.Ok {
		return orcapi.AppParameterValue{}
	}

	mount := s.baseMount
	mount.MountPath = s.MountPath
	return mount
}

var stackResourceLabelsToKeep = map[string]util.Empty{
	"ucloud.dk/stack":              {},
	"ucloud.dk/stackname":          {},
	"ucloud.dk/stackinstance":      {},
	"ucloud.dk/stack-state-folder": {},
}

func StackFromJob(app ucx.Application, job orcapi.Job) (*Stack, bool) {
	instanceId := strings.TrimSpace(job.Specification.Labels[stackLabelInstance])
	if instanceId == "" {
		return &Stack{}, false
	}

	labels := map[string]string{}
	for k, v := range job.Specification.Labels {
		if _, ok := stackResourceLabelsToKeep[k]; ok {
			labels[k] = v
		}
	}

	stateFolder := strings.TrimSpace(job.Specification.Labels[stackLabelStateFolder])
	mountPath := stackFindMountPath(job)

	mount := orcapi.AppParameterValue{}
	if stateFolder != "" {
		mount = orcapi.AppParameterValueFileWithMountPath(stateFolder, false, mountPath)
	}

	return &Stack{
		InstanceId: instanceId,
		MountPath:  mountPath,
		baseMount:  mount,
		baseLabels: labels,
		app:        app,
		Ok:         true,
	}, true
}

func stackFindMountPath(job orcapi.Job) string {
	const defaultMountPath = "/etc/ucloud-stack"

	stackPath := strings.TrimSpace(job.Specification.Labels[stackLabelStateFolder])

	for _, parameter := range job.Specification.Parameters {
		if parameter.Type != orcapi.AppParameterValueTypeFile {
			continue
		}

		if stackPath != "" && strings.TrimSpace(parameter.Path) == stackPath {
			if mountPath := strings.TrimSpace(parameter.MountPath); mountPath != "" {
				return mountPath
			}
		}
	}

	for _, resource := range job.Specification.Resources {
		if resource.Type != orcapi.AppParameterValueTypeFile {
			continue
		}

		if stackPath != "" && strings.TrimSpace(resource.Path) == stackPath {
			if mountPath := strings.TrimSpace(resource.MountPath); mountPath != "" {
				return mountPath
			}
		}
	}

	for _, parameter := range job.Specification.Parameters {
		if parameter.Type == orcapi.AppParameterValueTypeFile {
			if mountPath := strings.TrimSpace(parameter.MountPath); mountPath != "" {
				return mountPath
			}
		}
	}

	for _, resource := range job.Specification.Resources {
		if resource.Type == orcapi.AppParameterValueTypeFile {
			if mountPath := strings.TrimSpace(resource.MountPath); mountPath != "" {
				return mountPath
			}
		}
	}

	return defaultMountPath
}

func StackCreate(app ucx.Application, id string, stackType string) (*Stack, bool) {
	session := *app.Session()
	ok, err := ucxapi.StackAvailable.Invoke(session, fndapi.FindByStringId{Id: id})
	if err != nil {
		UiSendFailure(app, "Unable to start application stack, try again later.")
		return &Stack{}, false
	} else if !ok {
		UiSendFailure(app, "An application stack with this name already exists, try another.")
		return &Stack{}, false
	} else {
		stack, err := ucxapi.StackCreate.Invoke(session, ucxapi.StackCreateRequest{StackId: id, StackType: stackType})
		if err != nil {
			UiSendFailure(app, "Unable to start application stack, try again later.")
			return &Stack{}, false
		} else {
			return &Stack{
				InstanceId: id,
				MountPath:  "/etc/ucloud-stack",
				baseMount:  stack.Mount,
				baseLabels: stack.Labels,
				app:        app,
				Ok:         true,
			}, true
		}
	}
}

func StackWriteFile(stack *Stack, path string, data string) {
	StackWriteFileEx(stack, path, data, 0660)
}

func StackWriteFileEx(stack *Stack, path string, data string, mode uint32) {
	if !stack.Ok {
		return
	}

	session := *stack.app.Session()
	_, err := ucxapi.StackDataWrite.Invoke(session, ucxapi.StackDataWriteRequest{
		InstanceId: stack.InstanceId,
		Path:       path,
		Data:       data,
		Perm:       mode,
	})

	if err != nil {
		log.Warn("Could not write file: %s", err)
		UiSendFailure(stack.app, "Unable start application stack, try again later.")
		stack.Ok = false
		return
	}
}

func StackWriteInitScript(stack *Stack, initScript string) map[string]string {
	if !stack.Ok {
		return map[string]string{}
	}

	initName := fmt.Sprintf(".init-%s.sh", util.SecureToken())
	StackWriteFileEx(stack, initName, initScript, 0770)
	if !stack.Ok {
		return map[string]string{}
	}

	return map[string]string{
		"ucloud.dk/initscript": filepath.Join(stack.MountPath, initName),
	}
}

func StackConfirmAndOpen(stack *Stack) {
	if !stack.Ok {
		return
	}
	session := *stack.app.Session()

	_, _ = ucxapi.StackConfirm.Invoke(session, fndapi.FindByStringId{Id: stack.InstanceId})
	_, _ = ucxapi.StackOpen.Invoke(session, fndapi.FindByStringId{Id: stack.InstanceId})
}

// Public IPs
// =====================================================================================================================

func PublicIpCreate(stack *Stack) orcapi.AppParameterValue {
	if !stack.Ok {
		return orcapi.AppParameterValue{}
	}

	session := *stack.app.Session()
	linkProducts, _ := ucxapi.PublicIpsRetrieveProducts.Invoke(session, util.Empty{})
	if len(linkProducts) == 0 {
		stack.Ok = false
		UiSendFailure(stack.app, "Could not find a suitable public ip product, but this stack requires it.")
		return orcapi.AppParameterValue{}
	}

	ips, err := ucxapi.PublicIpsCreate.Invoke(session, []orcapi.PublicIPSpecification{
		{
			ResourceSpecification: orcapi.ResourceSpecification{
				Product: linkProducts[0].Product.ToReference(),
				Labels:  stack.Labels(),
			},
		},
	})

	if len(ips) == 0 || err != nil {
		stack.Ok = false
		UiSendFailure(stack.app, fmt.Sprintf("Could not create a public ip! %s", err))
		return orcapi.AppParameterValue{}
	}

	return orcapi.AppParameterValueNetwork(ips[0].Id)
}

// Public links
// =====================================================================================================================

func PublicLinkCreate(stack *Stack, name string, port util.Option[int]) orcapi.AppParameterValue {
	if !stack.Ok {
		return orcapi.AppParameterValue{}
	}

	session := *stack.app.Session()
	linkProducts, _ := ucxapi.PublicLinksRetrieveProducts.Invoke(session, util.Empty{})
	if len(linkProducts) == 0 {
		stack.Ok = false
		UiSendFailure(stack.app, "Could not find a suitable public link product, but this stack requires it.")
		return orcapi.AppParameterValue{}
	}

	links, err := ucxapi.PublicLinksCreate.Invoke(session, []orcapi.IngressSpecification{
		{
			Domain: fmt.Sprintf("%s%s%s", linkProducts[0].Support.Prefix, name, linkProducts[0].Support.Suffix),
			ResourceSpecification: orcapi.ResourceSpecification{
				Product: linkProducts[0].Product.ToReference(),
				Labels:  stack.Labels(),
			},
		},
	})

	if len(links) == 0 || err != nil {
		stack.Ok = false
		UiSendFailure(stack.app, fmt.Sprintf("Could not create a link! %s", err))
		return orcapi.AppParameterValue{}
	}

	result := orcapi.AppParameterValueIngress(links[0].Id)
	if port.Present {
		result.Port = port.Value
	}
	return result
}

// Private networks
// =====================================================================================================================

func PrivateNetworkCreate(stack *Stack, name string) orcapi.AppParameterValue {
	session := *stack.app.Session()
	products, _ := ucxapi.PrivateNetworksRetrieveProducts.Invoke(session, util.Empty{})
	if len(products) == 0 {
		stack.Ok = false
		UiSendFailure(stack.app, "Could not find a suitable private network product, but this stack requires it.")
		return orcapi.AppParameterValue{}
	}

	networks, err := ucxapi.PrivateNetworksCreate.Invoke(session, []orcapi.PrivateNetworkSpecification{
		{
			Name:      name,
			Subdomain: fmt.Sprintf("net-%s", util.RandomTokenNoTs(4)),
			ResourceSpecification: orcapi.ResourceSpecification{
				Product: products[0].Product.ToReference(),
				Labels:  stack.Labels(),
			},
		},
	})

	if len(networks) == 0 || err != nil {
		stack.Ok = false
		UiSendFailure(stack.app, fmt.Sprintf("Could not create a network! %s", err))
		return orcapi.AppParameterValue{}
	}

	return orcapi.AppParameterValuePrivateNetwork(networks[0].Id)
}

// Jobs
// =====================================================================================================================

func JobCreate(stack *Stack, spec orcapi.JobSpecification) string {
	if !stack.Ok {
		return "0"
	}

	session := *stack.app.Session()

	spec.Labels = util.MapMerge(spec.Labels, stack.Labels())
	resp, serr := ucxapi.JobsCreate.Invoke(session, []orcapi.JobSpecification{spec})
	if serr != nil {
		stack.Ok = false
		UiSendFailure(stack.app, fmt.Sprintf("Failed to create job: %s", serr))
		return "0"
	} else {
		return resp[0].Id
	}
}

type VirtualMachineSpec struct {
	Labels         map[string]string
	Product        accapi.ProductReference
	Image          orcapi.NameAndVersion
	Hostname       string
	Attachments    []orcapi.AppParameterValue
	DiskSize       util.Option[int]
	SkipStackState bool
}

func VirtualMachineCreate(stack *Stack, spec VirtualMachineSpec) string {
	attachments := slices.Clone(spec.Attachments)
	if !spec.SkipStackState {
		attachments = append(attachments, stack.Mount())
	}

	return JobCreate(stack, orcapi.JobSpecification{
		ResourceSpecification: orcapi.ResourceSpecification{
			Product: spec.Product,
			Labels:  spec.Labels,
		},
		Application: spec.Image,
		Name:        spec.Hostname,
		Hostname:    util.OptValue[string](spec.Hostname),
		Parameters: map[string]orcapi.AppParameterValue{
			"diskSize": orcapi.AppParameterValueInteger(int64(spec.DiskSize.GetOrDefault(50))),
		},
		Replicas:  1,
		Resources: attachments,
	})
}

var (
	VmImageUbuntu24_04 = orcapi.NameAndVersion{
		Name:    "vm-ubuntu",
		Version: "24.04",
	}
)

// User-interface
// =====================================================================================================================

func UiSendFailure(app ucx.Application, message string) {
	session := *app.Session()
	_, _ = ucxapi.UiSendMessage.Invoke(session, ucxapi.UiSendMessageRequest{Message: message, Success: false})
}

func UiSendSuccess(app ucx.Application, message string) {
	session := *app.Session()
	_, _ = ucxapi.UiSendMessage.Invoke(session, ucxapi.UiSendMessageRequest{Message: message, Success: true})
}

func StackCopyFile(stack *Stack, fileName string) {
	if stack == nil || !stack.Ok {
		return
	}

	session := *stack.app.Session()
	_, _ = ucxapi.StackCopyFile.Invoke(session, ucxapi.StackDownloadFileRequest{FileName: fileName})
}

func StackDownloadFile(stack *Stack, fileName string) {
	if stack == nil || !stack.Ok {
		return
	}

	session := *stack.app.Session()
	_, _ = ucxapi.StackDownloadFile.Invoke(session, ucxapi.StackDownloadFileRequest{FileName: fileName})
}

func RouterPushPage(app ucx.Application, path string) {
	session := *app.Session()
	_, _ = ucxapi.RouterPushPage.Invoke(session, ucxapi.RouterPushPageRequest{Path: path})
}
