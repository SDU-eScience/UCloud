package k8s

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	ws "github.com/gorilla/websocket"
	"golang.org/x/sys/unix"
	"ucloud.dk/gonja/v2/exec"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"

	appsv1 "k8s.io/api/apps/v1"
	k8score "k8s.io/api/core/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	k8slabels "k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/util/intstr"
)

const ucxBackendPort int32 = 8080

var ucxNameRegex = regexp.MustCompile(`[^a-z0-9-]+`)

func initAppUcx() ctrl.UcxApplicationService {
	return ctrl.UcxApplicationService{OnConnect: ucxOnConnect}
}

func ucxOnConnect(conn *ws.Conn) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	proxy := ucx.NewProxy("ws://pending")
	info := orcapi.AppUcxConnectProviderRequest{}

	proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
		_ = downstreamToken

		if err := json.Unmarshal([]byte(downstreamSysHello), &info); err != nil {
			log.Warn("UCX provider: invalid syshello payload: %v", err)
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		application := &info.Application
		if !application.Invocation.Tool.Tool.Present {
			log.Warn("UCX provider: missing resolved tool in application payload")
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		if application.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendUcx {
			log.Warn("UCX provider: application backend is not UCX")
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		upstreamUrl, err := ensureUcxBackendAndResolveUpstream(ctx, application)
		if err != nil {
			log.Warn("UCX provider: failed to prepare backend: %v", err)
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		return ucx.ProxyUpstreamSelection{
			Allowed:          true,
			UpstreamUrl:      upstreamUrl,
			UpstreamToken:    "",
			UpstreamSysHello: downstreamSysHello,
		}
	})

	mu := sync.Mutex{}
	stackToDeletionRequest := map[string]int{}

	ucxapi.StackCreate.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.StackCreateRequest) (ucxapi.Stack, error) {
		if err := util.ValidateStringE(&request.StackType, "stackType", 0); err != nil {
			return ucxapi.Stack{}, err.AsError()
		}

		instanceId := request.StackId

		internalPathMemberFiles, drive, err := filesystem.InitializeMemberFiles(info.Owner.CreatedBy, info.Owner.Project)
		if err != nil {
			return ucxapi.Stack{}, err.AsError()
		}

		instanceFolder := filepath.Join(internalPathMemberFiles, "Jobs", "Stacks", instanceId)
		err = filesystem.DoCreateFolder(instanceFolder)
		if err != nil {
			return ucxapi.Stack{}, err.AsError()
		}

		ucloudPath, ok := filesystem.InternalToUCloudWithDrive(drive, instanceFolder)
		if !ok {
			return ucxapi.Stack{}, fmt.Errorf("internal error")
		}

		id, err := orcapi.StacksControlRequestDeletion.Invoke(orcapi.StacksControlRequestDeletionRequest{
			Id:             instanceId,
			ActivationTime: util.OptValue[fnd.Timestamp](fnd.Timestamp(time.Now().Add(2 * time.Minute))),
			Owner:          info.Owner,
		})

		if err != nil {
			return ucxapi.Stack{}, err.AsError()
		}

		mu.Lock()
		stackToDeletionRequest[instanceId] = id.Id
		mu.Unlock()

		return ucxapi.Stack{
			InstanceId: instanceId,
			Labels: map[string]string{
				"ucloud.dk/stack":         "true",
				"ucloud.dk/stackname":     request.StackType,
				"ucloud.dk/stackinstance": instanceId,
			},
			Mount: orcapi.AppParameterValueFileWithMountPath(ucloudPath, false, "/etc/ucloud-stack"),
		}, nil
	})

	ucxapi.StackDataWrite.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.StackDataWriteRequest) (util.Empty, error) {
		if len(request.Data) >= 1024*64 {
			return util.Empty{}, fmt.Errorf("input data is too large")
		}

		internalPathMemberFiles, _, err := filesystem.InitializeMemberFiles(info.Owner.CreatedBy, info.Owner.Project)
		if err != nil {
			return util.Empty{}, err.AsError()
		}

		requestedPath := filepath.Join(internalPathMemberFiles, "Jobs", "Stacks", request.InstanceId)
		err = filesystem.DoCreateFolder(requestedPath)
		if err != nil {
			return util.Empty{}, err.AsError()
		}

		pathComponents := util.Components(request.Path)
		for _, comp := range pathComponents {
			if comp != "." && comp != ".." {
				requestedPath = filepath.Join(requestedPath, comp)
			}
		}

		parentPath := util.Parent(requestedPath)
		if err = filesystem.DoCreateFolder(parentPath); err != nil {
			return util.Empty{}, err.AsError()
		}

		file, ok := filesystem.OpenFile(requestedPath, unix.O_CREAT|unix.O_WRONLY|unix.O_TRUNC, request.Perm)
		if !ok {
			return util.Empty{}, fmt.Errorf("unable to write data at: %s", requestedPath)
		}

		defer util.SilentClose(file)

		_, gerr := file.WriteString(request.Data)
		if gerr != nil {
			return util.Empty{}, fmt.Errorf("unable to write data: %s", gerr)
		}

		return util.Empty{}, nil
	})

	ucxapi.StackConfirm.HandlerProxy(proxy, func(ctx context.Context, request fnd.FindByStringId) (util.Empty, error) {
		mu.Lock()
		deletionReqId, ok := stackToDeletionRequest[request.Id]
		mu.Unlock()
		if ok {
			_, err := orcapi.StacksControlCancelDeletion.Invoke(fnd.FindByIntId{Id: deletionReqId})

			if err != nil {
				return util.Empty{}, err.AsError()
			}
		}
		return util.Empty{}, nil
	})

	ucxapi.IM.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.Message) (ucxapi.Message, error) {
		log.Info("Got a message from '%#v': %s", info.Owner, request.Message)
		return ucxapi.Message{"Hello from the provider!"}, nil
	})

	if err := proxy.Run(ctx, conn); err != nil {
		log.Warn("UCX provider proxy failure: %v", err)
	}
}

