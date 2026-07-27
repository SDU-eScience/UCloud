package ucxsvc

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strconv"
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
	stackDataMaxBytes     = 64*1024 - 1
	ucxAppNameEnv         = "UCLOUD_UCX_APP_NAME"
	ucxAppVersionEnv      = "UCLOUD_UCX_APP_VERSION"
	ucxVmServiceUid       = 11042
)

type UcxCustomUiServiceInit struct {
	Labels     map[string]string
	InitScript string
}

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

	if len(data) <= stackDataMaxBytes {
		_ = stackDataWriteString(stack, path, data, mode)
		return
	}

	_ = stackDataWriteString(stack, path, data[:stackDataMaxBytes], mode)
	if !stack.Ok {
		return
	}

	remaining := []byte(data[stackDataMaxBytes:])
	_ = stackDataAppendBytesChunked(stack, path, remaining, mode)
}

func StackWriteFileBytes(stack *Stack, path string, data []byte) {
	StackWriteFileBytesEx(stack, path, data, 0660)
}

func StackWriteFileBytesEx(stack *Stack, path string, data []byte, mode uint32) {
	if !stack.Ok {
		return
	}

	if stackDataWriteString(stack, path, "", mode) != nil {
		return
	}

	_ = stackDataAppendBytesChunked(stack, path, data, mode)
}

func StackOpenFileWriter(stack *Stack, path string) io.Writer {
	return StackOpenFileWriterEx(stack, path, 0660)
}

func StackOpenFileWriterEx(stack *Stack, path string, mode uint32) io.Writer {
	writer := &stackFileWriter{stack: stack, path: path, mode: mode}
	if stack.Ok && stackDataWriteString(stack, path, "", mode) == nil {
		writer.initialized = true
	}
	return writer
}

type stackFileWriter struct {
	stack       *Stack
	path        string
	mode        uint32
	initialized bool
}

func (w *stackFileWriter) Write(data []byte) (int, error) {
	if !w.stack.Ok {
		return 0, fmt.Errorf("stack is not available")
	}

	if !w.initialized {
		if err := stackDataWriteString(w.stack, w.path, "", w.mode); err != nil {
			return 0, err
		}
		w.initialized = true
	}

	written := 0
	for len(data) > 0 {
		chunkSize := stackDataChunkSize(len(data))
		if err := stackDataAppendBytes(w.stack, w.path, data[:chunkSize], w.mode); err != nil {
			return written, err
		}
		written += chunkSize
		data = data[chunkSize:]
	}

	return written, nil
}

func stackDataWriteString(stack *Stack, path string, data string, mode uint32) error {
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
		return err
	}

	return nil
}

func stackDataAppendBytesChunked(stack *Stack, path string, data []byte, mode uint32) error {
	for len(data) > 0 {
		chunkSize := stackDataChunkSize(len(data))
		if err := stackDataAppendBytes(stack, path, data[:chunkSize], mode); err != nil {
			return err
		}
		data = data[chunkSize:]
	}

	return nil
}

func stackDataChunkSize(remaining int) int {
	if remaining < stackDataMaxBytes {
		return remaining
	}
	return stackDataMaxBytes
}

