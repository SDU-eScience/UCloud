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
	"slices"
	"strings"
	"sync"
	"time"
	fnd "ucloud.dk/shared/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
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

	initSyncthing()
	initIntegratedTerminal()

	loadIApps()
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
func FindJobFolder(job *orc.Job) (string, *orc.Drive, error) {
	path, drive, err := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, util.OptStringIfNotEmpty(job.Owner.Project))
	if err != nil {
		return "", nil, err
	}

	jobFolderPath := filepath.Join(path, "Jobs", job.Status.ResolvedApplication.Metadata.Title, job.Id)
	_ = filesystem.DoCreateFolder(jobFolderPath)
	return jobFolderPath, drive, nil
}

type trackedLogFile struct {
	Rank    int
	Stdout  bool
	Channel util.Option[string]
	File    *os.File
}

type followMessageHandler struct {
	handler func(rank int, message string, channel string)
}

var followSubscriptions = map[string][]*followMessageHandler{}
var followSubscriptionsMutex = sync.Mutex{}

func subscribeToFollowUpdates(jobId string, onMessage *followMessageHandler) *followMessageHandler {
	followSubscriptionsMutex.Lock()
	defer followSubscriptionsMutex.Unlock()

	existing := followSubscriptions[jobId]
	newList := append(existing, onMessage)
	followSubscriptions[jobId] = newList
	return onMessage
}

func unsubscribeFromFollowUpdates(jobId string, onMessage *followMessageHandler) {
	followSubscriptionsMutex.Lock()
	defer followSubscriptionsMutex.Unlock()

	slice := followSubscriptions[jobId]
	idx := slices.Index(slice, onMessage)
	if idx != -1 {
		slice = append(slice[:idx], slice[idx+1:]...)
		if len(slice) == 0 {
			delete(followSubscriptions, jobId)
		} else {
			followSubscriptions[jobId] = slice
		}
	}
}

func dispatchNonPersistentFollowMessage(jobId string, rank int, message string, channel string) {
	followSubscriptionsMutex.Lock()
	handlers := followSubscriptions[jobId]
	for _, h := range handlers {
		go h.handler(rank, message, channel)
	}
	followSubscriptionsMutex.Unlock()
}

func follow(session *ctrl.FollowJobSession) {
	logFiles := map[string]trackedLogFile{}

	messageHandler := &followMessageHandler{}
	messageHandler.handler = func(rank int, message string, channel string) {
		session.EmitLogs(rank, util.OptValue(message), util.OptNone[string](), util.OptValue(channel))
	}

	subscribeToFollowUpdates(session.Job.Id, messageHandler)
	defer unsubscribeFromFollowUpdates(session.Job.Id, messageHandler)

	job, ok := ctrl.RetrieveJob(session.Job.Id)
	if !ok {
		return
	}

	jobFolder, _, err := FindJobFolder(job)
	if err != nil {
		return
	}

	trackFile := func(baseName string, file trackedLogFile) {
		_, exists := logFiles[baseName]

		if !exists {
			stdout, ok1 := filesystem.OpenFile(filepath.Join(jobFolder, baseName), unix.O_RDONLY, 0)
			if ok1 {
				file.File = stdout
				logFiles[baseName] = file
			}
		}
	}

	trackAllFiles := func() {
		for rank := 0; rank < job.Specification.Replicas; rank++ {
			baseName := fmt.Sprintf("stdout-%d.log", rank)
			trackFile(baseName, trackedLogFile{
				Rank:   rank,
				Stdout: true,
			})
		}

		trackFile(".ucviz-ui", trackedLogFile{
			Rank:    0,
			Stdout:  true,
			Channel: util.OptValue("ui"),
		})

		trackFile(".ucviz-data", trackedLogFile{
			Rank:    0,
			Stdout:  true,
			Channel: util.OptValue("data"),
		})
	}

	for util.IsAlive && *session.Alive {
		job, ok := ctrl.RetrieveJob(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			time.Sleep(1 * time.Second)
			continue
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

		trackAllFiles()

		for _, logFile := range logFiles {
			now := time.Now()
			deadline := now.Add(5 * time.Microsecond)
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

				session.EmitLogs(logFile.Rank, stdout, stderr, logFile.Channel)
			}
		}

		time.Sleep(15 * time.Millisecond)
	}
}

func terminate(request ctrl.JobTerminateRequest) error {
	// Delete pods
	// -----------------------------------------------------------------------------------------------------------------
	// NOTE(Dan): Helper resources (e.g. Service) have a owner reference on the pod. For this reason, we do not need to
	// delete this directly.
	for rank := 0; rank < request.Job.Specification.Replicas; rank++ {
		ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(10*time.Second))
		podName := idAndRankToPodName(request.Job.Id, rank)

		// NOTE(Dan): JobUpdateBatch and monitoring logic will aggressively get rid of pods that don't belong in
		// the namespace and as such we don't have to worry about failures here.
		_ = K8sClient.CoreV1().Pods(Namespace).Delete(ctx, podName, meta.DeleteOptions{
			GracePeriodSeconds: util.Pointer[int64](1),
		})

		cancel()
	}

	// Unbinding IP and port assignments
	// -----------------------------------------------------------------------------------------------------------------
	ctrl.UnbindIpsFromJob(request.Job)
	shared.ClearAssignedSshPort(request.Job)

	// Cleaning up mount dirs
	// -----------------------------------------------------------------------------------------------------------------
	internalJobFolder, _, err := FindJobFolder(request.Job)
	if err == nil {
		mounts, _ := calculateMounts(request.Job, internalJobFolder)
		if len(mounts.Folders) > 0 {
			jobFolder, ok := filesystem.OpenFile(internalJobFolder, os.O_RDONLY, 0)
			if ok {
				defer util.SilentClose(jobFolder)

				for _, folder := range mounts.Folders {
					if folderName, isMountedInJobFolder := strings.CutPrefix(folder.PodPath, "/work/"); isMountedInJobFolder {
						if !strings.Contains(folderName, "/") {
							child, err := filesystem.FileOpenAt(jobFolder, folderName, os.O_RDONLY, 0)
							if err == nil {
								folderChildren, err := child.Readdirnames(0)
								util.SilentClose(child)

								if err == nil && len(folderChildren) == 0 {
									_ = filesystem.DoDeleteFile(filepath.Join(internalJobFolder, folderName))
								} else {
									log.Info("Refusing to delete %v it has children.", filepath.Join(internalJobFolder, folderName))
								}
							}
						}
					}
				}
			}
		}
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

func JobAnnotations(job *orc.Job, rank int) map[string]string {
	podName := idAndRankToPodName(job.Id, rank)
	timeout, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	pod, err := K8sClient.CoreV1().Pods(Namespace).Get(timeout, podName, meta.GetOptions{})
	if err == nil {
		return pod.Annotations
	} else {
		return nil
	}
}

const (
	ContainerUserJob = "user-job"
)