func ensureUcxBackendAndResolveUpstream(ctx context.Context, app *orcapi.Application) (string, error) {
	namespace := shared.ServiceConfig.Compute.TaskNamespace
	deploymentName := ucxDeploymentName(app.Metadata.Name, app.Metadata.Version)
	serviceName := deploymentName
	selector := ucxSelectorLabels(deploymentName)
	inDevelopmentMode := ucxDevelopmentModePath(app)

	isRunning, err := ucxDeploymentRunning(ctx, namespace, deploymentName)
	if err != nil {
		log.Warn("UCX provider: failed deployment lookup for %s/%s: %v", namespace, deploymentName, err)
		return "", err
	}

	if !isRunning {
		script, err := renderUcxInvocationScript(app, inDevelopmentMode.Present)
		if err != nil {
			return "", err
		}

		image := strings.TrimSpace(app.Invocation.Tool.Tool.Value.Description.Image)
		if image == "" {
			return "", fmt.Errorf("resolved tool image is missing")
		}

		if err := ensureUcxService(ctx, namespace, serviceName, selector); err != nil {
			log.Warn("UCX provider: failed ensuring service %s/%s: %v", namespace, serviceName, err)
			return "", err
		}

		if err := ensureUcxDeployment(ctx, namespace, deploymentName, image, script, selector, inDevelopmentMode); err != nil {
			log.Warn("UCX provider: failed ensuring deployment %s/%s: %v", namespace, deploymentName, err)
			return "", err
		}
	} else {
		if err := ensureUcxService(ctx, namespace, serviceName, selector); err != nil {
			log.Warn("UCX provider: failed ensuring service %s/%s: %v", namespace, serviceName, err)
			return "", err
		}
	}

	if err := waitForUcxDeploymentReady(ctx, namespace, deploymentName, 90*time.Second); err != nil {
		log.Warn("UCX provider: deployment did not become ready %s/%s: %v", namespace, deploymentName, err)
		return "", err
	}

	if util.DevelopmentModeEnabled() {
		podName, err := findReadyUcxPod(ctx, namespace, selector)
		if err != nil {
			log.Warn("UCX provider: failed finding ready pod in dev mode for %s/%s: %v", namespace, deploymentName, err)
			return "", err
		}

		tunnelPort := shared.EstablishTunnelEx(podName, namespace, int(ucxBackendPort))
		return fmt.Sprintf("ws://127.0.0.1:%d/", tunnelPort), nil
	}

	return fmt.Sprintf("ws://%s.%s.svc.cluster.local:%d/", serviceName, namespace, ucxBackendPort), nil
}

