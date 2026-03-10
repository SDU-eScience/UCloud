package filesystem

import (
	"fmt"
	"net/http"
	"os"
	"sync/atomic"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var taskProcessorState struct {
	ProviderHostname string
	TaskToken        string
	LastStatus       atomic.Pointer[fnd.TaskStatus]
}

func TaskProcessor() {
	spec := Task2Spec{
		Type:             Task2SpecType(os.Getenv(taskEnvType)),
		Id:               os.Getenv(taskEnvId),
		Source:           os.Getenv(taskEnvSource),
		Destination:      os.Getenv(taskEnvDestination),
		TransferEndpoint: os.Getenv(taskEnvTransferEndpoint),
		TaskToken:        os.Getenv(taskEnvTaskToken),
	}

	if spec.Type == "" {
		log.Fatal("Failed to understand task environment")
		return
	}

	providerHostnameRaw, _ := os.ReadFile("/opt/ucloud/provider-hostname.txt")
	providerHostname := string(providerHostnameRaw)
	if providerHostname == "" {
		log.Fatal("Failed to get provider hostname")
		return
	}

	log.Info("Processing task: %#v", spec)
	log.Info("I can find the provider at: %s", providerHostname)

	taskProcessorState.ProviderHostname = providerHostname
	taskProcessorState.TaskToken = spec.TaskToken
	rpc.DefaultClient = &rpc.Client{
		RefreshToken: "",
		BasePath:     fmt.Sprintf("http://%s:8889", providerHostname),
		Client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}

	var err *util.HttpError
	switch spec.Type {
	case TaskSpecTypeCopy:
		err = task2ProcessCopy(spec)
	}

	last := taskProcessorState.LastStatus.Load()
	status := fnd.TaskStatus{}
	if last != nil {
		status = *last
	}
	if err == nil {
		status.State = fnd.TaskStateSuccess
	} else {
		status.State = fnd.TaskStateFailure
		status.Body.Set(err.Why)
	}
	taskProcessorPostUpdate(status)

	log.Info("Done")
}

func taskProcessorPostUpdate(status fnd.TaskStatus) {
	taskProcessorState.LastStatus.Store(&status)

	_, err := tasksInternalPostStatus.Invoke(tasksInternalPostStatusRequest{
		Token:  taskProcessorState.TaskToken,
		Update: status,
	})

	if err != nil {
		log.Info("Failed to post status update: %s", err)
	}
}

func taskProcessorIsCancelled() bool {
	return false // TODO
}

const (
	taskEnvType             = "UCLOUD_JOB_TYPE"
	taskEnvId               = "UCLOUD_JOB_ID"
	taskEnvSource           = "UCLOUD_JOB_SOURCE"
	taskEnvDestination      = "UCLOUD_JOB_DESTINATION"
	taskEnvTransferEndpoint = "UCLOUD_JOB_TRANSFER_ENDPOINT"
	taskEnvTaskToken        = "UCLOUD_JOB_TASK_TOKEN"
	taskEnvConflictPolicy   = "UCLOUD_JOB_CONFLICT_POLICY"
)
