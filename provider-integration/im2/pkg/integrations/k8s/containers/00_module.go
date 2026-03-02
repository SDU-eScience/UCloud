package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var K8sClient *kubernetes.Clientset
var K8sConfig *rest.Config
var ServiceConfig *config.ServicesConfigurationKubernetes
var Namespace string
var ExecCodec runtime.ParameterCodec

func Init() controller.JobsService {
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

	return controller.JobsService{
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

func FindJobFolder(job *orc.Job) (string, *orc.Drive, *util.HttpError) {
	return FindJobFolderEx(job, 0)
}

const (
	FindJobFolderNoInitFolder int = 1 << iota
)

// FindJobFolderEx finds the most relevant job folder for a given job. The returned path will be internal.
func FindJobFolderEx(job *orc.Job, flags int) (string, *orc.Drive, *util.HttpError) {
	path, drive, err := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, job.Owner.Project)
	if err != nil {
		return "", nil, err
	}

	title := job.Status.ResolvedApplication.Value.Metadata.Title
	if job.Status.ResolvedApplication.Value.Metadata.Name == "unknown" && job.Specification.Name != "" {
		title = job.Specification.Name
	}

	jobFolderPath := filepath.Join(path, "Jobs", title, job.Id)
	if flags&FindJobFolderNoInitFolder == 0 {
		_ = filesystem.DoCreateFolder(jobFolderPath)
	}
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

func follow(session *controller.FollowJobSession) {
	logFiles := map[string]trackedLogFile{}

	messageHandler := &followMessageHandler{}
	messageHandler.handler = func(rank int, message string, channel string) {
		session.EmitLogs(rank, util.OptValue(message), util.OptNone[string](), util.OptValue(channel))
	}

	subscribeToFollowUpdates(session.Job.Id, messageHandler)
	defer unsubscribeFromFollowUpdates(session.Job.Id, messageHandler)

	job, ok := controller.JobRetrieve(session.Job.Id)
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
				sinfo, err := stdout.Stat()
				if err == nil {
					if sinfo.Size() > 1024*256 {
						_, _ = stdout.Seek(sinfo.Size()-1024*256, io.SeekStart)
					}
				}
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
	}

	utilizationChannel := make(chan []float64)
	utilizationDataTracked := false
	var utilizationData *util.FsRingReader[[]float64]
	utilSerializer := util.FsRingSerializer[[]float64]{
		Deserialize: func(buf *util.UBufferReader) []float64 {
			result := make([]float64, 64) // NOTE(Dan): change ucmetrics if changing this
			for i := 0; i < 64; i++ {
				result[i] = buf.ReadF64()
			}
			return result
		},
	}

	for util.IsAlive && *session.Alive {
		job, ok := controller.JobRetrieve(session.Job.Id)
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
		job, ok := controller.JobRetrieve(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			break
		}

		trackAllFiles()
		if !utilizationDataTracked {
			path := filepath.Join(jobFolder, ".ucviz-utilization-data")
			ring, err := util.FsRingOpen(path, utilSerializer)
			if err == nil {
				utilizationData = ring
				utilizationDataTracked = true

				go func() {
					_ = ring.Follow(context.Background(), utilizationChannel, 256)
					util.SilentClose(ring)
				}()
			}
		}

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

		if utilizationData != nil {
		loop:
			for {
				select {
				case row := <-utilizationChannel:
					b := strings.Builder{}
					for i, elem := range row {
						if i > 0 {
							b.WriteString(",")
						}
						b.WriteString(fmt.Sprint(elem))
					}

					session.EmitLogs(0, util.OptValue(b.String()), util.OptNone[string](), util.OptValue("utilization-data"))

				default:
					break loop
				}
			}
		}

		time.Sleep(15 * time.Millisecond)
	}

	if utilizationData != nil {
		util.SilentClose(utilizationData)
	}
}

func terminate(request controller.JobTerminateRequest) *util.HttpError {
	// Delete pods
	// -----------------------------------------------------------------------------------------------------------------
	// NOTE(Dan): Helper resources (e.g. Service) have a owner reference on the pod. For this reason, we do not need to
	// delete this directly.
	for rank := 0; rank < request.Job.Specification.Replicas; rank++ {
		podName := idAndRankToPodName(request.Job.Id, rank)
		pod, ok := shared.JobPods.Retrieve(podName)

		// NOTE(Dan): Do not waste time on pods that we know are not in the system. The K8s API is really slow and this
		// can waste a lot of time if someone attempts to schedule a 1 million node job (which we cannot possibly host
		// anyway).
		if ok && pod.DeletionTimestamp == nil {
			ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(10*time.Second))
			// NOTE(Dan): JobUpdateBatch and monitoring logic will aggressively get rid of pods that don't belong in
			// the namespace and as such we don't have to worry about failures here.
			_ = K8sClient.CoreV1().Pods(Namespace).Delete(ctx, podName, meta.DeleteOptions{
				GracePeriodSeconds: util.Pointer[int64](1),
			})

			cancel()
		}
	}

	// Unbinding IP and port assignments
	// -----------------------------------------------------------------------------------------------------------------
	controller.PublicIpUnbindFromJob(request.Job)
	shared.ClearAssignedSshPort(request.Job)
	shared.RemoveFromQueue(request.Job.Id)

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
								info, err := child.Stat()
								if err == nil {
									if info.IsDir() {
										folderChildren, err := child.Readdirnames(0)
										util.SilentClose(child)

										if err == nil && len(folderChildren) == 0 {
											_ = filesystem.DoDeleteFile(filepath.Join(internalJobFolder, folderName))
										} else {
											log.Info("Refusing to delete %v it has children.", filepath.Join(internalJobFolder, folderName))
										}
									} else {
										if info.Size() == 0 {
											_ = filesystem.DoDeleteFile(filepath.Join(internalJobFolder, folderName))
										} else {
											log.Info("Refusing to delete %v it is not empty.", filepath.Join(internalJobFolder, folderName))
										}
									}
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
		job, ok := controller.JobRetrieve(request.Job.Id)
		if !ok {
			job = request.Job
		}

		copied := *job
		copied.Status.State = orc.JobStateSuccess
		copied.Updates = append(copied.Updates, orc.JobUpdate{
			State: util.OptValue(orc.JobStateSuccess),
		})
		controller.JobTrackNew(copied)

		// NOTE(Dan): Failure in this function will be automatically retried by the JobUpdateBatch/monitoring logic.
		_, _ = orc.JobsControlAddUpdate.Invoke(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.JobUpdate]]{
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
	param := orc.ApplicationParameterEnumeration(
		"ucMetricSampleRate",
		true,
		"Job report sample rate",
		"Sets sampling rate for resource utilization for the job report (default is 250 ms)",
		[]orc.EnumOption{
			{Name: "Do not sample", Value: "0ms"},
			{Name: "250 ms", Value: "250ms"},
			{Name: "500 ms", Value: "500ms"},
			{Name: "750 ms", Value: "750ms"},
			{Name: "1 s", Value: "1000ms"},
			{Name: "5 s", Value: "5000ms"},
			{Name: "10 s", Value: "10000ms"},
			{Name: "30 s", Value: "30000ms"},
			{Name: "1 minute", Value: "60000ms"},
			{Name: "2 minute", Value: "120000ms"},
		},
	)

	param.DefaultValue = json.RawMessage(`"250ms"`)

	return []orc.ApplicationParameter{param}
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (controller.ConfiguredWebSession, *util.HttpError) {
	podName := idAndRankToPodName(job.Id, rank)

	app := &job.Status.ResolvedApplication.Value.Invocation

	flags := controller.RegisteredIngressFlags(0)

	port := app.Web.Value.Port
	if app.ApplicationType == orc.ApplicationTypeVnc {
		port = app.Vnc.Value.Port
		flags = controller.RegisteredIngressFlagsVnc
	} else {
		flags = controller.RegisteredIngressFlagsWeb
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
					if dynTarget.Type == orc.InteractiveSessionTypeVnc {
						flags = controller.RegisteredIngressFlagsVnc
					} else {
						flags = controller.RegisteredIngressFlagsWeb
					}
				}
			}
		}
	}

	address := config.HostInfo{
		Address: jobHostName(job.Id, rank),
		Port:    int(port),
	}

	if !shared.K8sInCluster {
		address.Address = "127.0.0.1"
		address.Port = establishTunnel(podName, int(port))
		flags |= controller.RegisteredIngressFlagsNoPersist
	}

	return controller.ConfiguredWebSession{
		Host:  address,
		Flags: flags,
	}, nil
}