func ensureUcxService(ctx context.Context, namespace string, name string, selector map[string]string) error {
	service := &k8score.Service{
		ObjectMeta: meta.ObjectMeta{
			Name:      name,
			Namespace: namespace,
		},
		Spec: k8score.ServiceSpec{
			Type:     k8score.ServiceTypeClusterIP,
			Selector: selector,
			Ports: []k8score.ServicePort{
				{
					Name:       "ws",
					Port:       ucxBackendPort,
					TargetPort: intstr.FromInt32(ucxBackendPort),
				},
			},
		},
	}

	_, err := shared.K8sClient.CoreV1().Services(namespace).Create(ctx, service, meta.CreateOptions{})
	if err != nil && !k8serrors.IsAlreadyExists(err) {
		return err
	}

	return nil
}

func ensureUcxDeployment(
	ctx context.Context,
	namespace string,
	name string,
	image string,
	script string,
	selector map[string]string,
	developmentModeSubPath util.Option[string],
) error {
	volumes := []k8score.Volume{}
	volumeMounts := []k8score.VolumeMount{}

	if developmentModeSubPath.Present {
		volumes = append(volumes, k8score.Volume{
			Name: "ucloud-filesystem",
			VolumeSource: k8score.VolumeSource{
				PersistentVolumeClaim: &k8score.PersistentVolumeClaimVolumeSource{
					ClaimName: shared.ServiceConfig.FileSystem.ClaimName,
				},
			},
		})

		volumeMounts = append(volumeMounts, k8score.VolumeMount{
			Name:      "ucloud-filesystem",
			MountPath: "/opt/ucloud",
			SubPath:   developmentModeSubPath.Value,
			ReadOnly:  true,
		})
	}

	deployment := &appsv1.Deployment{
		ObjectMeta: meta.ObjectMeta{
			Name:      name,
			Namespace: namespace,
		},
		Spec: appsv1.DeploymentSpec{
			Replicas: util.Pointer[int32](1),
			Selector: &meta.LabelSelector{MatchLabels: selector},
			Template: k8score.PodTemplateSpec{
				ObjectMeta: meta.ObjectMeta{Labels: selector},
				Spec: k8score.PodSpec{
					Volumes:      volumes,
					NodeSelector: shared.ServiceConfig.Compute.TaskNodeSelector,
					Containers: []k8score.Container{
						{
							Name:            "ucx-app",
							Image:           image,
							ImagePullPolicy: k8score.PullIfNotPresent,
							Command:         []string{"/bin/bash", "-lc", script},
							VolumeMounts:    volumeMounts,
							Ports: []k8score.ContainerPort{
								{ContainerPort: ucxBackendPort},
							},
						},
					},
				},
			},
		},
	}

	_, err := shared.K8sClient.AppsV1().Deployments(namespace).Create(ctx, deployment, meta.CreateOptions{})
	if err == nil {
		return nil
	}

	if !k8serrors.IsAlreadyExists(err) {
		return err
	}

	existing, getErr := shared.K8sClient.AppsV1().Deployments(namespace).Get(ctx, name, meta.GetOptions{})
	if getErr != nil {
		return getErr
	}

	if existing.Spec.Replicas == nil || *existing.Spec.Replicas != 1 {
		existing.Spec.Replicas = util.Pointer[int32](1)
		_, updateErr := shared.K8sClient.AppsV1().Deployments(namespace).Update(ctx, existing, meta.UpdateOptions{})
		if updateErr != nil {
			return updateErr
		}
	}

	return nil
}

func ucxDeploymentRunning(ctx context.Context, namespace string, name string) (bool, error) {
	deployment, err := shared.K8sClient.AppsV1().Deployments(namespace).Get(ctx, name, meta.GetOptions{})
	if err != nil {
		if k8serrors.IsNotFound(err) {
			return false, nil
		}
		return false, err
	}

	return deployment.Status.ReadyReplicas > 0, nil
}