func stackDataAppendBytes(stack *Stack, path string, data []byte, mode uint32) error {
	session := *stack.app.Session()
	_, err := ucxapi.StackDataAppend.Invoke(session, ucxapi.StackDataAppendRequest{
		InstanceId: stack.InstanceId,
		Path:       path,
		Data:       data,
		Perm:       mode,
	})

	if err != nil {
		log.Warn("Could not write file: %s", err)
		UiSendFailure(stack.app, "Unable start application stack, try again later.")
		stack.Ok = false
		return err
	}

	return nil
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

func UcxPortLabel(port int) map[string]string {
	labels := map[string]string{}
	appName := strings.TrimSpace(os.Getenv(ucxAppNameEnv))
	appVersion := strings.TrimSpace(os.Getenv(ucxAppVersionEnv))
	if appName != "" {
		labels["ucloud.dk/ucxAppName"] = appName
	}
	if appVersion != "" {
		labels["ucloud.dk/ucxAppVersion"] = appVersion
	}
	if port > 0 {
		labels["ucloud.dk/ucxport"] = strconv.Itoa(port)
	}
	return labels
}

func UcxInitCustomUiService(stack *Stack, port int, args string) UcxCustomUiServiceInit {
	result := UcxCustomUiServiceInit{Labels: UcxPortLabel(port)}
	if stack == nil || !stack.Ok {
		return result
	}

	runnerName := fmt.Sprintf(".ucx-custom-ui-%s.sh", util.SecureToken())
	runnerPath := filepath.Join(stack.MountPath, runnerName)
	StackWriteFileEx(stack, runnerName, ucxCustomUiRunnerScript(port, args), 0770)
	if !stack.Ok {
		return result
	}

	result.InitScript = ucxCustomUiServiceInitScript(runnerPath)
	return result
}

func ucxCustomUiRunnerScript(port int, args string) string {
	appName := strings.TrimSpace(os.Getenv(ucxAppNameEnv))
	appVersion := strings.TrimSpace(os.Getenv(ucxAppVersionEnv))
	args = strings.TrimSpace(args)
	invoke := `"$RUNTIME_BIN"`
	if args != "" {
		invoke += " " + args
	}

	return fmt.Sprintf(`#!/usr/bin/env bash
set -euo pipefail

WATCHED=/opt/ucloud-ucx/current
RUNTIME_DIR=/tmp/ucloud-ucx-custom-ui
RUNTIME_BIN="$RUNTIME_DIR/current"
mkdir -p "$RUNTIME_DIR"

file_state() {
  if [ ! -f "$WATCHED" ]; then
    printf 'missing'
    return
  fi
  sha256sum "$WATCHED" | cut -d ' ' -f 1
}

export UCX_PORT=%d
export UCLOUD_UCX_APP_NAME=%s
export UCLOUD_UCX_APP_VERSION=%s

LAST_STATE=""
while true; do
  while [ ! -f "$WATCHED" ]; do
    sleep 1
  done

  CURRENT_STATE="$(file_state)"
  if [ "$CURRENT_STATE" != "$LAST_STATE" ]; then
    cp "$WATCHED" "$RUNTIME_BIN.tmp"
    chmod +x "$RUNTIME_BIN.tmp"
    mv "$RUNTIME_BIN.tmp" "$RUNTIME_BIN"
    LAST_STATE="$CURRENT_STATE"
  fi

  export UCX_EXECUTABLE="$RUNTIME_BIN"
  %s &
  PID="$!"

  while kill -0 "$PID" 2>/dev/null; do
    sleep 1
    NEXT_STATE="$(file_state)"
    if [ "$NEXT_STATE" != "$LAST_STATE" ]; then
      kill "$PID" 2>/dev/null || true
      wait "$PID" 2>/dev/null || true
      break
    fi
  done

  wait "$PID" 2>/dev/null || true
  sleep 1
done
`, port, orcapi.EscapeBash(appName), orcapi.EscapeBash(appVersion), invoke)
}

func ucxCustomUiServiceInitScript(runnerPath string) string {
	return fmt.Sprintf(`install -d -m 0755 /etc/systemd/system
cat >/etc/systemd/system/ucloud-ucx-custom-ui.service <<'EOF'
[Unit]
Description=UCloud UCX custom UI service
After=network-online.target remote-fs.target
Wants=network-online.target

[Service]
Type=simple
User=%d
Group=%d
ExecStart=/bin/bash %s
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now ucloud-ucx-custom-ui.service
`, ucxVmServiceUid, ucxVmServiceUid, orcapi.EscapeBash(runnerPath))
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

type PublicLinkCreateOptions struct {
	Port util.Option[int]
	TLS  bool
}

func PublicLinkCreate(stack *Stack, name string, options PublicLinkCreateOptions) orcapi.AppParameterValue {
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
	if options.Port.Present {
		result.Port = options.Port.Value
	}
	result.TLS = options.TLS
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
