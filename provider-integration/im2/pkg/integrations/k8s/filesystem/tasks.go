package filesystem

import (
	"context"
	"fmt"
	"net/http"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"

	k8sbatch "k8s.io/api/batch/v1"
	k8score "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Task2SpecType string

const (
	TaskSpecTypeCopy     Task2SpecType = "copy"
	TaskSpecTypeTransfer Task2SpecType = "file_transfer"
)

type TaskMount struct {
	UCloudPath string
}

type TaskSpec struct {
	Type      Task2SpecType
	Id        string
	Mounts    []TaskMount
	TaskToken string

	Source           string // UCloud path prior to submission, mount path in job task
	Destination      string // UCloud path prior to submission, mount path in job task
	TransferEndpoint string
	ConflictPolicy   string

	CreationState struct {
		Username string
		Icon     string
	}
}

const (
	taskJobLabel        = "ucloud.dk/backgroundTask"
	taskStorageMountDir = "/mnt/storage"
)

var taskState struct {
	Mu    sync.RWMutex
	Cache map[string]int // token to ucloud task id
}

func taskGetUCloudTaskIdFromToken(token string) (int, bool) {
	taskState.Mu.RLock()
	taskId, ok := taskState.Cache[token]
	taskState.Mu.RUnlock()

	if !ok {
		taskState.Mu.Lock()
		taskId, ok = db.NewTx2(func(tx *db.Transaction) (int, bool) {
			row, ok := db.Get[struct {
				UCloudTaskId int
			}](
				tx,
				`
					select ucloud_task_id
					from k8s.tasks_v2
					where api_token = :tok
				`,
				db.Params{
					"tok": token,
				},
			)

			return row.UCloudTaskId, ok
		})

		if ok {
			taskState.Cache[token] = taskId
		}
		taskState.Mu.Unlock()
	}

	return taskId, ok
}

func taskCleanupByToken(token string) (string, bool) {
	_, ok := taskGetUCloudTaskIdFromToken(token)
	if ok {
		jobName, ok := db.NewTx2(func(tx *db.Transaction) (string, bool) {
			row, ok := db.Get[struct{ Id string }](
				tx,
				`
					delete from k8s.tasks_v2
					where api_token = :tok
					returning id
				`,
				db.Params{
					"tok": token,
				},
			)
			return row.Id, ok
		})

		taskState.Mu.Lock()
		delete(taskState.Cache, token)
		taskState.Mu.Unlock()
		return jobName, ok
	}

	return "", false
}

func initTasks2() {
	taskState.Cache = map[string]int{}

	tasksInternalPostStatus.Handler(func(info rpc.RequestInfo, request tasksInternalPostStatusRequest) (util.Empty, *util.HttpError) {
		ucloudTaskId, ok := taskGetUCloudTaskIdFromToken(request.Token)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "invalid token")
		}

		_, err := fndapi.TasksPostStatus.Invoke(fndapi.TasksPostStatusRequest{
			Update: fndapi.TasksPostStatusRequestUpdate{
				Id:         ucloudTaskId,
				ModifiedAt: fndapi.Timestamp(time.Now()),
				NewStatus:  request.Update,
			},
		})

		if err != nil {
			return util.Empty{}, err
		}

		if request.Update.State == fndapi.TaskStateSuccess || request.Update.State == fndapi.TaskStateFailure {
			taskCleanupByToken(request.Token)
		}

		return util.Empty{}, nil
	})

	tasksInternalIsCancelled.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (bool, *util.HttpError) {
		_, ok := taskGetUCloudTaskIdFromToken(request.Id)
		return ok, nil
	})

	ctrl.Tasks = ctrl.TaskService{
		OnCancel: func(id int) *util.HttpError {
			apiTok, ok := db.NewTx2(func(tx *db.Transaction) (string, bool) {
				row, ok := db.Get[struct{ ApiToken string }](
					tx,
					`
						select api_token
						from k8s.tasks_v2
						where ucloud_task_id = :task_id
				    `,
					db.Params{
						"task_id": id,
					},
				)
				return row.ApiToken, ok
			})

			jobName, ok := taskCleanupByToken(apiTok)
			if ok {
				deleteOpts := k8smeta.DeleteOptions{
					PropagationPolicy: util.Pointer(k8smeta.DeletePropagationBackground),
				}

				k8sErr := shared.K8sClient.BatchV1().
					Jobs(shared.ServiceConfig.Compute.TaskNamespace).
					Delete(context.Background(), jobName, deleteOpts)

				if k8sErr != nil {
					log.Warn("Task cancellation: failed to delete completed job %s/%s: %s",
						shared.ServiceConfig.Compute.TaskNamespace, jobName, k8sErr)
				}
			}
			return nil
		},
		OnPause: func(id int) *util.HttpError {
			return util.HttpErr(http.StatusBadRequest, "operation not supported")
		},
		OnResume: func(id int) *util.HttpError {
			return util.HttpErr(http.StatusBadRequest, "operation not supported")
		},
	}

	go taskJobReconciler()
}

