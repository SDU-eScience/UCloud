package containers

import (
	"context"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var K8sClient *kubernetes.Clientset
var K8sConfig *rest.Config
var ServiceConfig *cfg.ServicesConfigurationKubernetes
var MachineSupport []orc.JobSupport
var Machines []apm.ProductV2

func Init() ctrl.JobsService {
	// Create a number of aliases for use in this package. These are all static by the time this function is called.
	K8sClient = shared.K8sClient
	K8sConfig = shared.K8sConfig
	ServiceConfig = shared.ServiceConfig
	MachineSupport = shared.MachineSupport
	Machines = shared.Machines

	return ctrl.JobsService{}
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

func StartScheduledJob(job *orc.Job, rank int, node string) {

}