func waitForUcxDeploymentReady(ctx context.Context, namespace string, name string, timeout time.Duration) error {
	waitCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	for {
		deployment, err := shared.K8sClient.AppsV1().Deployments(namespace).Get(waitCtx, name, meta.GetOptions{})
		if err == nil && deployment.Status.ReadyReplicas > 0 {
			return nil
		}

		if err != nil && !k8serrors.IsNotFound(err) {
			return err
		}

		select {
		case <-waitCtx.Done():
			if err != nil {
				return err
			}
			return fmt.Errorf("timeout waiting for deployment '%s' to become ready", name)
		case <-time.After(1 * time.Second):
		}
	}
}

func findReadyUcxPod(ctx context.Context, namespace string, selector map[string]string) (string, error) {
	selectorString := k8slabels.Set(selector).String()
	pods, err := shared.K8sClient.CoreV1().Pods(namespace).List(ctx, meta.ListOptions{LabelSelector: selectorString})
	if err != nil {
		return "", err
	}

	if len(pods.Items) == 0 {
		return "", fmt.Errorf("no pods found for selector %s", selectorString)
	}

	for _, pod := range pods.Items {
		if pod.Status.Phase == k8score.PodRunning {
			for _, cond := range pod.Status.Conditions {
				if cond.Type == k8score.PodReady && cond.Status == k8score.ConditionTrue {
					return pod.Name, nil
				}
			}
		}
	}

	return pods.Items[0].Name, nil
}

func renderUcxInvocationScript(app *orcapi.Application, inDevelopmentMode bool) (string, error) {
	template := ""
	for _, parameter := range app.Invocation.Invocation {
		if parameter.Type == orcapi.InvocationParameterTypeJinja {
			template = parameter.InvocationParameterJinja.Template
			break
		}
	}

	if template == "" {
		return "", fmt.Errorf("resolved application is missing jinja invocation")
	}

	jinjaContext := exec.NewContext(map[string]any{
		"ucloud": map[string]any{
			"ucxDevelopmentMode": inDevelopmentMode,
			"application": map[string]any{
				"name":    app.Metadata.Name,
				"version": app.Metadata.Version,
			},
		},
	})

	output, err := ctrl.JinjaTemplateExecute(template, 0, nil, jinjaContext, ctrl.JinjaFlagsNoPreProcess)
	if err != nil {
		return "", err
	}

	return output, nil
}

func ucxDeploymentName(name string, version string) string {
	base := fmt.Sprintf("ucx-%s-%s", dnsLabel(name), dnsLabel(version))
	if len(base) <= 63 {
		return base
	}

	hash := util.Sha256([]byte(base))
	trimmed := strings.Trim(base[:54], "-")
	return fmt.Sprintf("%s-%s", trimmed, hash[:8])
}

func dnsLabel(input string) string {
	cleaned := strings.ToLower(strings.TrimSpace(input))
	cleaned = ucxNameRegex.ReplaceAllString(cleaned, "-")
	cleaned = strings.Trim(cleaned, "-")
	for strings.Contains(cleaned, "--") {
		cleaned = strings.ReplaceAll(cleaned, "--", "-")
	}

	if cleaned == "" {
		return "x"
	}

	if len(cleaned) > 20 {
		cleaned = strings.Trim(cleaned[:20], "-")
		if cleaned == "" {
			return "x"
		}
	}

	return cleaned
}

func ucxSelectorLabels(deploymentName string) map[string]string {
	return map[string]string{
		"ucloud.dk/ucx-deployment": deploymentName,
	}
}

func ucxDevelopmentModePath(app *orcapi.Application) util.Option[string] {
	developmentEntries := shared.ServiceConfig.Compute.Ucx.Development
	for _, entry := range developmentEntries {
		if entry.Name == app.Metadata.Name && entry.Version == app.Metadata.Version {
			return util.OptValue(entry.SubPath.GetOrDefault(shared.ExecutablesDir))
		}
	}

	return util.OptNone[string]()
}