func taskJobReconciler() {
	ticker := time.NewTicker(3 * time.Second)
	defer ticker.Stop()

	for util.IsAlive {
		<-ticker.C

		for _, job := range shared.BatchBackgroundJobs.List() {
			if job.Labels[taskJobLabel] != "true" {
				continue
			}

			finalState := fndapi.TaskStateSuccess
			done := false

			{
				for _, cond := range job.Status.Conditions {
					if cond.Status != k8score.ConditionTrue {
						continue
					}

					switch cond.Type {
					case k8sbatch.JobComplete:
						finalState, done = fndapi.TaskStateSuccess, true
					case k8sbatch.JobFailed:
						finalState, done = fndapi.TaskStateFailure, true
					}
				}

				if job.Status.Succeeded > 0 {
					finalState, done = fndapi.TaskStateSuccess, true
				}

				if job.Status.Failed > 0 {
					finalState, done = fndapi.TaskStateFailure, true
				}
			}

			if !done {
				continue
			}

			// NOTE(Dan): We expect most to _not_ have a task at this point, since it should have posted a status
			// update prior to this completing it. This code is here to send a status update in case it completes while
			// the IM is down.

			ucloudTaskId, apiToken, ok := db.NewTx3(func(tx *db.Transaction) (int, string, bool) {
				result, resultOk := db.Get[struct {
					UCloudTaskId int
					ApiToken     string
				}](
					tx,
					`
							select ucloud_task_id, api_token
							from k8s.tasks_v2
							where id = :id
						`,
					db.Params{
						"id": job.Name,
					},
				)

				return result.UCloudTaskId, result.ApiToken, resultOk
			})

			if ok {
				_, err := fndapi.TasksPostStatus.Invoke(fndapi.TasksPostStatusRequest{
					Update: fndapi.TasksPostStatusRequestUpdate{
						Id:         ucloudTaskId,
						ModifiedAt: fndapi.Timestamp(time.Now()),
						NewStatus: fndapi.TaskStatus{
							Title:              util.OptValue("Task has completed"),
							Body:               util.OptValue("This task has completed while the system was being updated."),
							ProgressPercentage: util.OptValue(100.0),
							State:              finalState,
						},
					},
				})

				if err == nil {
					taskCleanupByToken(apiToken)

				} else {
					log.Warn("Task reconciler: failed to post status for %s: %s", job.Name, err)
					continue
				}
			}

			k8sErr := shared.K8sClient.BatchV1().Jobs(job.Namespace).Delete(context.Background(), job.Name, k8smeta.DeleteOptions{
				PropagationPolicy: util.Pointer(k8smeta.DeletePropagationBackground),
			})
			if k8sErr != nil {
				log.Warn("Task reconciler: failed to delete completed job %s/%s: %s", job.Namespace, job.Name, k8sErr)
			}
		}
	}
}