func serverFindIngress(job *orc.Job, rank int, suffix util.Option[string]) controller.ConfiguredWebIngress {
	for _, resource := range job.Specification.Resources {
		if resource.Type == orc.AppParameterValueTypeIngress {
			ingress := controller.LinkRetrieve(resource.Id)

			return controller.ConfiguredWebIngress{
				IsPublic:     true,
				TargetDomain: ingress.Specification.Domain,
			}
		}
	}

	return controller.ConfiguredWebIngress{
		IsPublic:     false,
		TargetDomain: ServiceConfig.Compute.Web.Prefix + job.Id + "-" + fmt.Sprint(rank) + suffix.Value + ServiceConfig.Compute.Web.Suffix,
	}
}

func extend(request orc.JobsProviderExtendRequestItem) *util.HttpError {
	// NOTE(Dan): Scheduler is automatically notified by the shared monitoring loop since it will forward the time
	// allocation from the job.
	alloc := request.Job.Specification.TimeAllocation
	if alloc.Present {
		return controller.JobTrackRawUpdates([]orc.ResourceUpdateAndId[orc.JobUpdate]{
			{
				Id: request.Job.Id,
				Update: orc.JobUpdate{
					NewTimeAllocation: util.OptValue(alloc.Value.ToMillis() + request.RequestedTime.ToMillis()),
				},
			},
		})
	} else {
		return nil
	}
}

func JobAnnotations(job *orc.Job, rank int) map[string]string {
	podName := idAndRankToPodName(job.Id, rank)
	pod, ok := shared.JobPods.Retrieve(podName)
	if ok {
		return pod.Annotations
	} else {
		return nil
	}
}

const (
	ContainerUserJob = "user-job"
)
