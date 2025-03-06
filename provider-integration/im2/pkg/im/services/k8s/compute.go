package k8s

import (
	"time"

	ws "github.com/gorilla/websocket"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/containers"
	"ucloud.dk/pkg/im/services/k8s/kubevirt"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var containerCpu ctrl.JobsService
var virtCpu ctrl.JobsService

func InitCompute() ctrl.JobsService {
	containerCpu = containers.Init()
	virtCpu = kubevirt.Init()

	go func() {
		for util.IsAlive {
			// TODO
			// TODO Need to resubmit old in-queue entries to the queue
			// TODO

			loopMonitoring()
			time.Sleep(100 * time.Millisecond)
		}
	}()

	return ctrl.JobsService{
		Submit:                   submit,
		Terminate:                terminate,
		Extend:                   extend,
		RetrieveProducts:         retrieveProducts,
		Follow:                   follow,
		HandleShell:              handleShell,
		ServerFindIngress:        serverFindIngress,
		OpenWebSession:           openWebSession,
		RequestDynamicParameters: requestDynamicParameters,
		Suspend:                  suspend,
		Unsuspend:                unsuspend,
		HandleBuiltInVnc:         handleBuiltInVnc,
		PublicIPs: ctrl.PublicIPService{
			Create:           createPublicIp,
			Delete:           deletePublicIp,
			RetrieveProducts: retrievePublicIpProducts,
		},
	}
}

func retrievePublicIpProducts() []orc.PublicIpSupport {
	return shared.IpSupport
}

func createPublicIp(ip *orc.PublicIp) error {
	return ctrl.AllocateIpAddress(ip)
}

func deletePublicIp(ip *orc.PublicIp) error {
	return ctrl.DeleteIpAddress(ip)
}

func retrieveProducts() []orc.JobSupport {
	return shared.MachineSupport
}

func backendByAppIsKubevirt(app *orc.Application) bool {
	return backendByApp(app) == &virtCpu
}

func backendByAppIsContainers(app *orc.Application) bool {
	return backendByApp(app) == &containerCpu
}

func backendByApp(app *orc.Application) *ctrl.JobsService {
	tool := &app.Invocation.Tool.Tool.Description
	switch tool.Backend {
	case orc.ToolBackendDocker:
		fallthrough
	case orc.ToolBackendSingularity:
		fallthrough
	case orc.ToolBackendNative:
		return &containerCpu
	case orc.ToolBackendVirtualMachine:
		return &virtCpu
	default:
		return &containerCpu
	}
}

func backendIsKubevirt(job *orc.Job) bool {
	return backend(job) == &virtCpu
}

func backendIsContainers(job *orc.Job) bool {
	return backend(job) == &containerCpu
}

func backend(job *orc.Job) *ctrl.JobsService {
	app := &job.Status.ResolvedApplication
	return backendByApp(app)
}

func submit(request ctrl.JobSubmitRequest) (util.Option[string], error) {
	if reason := IsJobLocked(request.JobToSubmit); reason.Present {
		return util.OptNone[string](), reason.Value.Err
	}

	shared.RequestSchedule(request.JobToSubmit)
	ctrl.TrackNewJob(*request.JobToSubmit)
	return util.OptNone[string](), nil
}

func terminate(request ctrl.JobTerminateRequest) error {
	return backend(request.Job).Terminate(request)
}

func extend(request ctrl.JobExtendRequest) error {
	return backend(request.Job).Extend(request)
}

func follow(session *ctrl.FollowJobSession) {
	backend(session.Job).Follow(session)
}

func handleShell(session *ctrl.ShellSession, cols int, rows int) {
	backend(session.Job).HandleShell(session, cols, rows)
}

func serverFindIngress(job *orc.Job, rank int, suffix util.Option[string]) ctrl.ConfiguredWebIngress {
	return backend(job).ServerFindIngress(job, rank, suffix)
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (ctrl.ConfiguredWebSession, error) {
	return backend(job).OpenWebSession(job, rank, target)
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	fn := backendByApp(app).RequestDynamicParameters
	if fn == nil {
		return nil
	} else {
		return fn(owner, app)
	}
}

func unsuspend(request ctrl.JobUnsuspendRequest) error {
	fn := backend(request.Job).Unsuspend
	if fn != nil {
		return fn(request)
	}
	return nil
}

func suspend(request ctrl.JobSuspendRequest) error {
	fn := backend(request.Job).Suspend
	if fn != nil {
		return fn(request)
	}
	return nil
}

func handleBuiltInVnc(job *orc.Job, rank int, conn *ws.Conn) {
	fn := backend(job).HandleBuiltInVnc
	if fn != nil {
		fn(job, rank, conn)
	}
}