func TaskSubmit(spec TaskSpec) *util.HttpError {
	spec.Id = fmt.Sprintf("task-%s", util.RandomToken(8))
	spec.TaskToken = util.SecureToken()

	resp, err := fndapi.TasksCreate.Invoke(fndapi.TasksCreateRequest{
		User:      spec.CreationState.Username,
		CanCancel: true,
		Icon:      util.OptValue(spec.CreationState.Icon),
	})

	if err != nil {
		log.Warn("Failed to create background task: %s", err)
		return util.HttpErr(http.StatusInternalServerError, "failed to create background task")
	}

	ucloudTaskId := resp.Id

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into k8s.tasks_v2(id, ucloud_task_id, api_token)
				values (:id, :ucloud_task, :tok)
		    `,
			db.Params{
				"id":          spec.Id,
				"ucloud_task": ucloudTaskId,
				"tok":         spec.TaskToken,
			},
		)
	})

	taskSelector := shared.ServiceConfig.Compute.TaskNodeSelector

	storageVolumeMounts, internalToPod := resolveTaskMounts(spec)
	sourcePath := mapUCloudPathToTaskPath(spec.Source, internalToPod)
	destinationPath := mapUCloudPathToTaskPath(spec.Destination, internalToPod)

	job := k8sbatch.Job{
		ObjectMeta: k8smeta.ObjectMeta{
			Name:      spec.Id,
			Namespace: shared.ServiceConfig.Compute.TaskNamespace,
			Labels: map[string]string{
				taskJobLabel: "true",
			},
		},
		Spec: k8sbatch.JobSpec{
			Template: k8score.PodTemplateSpec{
				Spec: k8score.PodSpec{
					AutomountServiceAccountToken: util.Pointer(false),
					EnableServiceLinks:           util.Pointer(false),
					Volumes: []k8score.Volume{
						{
							Name: "ucloud-filesystem",
							VolumeSource: k8score.VolumeSource{
								PersistentVolumeClaim: &k8score.PersistentVolumeClaimVolumeSource{
									ClaimName: shared.ServiceConfig.FileSystem.ClaimName,
								},
							},
						},
						{
							Name: "ucloud-opt",
							VolumeSource: k8score.VolumeSource{
								EmptyDir: &k8score.EmptyDirVolumeSource{},
							},
						},
					},
					InitContainers: []k8score.Container{
						{
							Name:            "ucloud-executables",
							Image:           "alpine:latest",
							ImagePullPolicy: k8score.PullIfNotPresent,
							Command: []string{
								"sh",
								"-c",
								"cp /mnt/exe/ucloud /opt/ucloud/ucloud ; cp /mnt/exe/provider-hostname.txt /opt/ucloud/provider-hostname.txt",
							},
							VolumeMounts: []k8score.VolumeMount{
								{
									Name:      "ucloud-opt",
									MountPath: "/opt/ucloud",
								},
								{
									Name:      "ucloud-filesystem",
									ReadOnly:  true,
									MountPath: "/mnt/exe",
									SubPath:   shared.ExecutablesDir,
								},
							},
						},
					},
					Containers: []k8score.Container{
						{
							Name:            "job",
							Image:           "alpine:latest",
							Command:         []string{"/opt/ucloud/ucloud", "task-processor"},
							WorkingDir:      "/",
							ImagePullPolicy: k8score.PullIfNotPresent,
							Env: []k8score.EnvVar{
								{Name: taskEnvType, Value: string(spec.Type)},
								{Name: taskEnvId, Value: spec.Id},
								{Name: taskEnvSource, Value: sourcePath},
								{Name: taskEnvDestination, Value: destinationPath},
								{Name: taskEnvTransferEndpoint, Value: spec.TransferEndpoint},
								{Name: taskEnvTaskToken, Value: spec.TaskToken},
								{Name: taskEnvConflictPolicy, Value: spec.ConflictPolicy},
							},
							Resources: k8score.ResourceRequirements{
								Limits: map[k8score.ResourceName]resource.Quantity{
									k8score.ResourceCPU:    *resource.NewMilliQuantity(8000, resource.DecimalExponent),
									k8score.ResourceMemory: *resource.NewScaledQuantity(1024*16, resource.Mega),
								},
								Requests: map[k8score.ResourceName]resource.Quantity{
									k8score.ResourceCPU:    *resource.NewMilliQuantity(500, resource.DecimalExponent),
									k8score.ResourceMemory: *resource.NewScaledQuantity(2048, resource.Mega),
								},
							},
							VolumeMounts: append(storageVolumeMounts, k8score.VolumeMount{
								Name:      "ucloud-opt",
								MountPath: "/opt/ucloud",
								ReadOnly:  true,
							}),
						},
					},
					RestartPolicy: k8score.RestartPolicyNever,
					NodeSelector:  taskSelector,
				},
			},
		},
	}

	_, kerr := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Create(context.Background(), &job, k8smeta.CreateOptions{})
	if kerr != nil {
		log.Warn("K8s background task failed: %s", kerr)
		return util.HttpErr(http.StatusInternalServerError, "failed to create background task")
	}

	return nil
}

func resolveTaskMounts(spec TaskSpec) ([]k8score.VolumeMount, map[string]string) {
	type candidateMount struct {
		InternalPath string
		SubPath      string
		PodPath      string
		ReadOnly     bool
	}

	resolvedMounts := map[string][]util.Tuple2[string, bool]{}
	internalToPod := map[string]string{}

	internalToSubpath := func(internalPath string) (string, bool) {
		subpath, ok := strings.CutPrefix(internalPath, filepath.Clean(shared.ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			return "", false
		}

		return subpath, true
	}

	addMount := func(containerPath, subpath string, readOnly bool) {
		existing, _ := resolvedMounts[containerPath]
		existing = append(existing, util.Tuple2[string, bool]{subpath, readOnly})
		resolvedMounts[containerPath] = existing
	}

	addUCloudMount := func(containerPath, ucloudPath string, readOnly bool) {
		internalPath, ok, _ := UCloudToInternal(ucloudPath)
		if !ok {
			return
		}

		subpath, ok := internalToSubpath(internalPath)
		if !ok {
			return
		}

		addMount(containerPath, subpath, readOnly)
	}

	for _, mount := range spec.Mounts {
		ucloudPath := mount.UCloudPath
		comps := util.Components(ucloudPath)
		compsLen := len(comps)
		if compsLen == 0 {
			continue
		}

		title := comps[compsLen-1]
		if compsLen == 1 {
			drive, ok := ResolveDrive(comps[0])
			if !ok {
				continue
			}

			title = strings.ReplaceAll(drive.Specification.Title, "Member Files: ", "")
		}

		containerPath := filepath.Join(taskStorageMountDir, title)
		addUCloudMount(containerPath, ucloudPath, false)
	}

	var folders []candidateMount
	for containerPath, mounts := range resolvedMounts {
		if len(mounts) == 1 {
			mount := mounts[0]
			internalPath := shared.ServiceConfig.FileSystem.MountPoint + "/" + mount.First
			folders = append(folders, candidateMount{
				InternalPath: internalPath,
				SubPath:      mount.First,
				PodPath:      containerPath,
				ReadOnly:     mount.Second,
			})
		} else {
			slices.SortFunc(mounts, func(a, b util.Tuple2[string, bool]) int {
				return strings.Compare(a.First, b.First)
			})

			for i, mount := range mounts {
				resolvedContainerPath := fmt.Sprintf("%s-%d", containerPath, i)
				internalPath := shared.ServiceConfig.FileSystem.MountPoint + "/" + mount.First
				folders = append(folders, candidateMount{
					InternalPath: internalPath,
					SubPath:      mount.First,
					PodPath:      resolvedContainerPath,
					ReadOnly:     mount.Second,
				})
			}
		}
	}

	volumeMounts := make([]k8score.VolumeMount, 0, len(folders))
	for _, folder := range folders {
		volumeMounts = append(volumeMounts, k8score.VolumeMount{
			Name:      "ucloud-filesystem",
			ReadOnly:  folder.ReadOnly,
			MountPath: folder.PodPath,
			SubPath:   folder.SubPath,
		})

		internalToPod[folder.InternalPath] = folder.PodPath
	}

	return volumeMounts, internalToPod
}

func mapUCloudPathToTaskPath(ucloudPath string, internalToPod map[string]string) string {
	internalPath, ok, _ := UCloudToInternal(ucloudPath)
	if !ok {
		return "/dev/null"
	}

	if podPath, ok := internalToPod[internalPath]; ok {
		return podPath
	}

	parent := util.Parent(ucloudPath)
	parentInternalPath, ok, _ := UCloudToInternal(parent)
	if !ok {
		return "/dev/null"
	}

	podPath, ok := internalToPod[parentInternalPath]
	if !ok {
		return "/dev/null"
	}

	return filepath.Join(podPath, util.FileName(ucloudPath))
}
