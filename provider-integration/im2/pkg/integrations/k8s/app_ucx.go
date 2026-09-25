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

	"github.com/golang-jwt/jwt/v5"
	ws "github.com/gorilla/websocket"
	"golang.org/x/sys/unix"
	"ucloud.dk/gonja/v2/exec"
	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/containers"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/inference"
	job_introspection "ucloud.dk/pkg/integrations/k8s/job-introspection"
	"ucloud.dk/pkg/integrations/k8s/kubevirt"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/pkg/ucxdelivery"
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
	k8stypes "k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/intstr"
)

const ucxBackendPort int32 = 8080

var ucxNameRegex = regexp.MustCompile(`[^a-z0-9-]+`)

const ucxAuthTokenAnnotation = "ucloud.dk/ucx-auth-token"

var ucxDownstreamJwtParser *jwt.Parser

func ucxDownstreamTokenValid(downstreamToken string) bool {
	trimmed := strings.TrimSpace(downstreamToken)
	if trimmed == "" {
		return false
	}

	if ucxDownstreamJwtParser == nil {
		if cfg.PublicKey == nil {
			return false
		}
		ucxDownstreamJwtParser = jwt.NewParser(
			jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
			jwt.WithIssuer("cloud.sdu.dk"),
		)
	}

	claims := jwt.RegisteredClaims{}
	token, err := ucxDownstreamJwtParser.ParseWithClaims(
		trimmed,
		&claims,
		func(token *jwt.Token) (any, error) {
			return cfg.PublicKey, nil
		},
	)
	if err != nil || !token.Valid {
		return false
	}

	return claims.Subject == "_UCloud"
}

func ucxCreationUiToken(ctx context.Context, namespace string, deploymentName string) (string, error) {
	deployment, err := shared.K8sClient.AppsV1().Deployments(namespace).Get(ctx, deploymentName, meta.GetOptions{})
	if err != nil {
		if k8serrors.IsNotFound(err) {
			return util.SecureToken(), nil
		}
		return "", err
	}

	if existing := deployment.Annotations[ucxAuthTokenAnnotation]; existing != "" {
		return existing, nil
	}

	token := util.SecureToken()
	patch := fmt.Sprintf(
		`{"metadata":{"annotations":{"%s":"%s"}}}`,
		ucxAuthTokenAnnotation, token,
	)
	patched, err := shared.K8sClient.AppsV1().Deployments(namespace).Patch(
		ctx, deploymentName, k8stypes.StrategicMergePatchType, []byte(patch), meta.PatchOptions{},
	)
	if err != nil {
		return "", err
	}

	if resulting := patched.Annotations[ucxAuthTokenAnnotation]; resulting != "" {
		return resulting, nil
	}
	return token, nil
}

func readUcxDeploymentAuthToken(ctx context.Context, namespace string, deploymentName string) (string, error) {
	deployment, err := shared.K8sClient.AppsV1().Deployments(namespace).Get(ctx, deploymentName, meta.GetOptions{})
	if err != nil {
		return "", err
	}
	return deployment.Annotations[ucxAuthTokenAnnotation], nil
}

func ensureUcxDeploymentAuthToken(ctx context.Context, namespace string, name string, authToken string) error {
	deployment, err := shared.K8sClient.AppsV1().Deployments(namespace).Get(ctx, name, meta.GetOptions{})
	if err != nil {
		return err
	}

	if len(deployment.Spec.Template.Spec.Containers) == 0 {
		return fmt.Errorf("deployment %s has no containers", name)
	}

	for _, env := range deployment.Spec.Template.Spec.Containers[0].Env {
		if env.Name == "UCX_AUTH_TOKEN" {
			return nil
		}
	}

	deployment.Spec.Template.Spec.Containers[0].Env = append(
		deployment.Spec.Template.Spec.Containers[0].Env,
		k8score.EnvVar{Name: "UCX_AUTH_TOKEN", Value: authToken},
	)
	_, err = shared.K8sClient.AppsV1().Deployments(namespace).Update(ctx, deployment, meta.UpdateOptions{})
	return err
}

func initAppUcx() ctrl.UcxApplicationService {
	return ctrl.UcxApplicationService{
		OnConnect:    ucxOnConnect,
		OnConnectJob: ucxOnConnectJob,
		InferencePlaygroundFactory: func(owner orcapi.ResourceOwner, sessionId string) ucx.Application {
			return inference.InferencePlayground(owner, sessionId)
		},
	}
}

