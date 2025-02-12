package containers

import (
	"context"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"path/filepath"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var K8sClient *kubernetes.Clientset
var K8sConfig *rest.Config
var ServiceConfig *cfg.ServicesConfigurationKubernetes
var Namespace string
var ExecCodec runtime.ParameterCodec

func Init() ctrl.JobsService {
	// Create a number of aliases for use in this package. These are all static by the time this function is called.
	K8sConfig = shared.K8sConfig
	K8sClient = shared.K8sClient
	ServiceConfig = shared.ServiceConfig
	Namespace = ServiceConfig.Compute.Namespace

	scheme := runtime.NewScheme()
	_ = core.AddToScheme(scheme)
	ExecCodec = runtime.NewParameterCodec(scheme)

	return ctrl.JobsService{
		Terminate:                terminate,
		Extend:                   nil,
		RetrieveProducts:         nil, // handled by main instance
		Follow:                   follow,
		HandleShell:              handleShell,
		ServerFindIngress:        nil,
		OpenWebSession:           openWebSession,
		RequestDynamicParameters: requestDynamicParameters,
	}
}

func findPodByJobIdAndRank(jobId string, rank int) util.Option[*core.Pod] {
	result, err := K8sClient.CoreV1().Pods(ServiceConfig.Compute.Namespace).Get(
		context.TODO(),
		idAndRankToPodName(jobId, rank),
		meta.GetOptions{},
	)

	if err != nil {
		return util.Option[*core.Pod]{}
	} else {
		return util.OptValue(result)
	}
}

// FindJobFolder finds the most relevant job folder for a given job. The returned path will be internal.
func FindJobFolder(job *orc.Job) (string, error) {
	path, err := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, util.OptStringIfNotEmpty(job.Owner.Project))
	if err != nil {
		return "", err
	}

	jobFolderPath := filepath.Join(path, "Jobs", job.Status.ResolvedApplication.Metadata.Title, job.Id)
	_ = filesystem.DoCreateFolder(jobFolderPath)
	return jobFolderPath, nil
}

func follow(session *ctrl.FollowJobSession) {
	// Keep alive to make the Core happy.
	for *session.Alive {
		time.Sleep(100 * time.Millisecond)
	}
}

func terminate(request ctrl.JobTerminateRequest) error {
	/*
		name := vmName(request.Job.Id, 0)
		err := KubevirtClient.VirtualMachine(Namespace).Delete(context.TODO(), name, metav1.DeleteOptions{})
		if err != nil {
			log.Info("Failed to delete VM: %v", err)
			return util.ServerHttpError("Failed to delete VM")
		}
		return nil
	*/
	return nil
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	return nil
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (ctrl.ConfiguredWebSession, error) {
	return ctrl.ConfiguredWebSession{
		Flags: ctrl.RegisteredIngressFlagsVnc | ctrl.RegisteredIngressFlagsNoGatewayConfig,
	}, nil
}
