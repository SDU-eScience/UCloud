package containers

import (
	"context"
	"encoding/json"
	"time"

	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	k8serr "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	ctrl "ucloud.dk/pkg/controller"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type ContainerIAppHandler struct {
	Flags         ctrl.IntegratedApplicationFlag
	BeforeRestart func(job *orc.Job) *util.HttpError

	ValidateConfiguration        func(job *orc.Job, configuration json.RawMessage) *util.HttpError
	ResetConfiguration           func(job *orc.Job, configuration json.RawMessage) (json.RawMessage, *util.HttpError)
	RetrieveDefaultConfiguration func(owner orc.ResourceOwner) json.RawMessage
	RetrieveLegacyConfiguration  func(owner orc.ResourceOwner) util.Option[json.RawMessage]

	ShouldRun func(job *orc.Job, configuration json.RawMessage) bool

	MutateJobNonPersistent          func(job *orc.Job, configuration json.RawMessage)
	MutatePod                       func(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError
	MutateService                   func(job *orc.Job, configuration json.RawMessage, svc *core.Service, pod *core.Pod) *util.HttpError
	MutateNetworkPolicy             func(job *orc.Job, configuration json.RawMessage, np *networking.NetworkPolicy, pod *core.Pod) *util.HttpError
	MutateJobSpecBeforeRegistration func(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError

	BeforeMonitor func(pods []*core.Pod, jobs map[string]*orc.Job, allActiveIApps map[string]ctrl.IAppRunningConfiguration)
}

var IApps = map[string]ContainerIAppHandler{}

func loadIApps() {
	for appName, handler := range IApps {
		ctrl.IntegratedApplications[appName] = containerIAppBridge(handler)
	}
}

func containerIAppBridge(handler ContainerIAppHandler) ctrl.IntegratedApplicationHandler {
	return ctrl.IntegratedApplicationHandler{
		Flags: handler.Flags,

		UpdateConfiguration: func(job *orc.Job, etag string, configuration json.RawMessage) *util.HttpError {
			// This function only needs to perform validation. Configuration updates are automatically triggered by
			// the reconciliation logic of the monitoring loop.

			pod, err := iappFindPod(job)
			if err != nil {
				return err
			}

			needsUpdate := !pod.Present

			if pod.Present {
				tag := util.OptMapGet(pod.Value.Annotations, IAppAnnotationEtag)
				needsUpdate = !tag.Present || tag.Value != etag
			}

			if needsUpdate {
				return handler.ValidateConfiguration(job, configuration)
			} else {
				return nil
			}
		},

		ResetConfiguration: func(job *orc.Job, configuration json.RawMessage) (json.RawMessage, *util.HttpError) {
			return handler.ResetConfiguration(job, configuration)
		},

		RestartApplication: func(job *orc.Job) *util.HttpError {
			pod, err := iappFindPod(job)
			if err != nil {
				return err
			}

			if pod.Present {
				timeout, cancelFunc := context.WithTimeout(context.Background(), 15*time.Second)
				defer cancelFunc()

				_ = K8sClient.CoreV1().Pods(Namespace).Delete(timeout, pod.Value.Name, metav1.DeleteOptions{
					GracePeriodSeconds: util.Pointer(int64(0)),
					PropagationPolicy:  util.Pointer(metav1.DeletePropagationBackground),
				})
			}

			return nil
		},

		RetrieveLegacyConfiguration: func(owner orc.ResourceOwner) util.Option[json.RawMessage] {
			if handler.RetrieveLegacyConfiguration == nil {
				return util.OptNone[json.RawMessage]()
			} else {
				return handler.RetrieveLegacyConfiguration(owner)
			}
		},

		RetrieveDefaultConfiguration: func(owner orc.ResourceOwner) json.RawMessage {
			return handler.RetrieveDefaultConfiguration(owner)
		},

		MutateSpecBeforeRegistration: func(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError {
			if handler.MutateJobSpecBeforeRegistration != nil {
				return handler.MutateJobSpecBeforeRegistration(owner, spec)
			} else {
				return nil
			}
		},
	}
}

func iappFindPod(job *orc.Job) (util.Option[*core.Pod], *util.HttpError) {
	podName := idAndRankToPodName(job.Id, 0)
	timeout, cancelFunc := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelFunc()

	pod, err := K8sClient.CoreV1().Pods(Namespace).Get(timeout, podName, metav1.GetOptions{})
	if err != nil {
		if k8serr.IsNotFound(err) {
			return util.OptNone[*core.Pod](), nil
		} else {
			return util.OptNone[*core.Pod](), util.HttpErrorFromErr(err)
		}
	}

	return util.OptValue[*core.Pod](pod), nil
}

const (
	IAppAnnotationEtag = "ucloud.dk/iapp-etag"
	IAppAnnotationName = "ucloud.dk/iapp-name"
)