func ucxOnConnect(conn *ws.Conn) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	proxy := ucx.NewProxy("ws://pending")
	info := orcapi.AppUcxConnectProviderRequest{}

	proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
		if !ucxDownstreamTokenValid(downstreamToken) {
			log.Warn("UCX provider: downstream token was not accepted")
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

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

		ucxdelivery.TrackApp(application)

		upstreamUrl, authToken, err := ensureUcxBackendAndResolveUpstream(ctx, application)
		if err != nil {
			log.Warn("UCX provider: failed to prepare backend: %v", err)
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		return ucx.ProxyUpstreamSelection{
			Allowed:          true,
			UpstreamUrl:      upstreamUrl,
			UpstreamToken:    authToken,
			UpstreamSysHello: downstreamSysHello,
		}
	})

	mu := sync.Mutex{}
	stackToDeletionRequest := map[string]int{}
	confirmedStacks := map[string]bool{}

	renewStackLease := func(instanceId string) error {
		mu.Lock()
		defer mu.Unlock()

		if confirmedStacks[instanceId] {
			return nil
		}

		deletionReqId, ok := stackToDeletionRequest[instanceId]
		if !ok {
			return fmt.Errorf("no deletion lease for stack %s", instanceId)
		}

		_, err := orcapi.StacksControlRenewDeletion.Invoke(orcapi.StacksControlRenewDeletionRequest{
			RequestId:      deletionReqId,
			ActivationTime: util.OptValue[fnd.Timestamp](fnd.Timestamp(time.Now().Add(2 * time.Minute))),
		})
		if err != nil {
			return err
		}
		return nil
	}

	confirmStack := func(instanceId string) error {
		mu.Lock()
		defer mu.Unlock()

		if confirmedStacks[instanceId] {
			return nil
		}

		deletionReqId, ok := stackToDeletionRequest[instanceId]
		if !ok {
			return fmt.Errorf("no deletion lease for stack %s", instanceId)
		}

		_, err := orcapi.StacksControlCancelDeletion.Invoke(fnd.FindByIntId{Id: deletionReqId})
		if err != nil {
			return err
		}

		delete(stackToDeletionRequest, instanceId)
		confirmedStacks[instanceId] = true
		return nil
	}

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
				orcapi.ResourceLabelStack:            "true",
				orcapi.ResourceLabelStackName:        request.StackType,
				orcapi.ResourceLabelStackInstance:    instanceId,
				orcapi.ResourceLabelStackStateFolder: ucloudPath,
			},
			Mount: orcapi.AppParameterValueFileWithMountPath(ucloudPath, false, "/etc/ucloud-stack"),
		}, nil
	})

	ucxapi.StackDataWrite.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.StackDataWriteRequest) (util.Empty, error) {
		if err := renewStackLease(request.InstanceId); err != nil {
			return util.Empty{}, err
		}

		return ucxStackDataWrite(info.Owner, request)
	})

	ucxapi.StackDataAppend.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.StackDataAppendRequest) (util.Empty, error) {
		if err := renewStackLease(request.InstanceId); err != nil {
			return util.Empty{}, err
		}

		return ucxStackDataAppend(info.Owner, request)
	})

	ucxapi.StackConfirm.HandlerProxy(proxy, func(ctx context.Context, request fnd.FindByStringId) (util.Empty, error) {
		if err := confirmStack(request.Id); err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

	ucxapi.StackHeartbeat.HandlerProxy(proxy, func(ctx context.Context, request fnd.FindByStringId) (util.Empty, error) {
		if err := renewStackLease(request.Id); err != nil {
			return util.Empty{}, err
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

func ucxOnConnectJob(conn *ws.Conn) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	proxy := ucx.NewProxy("ws://pending")
	info := orcapi.AppUcxConnectJobProviderRequest{}

	proxy.RegisterUpstreamSelector(func(ctx context.Context, downstreamToken string, downstreamSysHello string) ucx.ProxyUpstreamSelection {
		if !ucxDownstreamTokenValid(downstreamToken) {
			log.Warn("UCX provider job: downstream token was not accepted")
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		if err := json.Unmarshal([]byte(downstreamSysHello), &info); err != nil {
			log.Warn("UCX provider job: invalid syshello payload: %v", err)
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		if info.Port < 0 || info.Port > 65535 {
			log.Warn("UCX provider job: invalid upstream port: %d", info.Port)
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		upstreamUrl, err := ucxResolveJobUpstream(info.Job, info.Port)
		if err != nil {
			log.Warn("UCX provider job: failed to establish upstream: %v", err)
			return ucx.ProxyUpstreamSelection{Allowed: false}
		}

		return ucx.ProxyUpstreamSelection{
			Allowed:          true,
			UpstreamUrl:      upstreamUrl,
			UpstreamToken:    job_introspection.UcxSessionToken(info.Job.Id),
			UpstreamSysHello: downstreamSysHello,
		}
	})

	ucxapi.StackDataWrite.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.StackDataWriteRequest) (util.Empty, error) {
		stackId := strings.TrimSpace(info.Job.Specification.Labels[orcapi.ResourceLabelStackInstance])
		if stackId == "" {
			return util.Empty{}, fmt.Errorf("job has no stack instance")
		}

		if request.InstanceId != stackId {
			return util.Empty{}, fmt.Errorf("invalid stack instance")
		}

		return ucxStackDataWrite(info.Job.Owner, request)
	})

	ucxapi.StackDataAppend.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.StackDataAppendRequest) (util.Empty, error) {
		stackId := strings.TrimSpace(info.Job.Specification.Labels[orcapi.ResourceLabelStackInstance])
		if stackId == "" {
			return util.Empty{}, fmt.Errorf("job has no stack instance")
		}

		if request.InstanceId != stackId {
			return util.Empty{}, fmt.Errorf("invalid stack instance")
		}

		return ucxStackDataAppend(info.Job.Owner, request)
	})

	ucxapi.IM.HandlerProxy(proxy, func(ctx context.Context, request ucxapi.Message) (ucxapi.Message, error) {
		log.Info("Got a job message from '%#v': %s", info.Job.Owner, request.Message)
		return ucxapi.Message{"Hello from the provider job session!"}, nil
	})

	if err := proxy.Run(ctx, conn); err != nil {
		log.Warn("UCX provider job proxy failure: %v", err)
	}
}

func ucxResolveJobUpstream(job orcapi.Job, port int) (string, error) {
	if backendIsKubevirt(&job) {
		return kubevirt.ResolveUcxJobSessionUpstream(&job, port)
	}

	if backendIsContainers(&job) {
		return containers.ResolveUcxJobSessionUpstream(&job, port)
	}

	return containers.ResolveUcxJobSessionUpstream(&job, port)
}

func ucxStackDataWrite(owner orcapi.ResourceOwner, request ucxapi.StackDataWriteRequest) (util.Empty, error) {
	return ucxStackDataWriteBytes(owner, request.InstanceId, request.Path, []byte(request.Data), request.Perm, unix.O_TRUNC, request.Atomic)
}

func ucxStackDataAppend(owner orcapi.ResourceOwner, request ucxapi.StackDataAppendRequest) (util.Empty, error) {
	return ucxStackDataWriteBytes(owner, request.InstanceId, request.Path, request.Data, request.Perm, unix.O_APPEND, false)
}

func ucxStackDataWriteBytes(owner orcapi.ResourceOwner, instanceId string, path string, data []byte, perm uint32, writeFlag int, atomicWrite bool) (util.Empty, error) {
	if atomicWrite && writeFlag != unix.O_TRUNC {
		return util.Empty{}, fmt.Errorf("atomic write is not supported for append")
	}

	if atomicWrite {
		if len(data) >= 1024*1024 {
			return util.Empty{}, fmt.Errorf("input data is too large for atomic write")
		}
	} else if len(data) >= 1024*64 {
		return util.Empty{}, fmt.Errorf("input data is too large")
	}

	internalPathMemberFiles, _, err := filesystem.InitializeMemberFiles(owner.CreatedBy, owner.Project)
	if err != nil {
		return util.Empty{}, err.AsError()
	}

	requestedPath := filepath.Join(internalPathMemberFiles, "Jobs", "Stacks", instanceId)
	err = filesystem.DoCreateFolder(requestedPath)
	if err != nil {
		return util.Empty{}, err.AsError()
	}

	pathComponents := util.Components(path)
	for _, comp := range pathComponents {
		if comp != "." && comp != ".." {
			requestedPath = filepath.Join(requestedPath, comp)
		}
	}

	if atomicWrite {
		if writeErr := filesystem.WriteFileAtomic(requestedPath, data, perm); writeErr != nil {
			return util.Empty{}, writeErr.AsError()
		}
		return util.Empty{}, nil
	}

	parentPath := util.Parent(requestedPath)
	if err = filesystem.DoCreateFolder(parentPath); err != nil {
		return util.Empty{}, err.AsError()
	}

	file, ok := filesystem.OpenFile(requestedPath, unix.O_CREAT|unix.O_WRONLY|writeFlag, perm)
	if !ok {
		return util.Empty{}, fmt.Errorf("unable to write data at: %s", requestedPath)
	}

	defer util.SilentClose(file)

	_, gerr := file.Write(data)
	if gerr != nil {
		return util.Empty{}, fmt.Errorf("unable to write data: %s", gerr)
	}

	return util.Empty{}, nil
}

func ensureUcxBackendAndResolveUpstream(ctx context.Context, app *orcapi.Application) (string, string, error) {
	namespace := shared.ServiceConfig.Compute.TaskNamespace
	deploymentName := ucxDeploymentName(app.Metadata.Name, app.Metadata.Version)
	authToken, err := ucxCreationUiToken(ctx, namespace, deploymentName)
	if err != nil {
		log.Warn("UCX provider: failed to resolve auth token for %s/%s: %v", namespace, deploymentName, err)
		return "", "", err
	}
	serviceName := deploymentName
	selector := ucxSelectorLabels(deploymentName)
	inDevelopmentMode := ucxDevelopmentModePath(app)
	cacheCurrentPath := util.OptNone[string]()
	if !inDevelopmentMode.Present {
		if app.Invocation.Ucx.Present && app.Invocation.Ucx.Value.Executable.Present {
			path, err := ucxdelivery.ExecutablePath(app.Metadata.Name, app.Metadata.Version)
			if err != nil {
				return "", "", err
			}
			cacheCurrentPath.Set(path)
		}
	}

	isRunning, err := ucxDeploymentRunning(ctx, namespace, deploymentName)
	if err != nil {
		log.Warn("UCX provider: failed deployment lookup for %s/%s: %v", namespace, deploymentName, err)
		return "", "", err
	}

	if !isRunning {
		invocation, err := renderUcxInvocationScript(app, inDevelopmentMode.Present)
		if err != nil {
			return "", "", err
		}
		script := renderUcxRunnerScript(invocation, app.Metadata.Name, app.Metadata.Version, cacheCurrentPath.Present)

		image := strings.TrimSpace(app.Invocation.Tool.Tool.Value.Description.Image)
		if image == "" {
			return "", "", fmt.Errorf("resolved tool image is missing")
		}

		if err := ensureUcxService(ctx, namespace, serviceName, selector); err != nil {
			log.Warn("UCX provider: failed ensuring service %s/%s: %v", namespace, serviceName, err)
			return "", "", err
		}

		if err := ensureUcxDeployment(ctx, namespace, deploymentName, image, script, selector, inDevelopmentMode, cacheCurrentPath, app.Metadata.Name, app.Metadata.Version, authToken); err != nil {
			log.Warn("UCX provider: failed ensuring deployment %s/%s: %v", namespace, deploymentName, err)
			return "", "", err
		}

		authToken, err = readUcxDeploymentAuthToken(ctx, namespace, deploymentName)
		if err != nil {
			log.Warn("UCX provider: failed reading auth token for %s/%s: %v", namespace, deploymentName, err)
			return "", "", err
		}
	} else {
		if err := ensureUcxService(ctx, namespace, serviceName, selector); err != nil {
			log.Warn("UCX provider: failed ensuring service %s/%s: %v", namespace, serviceName, err)
			return "", "", err
		}

		if err := ensureUcxDeploymentAuthToken(ctx, namespace, deploymentName, authToken); err != nil {
			log.Warn("UCX provider: failed ensuring auth token for %s/%s: %v", namespace, deploymentName, err)
			return "", "", err
		}
	}

	if err := waitForUcxDeploymentReady(ctx, namespace, deploymentName, 90*time.Second); err != nil {
		log.Warn("UCX provider: deployment did not become ready %s/%s: %v", namespace, deploymentName, err)
		return "", "", err
	}

	if util.DevelopmentModeEnabled() {
		podName, err := findReadyUcxPod(ctx, namespace, selector)
		if err != nil {
			log.Warn("UCX provider: failed finding ready pod in dev mode for %s/%s: %v", namespace, deploymentName, err)
			return "", "", err
		}

		tunnelPort := shared.EstablishTunnelEx(podName, namespace, int(ucxBackendPort))
		return fmt.Sprintf("ws://127.0.0.1:%d/", tunnelPort), authToken, nil
	}

	return fmt.Sprintf("ws://%s.%s.svc.cluster.local:%d/", serviceName, namespace, ucxBackendPort), authToken, nil
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
	cacheCurrentPath util.Option[string],
	appName string,
	appVersion string,
	authToken string,
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

	if cacheCurrentPath.Present {
		cacheParent := filepath.Dir(cacheCurrentPath.Value)
		cacheSubPath, ok := strings.CutPrefix(filepath.Clean(cacheParent), filepath.Clean(shared.ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			return fmt.Errorf("UCX executable cache path is not inside the provider filesystem")
		}

		volumes = append(volumes, k8score.Volume{
			Name: "ucloud-ucx-cache",
			VolumeSource: k8score.VolumeSource{
				PersistentVolumeClaim: &k8score.PersistentVolumeClaimVolumeSource{
					ClaimName: shared.ServiceConfig.FileSystem.ClaimName,
				},
			},
		})

		volumeMounts = append(volumeMounts, k8score.VolumeMount{
			Name:      "ucloud-ucx-cache",
			MountPath: "/opt/ucloud-ucx-cache",
			SubPath:   cacheSubPath,
			ReadOnly:  true,
		})
	}

	deployment := &appsv1.Deployment{
		ObjectMeta: meta.ObjectMeta{
			Name:      name,
			Namespace: namespace,
			Annotations: map[string]string{
				ucxAuthTokenAnnotation: authToken,
			},
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
							Env: []k8score.EnvVar{
								{Name: "UCX_PORT", Value: fmt.Sprint(ucxBackendPort)},
								{Name: "UCLOUD_UCX_APP_NAME", Value: appName},
								{Name: "UCLOUD_UCX_APP_VERSION", Value: appVersion},
								{Name: "UCX_AUTH_TOKEN", Value: authToken},
							},
							VolumeMounts: volumeMounts,
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

	updated := false
	if existing.Annotations == nil {
		existing.Annotations = map[string]string{}
	}
	if existing.Annotations[ucxAuthTokenAnnotation] == "" {
		existing.Annotations[ucxAuthTokenAnnotation] = authToken
		updated = true
	}

	if existing.Spec.Replicas == nil || *existing.Spec.Replicas != 1 {
		existing.Spec.Replicas = util.Pointer[int32](1)
		updated = true
	}

	effectiveToken := existing.Annotations[ucxAuthTokenAnnotation]
	hasAuthEnv := false
	for i := range existing.Spec.Template.Spec.Containers {
		for _, env := range existing.Spec.Template.Spec.Containers[i].Env {
			if env.Name == "UCX_AUTH_TOKEN" {
				hasAuthEnv = true
				break
			}
		}
	}
	if !hasAuthEnv && len(existing.Spec.Template.Spec.Containers) > 0 {
		existing.Spec.Template.Spec.Containers[0].Env = append(
			existing.Spec.Template.Spec.Containers[0].Env,
			k8score.EnvVar{Name: "UCX_AUTH_TOKEN", Value: effectiveToken},
		)
		updated = true
	}

	if updated {
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

func renderUcxRunnerScript(invocation string, appName string, appVersion string, useVerifiedCache bool) string {
	if !useVerifiedCache {
		return invocation
	}

	return fmt.Sprintf(`set -euo pipefail
WATCHED=/opt/ucloud-ucx-cache/current
RUNTIME_DIR=/tmp/ucloud-ucx
RUNTIME_BIN="$RUNTIME_DIR/current"
mkdir -p "$RUNTIME_DIR"

file_state() {
  if [ ! -f "$WATCHED" ]; then
    printf 'missing'
    return
  fi
  stat -c '%%s %%Y' "$WATCHED"
}

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
  export UCX_PORT=%d
  export UCLOUD_UCX_APP_NAME=%s
  export UCLOUD_UCX_APP_VERSION=%s
  bash -lc $UCX_EXECUTABLE %s &
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
`, ucxBackendPort, ctrl.EscapeBash(appName), ctrl.EscapeBash(appVersion), ctrl.EscapeBash(invocation))
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
