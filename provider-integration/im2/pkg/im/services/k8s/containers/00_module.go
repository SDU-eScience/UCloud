package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"os"
	"path/filepath"
	"strings"
	"time"
	fnd "ucloud.dk/pkg/foundation"
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

	LoadNixModules()

	return ctrl.JobsService{
		Terminate:                terminate,
		Extend:                   extend,
		RetrieveProducts:         nil, // handled by main instance
		Follow:                   follow,
		HandleShell:              handleShell,
		ServerFindIngress:        serverFindIngress,
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

type trackedLogFile struct {
	Rank   int
	Stdout bool
	File   *os.File
}

func follow(session *ctrl.FollowJobSession) {
	var logFiles []trackedLogFile

	for util.IsAlive && *session.Alive {
		job, ok := ctrl.RetrieveJob(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			time.Sleep(1 * time.Second)
			continue
		}

		jobFolder, err := FindJobFolder(job)
		if err != nil {
			time.Sleep(1 * time.Second)
			continue
		}

		for rank := 0; rank < job.Specification.Replicas; rank++ {
			stdout, ok1 := filesystem.OpenFile(filepath.Join(jobFolder, fmt.Sprintf("stdout-%d.log", rank)), unix.O_RDONLY, 0)
			if ok1 {
				logFiles = append(logFiles, trackedLogFile{
					Rank:   rank,
					Stdout: true,
					File:   stdout,
				})
			}
		}

		break
	}

	readBuffer := make([]byte, 1024*4)

	// Watch log files
	for util.IsAlive && *session.Alive {
		job, ok := ctrl.RetrieveJob(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			break
		}

		for _, logFile := range logFiles {
			now := time.Now()
			deadline := now.Add(5 * time.Millisecond)
			_ = logFile.File.SetReadDeadline(deadline)

			bytesRead, _ := logFile.File.Read(readBuffer)
			if bytesRead > 0 {
				message := string(readBuffer[:bytesRead])
				var stdout util.Option[string]
				var stderr util.Option[string]
				if logFile.Stdout {
					stdout.Set(message)
				} else {
					stderr.Set(message)
				}

				session.EmitLogs(logFile.Rank, stdout, stderr)
			}
		}

		time.Sleep(200 * time.Millisecond)
	}
}

func terminate(request ctrl.JobTerminateRequest) error {
	for rank := 0; rank < request.Job.Specification.Replicas; rank++ {
		ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(10*time.Second))
		podName := idAndRankToPodName(request.Job.Id, rank)

		// NOTE(Dan): JobUpdateBatch and monitoring logic will aggressively get rid of pods that don't belong in
		// the namespace and as such we don't have to worry about failures here.
		_ = K8sClient.CoreV1().Pods(Namespace).Delete(ctx, podName, meta.DeleteOptions{})

		cancel()
	}

	if !request.IsCleanup {
		// NOTE(Dan): There is no need to run this branch if we are cleaning up for an already terminated job
		// (terminated according to the Core).

		// NOTE(Dan): Track the new job with a status of success immediately. This is required to ensure that the
		// JobUpdateBatch understands that the job is definitely not supposed to be running. This is mostly relevant for
		// jobs that are still in the queue and would trigger the "too early to remove" check.
		job, ok := ctrl.RetrieveJob(request.Job.Id)
		if !ok {
			job = request.Job
		}

		copied := *job
		copied.Status.State = orc.JobStateSuccess
		copied.Updates = append(copied.Updates, orc.JobUpdate{
			State: util.OptValue(orc.JobStateSuccess),
		})
		ctrl.TrackNewJob(copied)

		// NOTE(Dan): Failure in this function will be automatically retried by the JobUpdateBatch/monitoring logic.
		_ = orc.UpdateJobs(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.JobUpdate]]{
			Items: []orc.ResourceUpdateAndId[orc.JobUpdate]{
				{
					Id: job.Id,
					Update: orc.JobUpdate{
						State: util.OptValue(orc.JobStateSuccess),
					},
				},
			},
		})
	}

	return nil
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	return RequestNixParameter(app)
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (ctrl.ConfiguredWebSession, error) {
	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(10*time.Second))
	defer cancel()

	podName := idAndRankToPodName(job.Id, rank)
	pod, err := K8sClient.CoreV1().Pods(Namespace).Get(ctx, podName, meta.GetOptions{})
	if err != nil {
		return ctrl.ConfiguredWebSession{}, util.ServerHttpError("Could not find target job, is it still running?")
	}

	app := &job.Status.ResolvedApplication.Invocation

	port := app.Web.Port
	if app.ApplicationType == orc.ApplicationTypeVnc {
		port = app.Vnc.Port
	}

	if target.Present {
		updates := job.Updates
		length := len(updates)
		for i := 0; i < length; i++ {
			update := &job.Updates[i]
			if strings.HasPrefix(update.Status.Value, "Target: ") {
				asJson := strings.TrimPrefix(update.Status.Value, "Target: ")
				var dynTarget orc.DynamicTarget
				err := json.Unmarshal([]byte(asJson), &dynTarget)
				if err != nil {
					continue
				}

				if dynTarget.Target == target.Value && dynTarget.Rank == rank {
					port = uint16(dynTarget.Port)
				}
			}
		}
	}

	address := cfg.HostInfo{
		Address: pod.Status.PodIP,
		Port:    int(port),
	}

	if !shared.K8sInCluster {
		address.Address = "127.0.0.1"
		address.Port = establishTunnel(podName, int(port))
	}

	return ctrl.ConfiguredWebSession{
		Host: address,
	}, nil
}

func serverFindIngress(job *orc.Job, rank int, suffix util.Option[string]) ctrl.ConfiguredWebIngress {
	return ctrl.ConfiguredWebIngress{
		IsPublic:     false,
		TargetDomain: ServiceConfig.Compute.Web.Prefix + job.Id + "-" + fmt.Sprint(rank) + suffix.Value + ServiceConfig.Compute.Web.Suffix,
	}
}
