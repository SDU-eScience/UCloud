package filesystem

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"sync/atomic"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type tasksInternalPostStatusRequest struct {
	Token  string
	Update fndapi.TaskStatus
}

var tasksInternalPostStatus = rpc.Call[tasksInternalPostStatusRequest, util.Empty]{
	BaseContext: "internal/tasks",
	Convention:  rpc.ConventionUpdate,
	Operation:   "post",
	Roles:       rpc.RolesPublic,
	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
		RetentionDays: util.OptValue(1),
	},
}

var taskProcessorState struct {
	ProviderHostname string
	TaskToken        string
	LastStatus       atomic.Pointer[fndapi.TaskStatus]
}

func TaskProcessor() {
	spec := TaskSpec{
		Type:             TaskType(os.Getenv(taskEnvType)),
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

	log.Info("Processing task: %v %v %v", spec.Type, spec.Source, spec.Destination)
	log.Info("I can find the provider at: %s", providerHostname)

	taskProcessorState.ProviderHostname = providerHostname
	taskProcessorState.TaskToken = spec.TaskToken
	rpc.DefaultClient = &rpc.Client{
		RefreshToken: "",
		BasePath:     fmt.Sprintf("http://%s:42000", providerHostname),
		Client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}

	var err *util.HttpError
	switch spec.Type {
	case TaskTypeCopy:
		err = task2ProcessCopy(spec)
	case TaskTypeTransfer:
		err = task2ProcessTransfer(spec)
	}

	last := taskProcessorState.LastStatus.Load()
	status := fndapi.TaskStatus{}
	if last != nil {
		status = *last
	}
	if err == nil {
		status.State = fndapi.TaskStateSuccess
	} else {
		status.State = fndapi.TaskStateFailure
		status.Body.Set(err.Why)
	}
	taskProcessorPostUpdate(status)

	log.Info("Done")
}

func taskProcessorPostUpdate(status fndapi.TaskStatus) {
	taskProcessorState.LastStatus.Store(&status)

	_, err := tasksInternalPostStatus.Invoke(tasksInternalPostStatusRequest{
		Token:  taskProcessorState.TaskToken,
		Update: status,
	})

	if err != nil {
		log.Info("Failed to post status update: %s", err)
	}
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
