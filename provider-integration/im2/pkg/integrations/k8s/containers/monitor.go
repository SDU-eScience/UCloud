package containers

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"slices"
	"strings"
	"time"

	"golang.org/x/sys/unix"
	"gopkg.in/yaml.v3"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/pkg/ucviz"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type PodPendingStatus struct {
	Message            string
	EstimatedTimeLeft  util.Option[time.Duration]
	EstimatedProgress1 util.Option[float64]
}

func Monitor(tracker shared.JobTracker, jobs map[string]*orc.Job) {
	allPods := shared.JobPods.List()

	/*
		events, err := K8sClient.CoreV1().Events(Namespace).List(context.Background(), meta.ListOptions{})
		if err != nil {
			log.Info("Failed to fetch events: %v", err)
			return
		}
	*/

	activeIApps := controller.IAppRetrieveAllByJobId()
	iappsHandled := map[string]util.Empty{}
	for _, handler := range IApps {
		beforeMonitor := handler.BeforeMonitor
		if beforeMonitor != nil {
			beforeMonitor(allPods, jobs, activeIApps)
		}
	}

	length := len(allPods)
	for i := 0; i < length; i++ {
		pod := allPods[i]
		idAndRank, ok := podNameToIdAndRank(pod.Name)

		if !ok {
			// This pod is not relevant - Do not process it. Most likely this could be a VMI pod.
			continue
		}

		job, ok := jobs[idAndRank.First]
		if !ok {
			// This pod does not belong to an active job - delete it.
			tracker.RequestCleanup(idAndRank.First)
			continue
		}

		iappName := util.OptMapGet(pod.Annotations, IAppAnnotationName)
		iappEtag := util.OptMapGet(pod.Annotations, IAppAnnotationEtag)
		if iappName.Present {
			iappsHandled[job.Id] = util.Empty{}
			iappConfig, iappOk := activeIApps[job.Id]
			handler, handlerOk := IApps[iappName.Value]

			shouldRun := handlerOk &&
				iappOk &&
				iappEtag.Present &&
				handler.ShouldRun(job, iappConfig.Configuration) &&
				iappEtag.Value == iappConfig.ETag

			if !shouldRun {
				ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
				_ = K8sClient.CoreV1().Pods(Namespace).Delete(ctx, pod.Name, meta.DeleteOptions{})
				cancel()

				tracker.TrackState(shared.JobReplicaState{
					Id:    job.Id,
					Rank:  0,
					State: orc.JobStateSuspended,
				})
			} else {
				state, status := podToStateAndStatus(pod)
				if state.IsFinal() {
					state = orc.JobStateSuspended
					tracker.RequestCleanup(job.Id)
				}

				tracker.TrackState(shared.JobReplicaState{
					Id:     job.Id,
					Rank:   0,
					State:  state,
					Node:   util.OptValue(pod.Spec.NodeName),
					Status: util.OptValue(status),
				})
			}
		} else {
			if idAndRank.Second == 0 && pod.Status.Phase == core.PodPending {
				// NOTE(Dan): Events are in chronological order. We go through the events and find the most recent
				// event to use as our message.

				const (
					PhaseInit = iota
					PhasePulling
					PhaseFinalPreparation
					PhaseStarted
				)

				phaseTime := pod.CreationTimestamp.Time
				phase := PhaseInit

				/*
					for _, ev := range events.Items {
						if ev.InvolvedObject.Name == pod.Name && ev.InvolvedObject.FieldPath == "spec.containers{user-job}" {
							if ev.Reason == "Pulling" {
								phase = PhasePulling
								phaseTime = ev.LastTimestamp.Time
							} else if ev.Reason == "Pulled" {
								phase = PhaseFinalPreparation
								phaseTime = ev.LastTimestamp.Time
							} else if ev.Reason == "Started" {
								phase = PhaseStarted
								phaseTime = ev.LastTimestamp.Time
							}
						}
					}
				*/

				writer := &bytes.Buffer{}
				stream := ucviz.NewWidgetStream(writer)

				var status struct {
					Valid              bool
					Message            string
					EstimatedTimeLeft  util.Option[time.Duration]
					EstimatedProgress1 util.Option[float64]
				}

				switch phase {
				case PhaseInit:
					status.Valid = true
					status.Message = "Your job is currently being prepared..."

				case PhasePulling:
					status.Valid = true
					status.Message = "The software is currently being downloaded to the machine..."

					for _, container := range pod.Spec.Containers {
						if container.Name == ContainerUserJob {
							size, err := EstimateCompressedImageSize(container.Image)
							if size != 0 && err == nil {
								timeSincePullStart := time.Now().Sub(phaseTime)
								dlSpeed := ServiceConfig.Compute.EstimatedContainerDownloadSpeed
								estimatedProgress := (dlSpeed * 1000000) * timeSincePullStart.Seconds()
								estimatedRemaining := float64(size) - estimatedProgress
								if estimatedRemaining < 0 {
									estimatedRemaining = 0
								}

								estimatedTimeLeft := time.Duration((estimatedRemaining/1000000.0)/dlSpeed) * time.Second
								estimatedPercentComplete := estimatedProgress / float64(size)

								status.EstimatedTimeLeft.Set(estimatedTimeLeft)
								status.EstimatedProgress1.Set(estimatedPercentComplete)
							}
							break
						}
					}

				case PhaseFinalPreparation:
					status.Valid = true
					status.Message = "Software has been download and your job will start soon!"

				case PhaseStarted:
					status.Valid = false
				}

				if status.Valid {
					_ = stream.WriteDoc(`
						<Box id="jobPending" tab="Status" icon="info">
							<Widget id="jobPendingMessage" />
							<Widget id="jobPendingTimeLeft" />
							<Widget id="jobPendingStatus" />
						</Box>
					`)

					stream.CreateLabel("jobPendingMessage", ucviz.WidgetLocation{}, ucviz.WidgetLabel{
						Text: status.Message,
					})

					if status.EstimatedTimeLeft.Present {
						stream.CreateLabel("jobPendingMessage", ucviz.WidgetLocation{}, ucviz.WidgetLabel{
							Text: fmt.Sprintf("Estimated completion in: %s", status.EstimatedTimeLeft.Value),
						})
					} else {
						stream.Delete("jobPendingTimeLeft")
					}

					if status.EstimatedProgress1.Present {
						stream.CreateProgressBar(
							"jobPendingStatus",
							ucviz.WidgetLocation{},
							ucviz.WidgetProgressBar{Progress: status.EstimatedProgress1.Value},
						)
					} else {
						stream.Delete("jobPendingStatus")
					}
				} else {
					stream.Delete("jobPending")
				}

				updateBytes := writer.Bytes()
				if len(updateBytes) > 0 {
					dispatchNonPersistentFollowMessage(idAndRank.First, idAndRank.Second, string(updateBytes), "jobPending")
				}
			}

			times := shared.ComputeRunningTime(job)
			if times.TimeRemaining.Present && times.TimeRemaining.Value < 0 {
				tracker.RequestCleanup(idAndRank.First) // Will implicitly set state to JobStateExpired
			}

			state, status := podToStateAndStatus(pod)

			if pod.ObjectMeta.DeletionTimestamp == nil {
				// Do not track pods which have been deleted.

				tracker.TrackState(shared.JobReplicaState{
					Id:     idAndRank.First,
					Rank:   idAndRank.Second,
					State:  state,
					Node:   util.OptValue(pod.Spec.NodeName),
					Status: util.OptValue(status),
				})
			}
		}
	}

	for jobId, iapp := range activeIApps {
		_, handled := iappsHandled[jobId]
		job, ok := jobs[jobId]
		if !handled {
			if ok {
				handler, handlerOk := IApps[iapp.AppName]
				if handlerOk {
					if handler.ShouldRun(job, iapp.Configuration) {
						// NOTE(Dan): The scheduler will ignore it if it is already in the queue/running.
						shared.RequestSchedule(job)
					}
				}
			}

			tracker.TrackState(shared.JobReplicaState{
				Id:    jobId,
				Rank:  0,
				State: orc.JobStateSuspended,
			})
		}
	}
}

func podToStateAndStatus(pod *core.Pod) (orc.JobState, string) {
	state := orc.JobStateFailure
	status := "Unexpected state"

	switch pod.Status.Phase {
	case core.PodPending:
		state = orc.JobStateInQueue
		status = "Job is currently in the queue"

	case core.PodRunning:
		state = orc.JobStateRunning
		status = "Job is now running"

	case core.PodSucceeded:
		state = orc.JobStateSuccess
		status = "Job has terminated"

	case core.PodFailed:
		state = orc.JobStateSuccess
		status = "Job has terminated with a non-zero exit code"
		if util.DevelopmentModeEnabled() {
			podYaml, _ := yaml.Marshal(pod)
			status += "\n" + string(podYaml)
		}

	case core.PodUnknown:
		state = orc.JobStateFailure
		status = "Job has failed due to an internal error (#1)"
	}
	return state, status
}

func OnStart(jobs []orc.Job) {
	var messages []controller.JobMessage

	length := len(jobs)
	for i := 0; i < length; i++ {
		// Common data collection for each job
		// -------------------------------------------------------------------------------------------------------------
		job := &jobs[i]
		jobFolder, _, err := FindJobFolder(job)
		if err != nil {
			continue
		}

		// Dynamic targets
		// -------------------------------------------------------------------------------------------------------------
		dynamicTargets := readDynamicTargets(job, jobFolder)
		for _, target := range dynamicTargets {
			targetAsJson, _ := json.Marshal(target)

			messages = append(messages, controller.JobMessage{
				JobId:   job.Id,
				Message: fmt.Sprintf("Target: %s", string(targetAsJson)),
			})
		}
	}

	// Not much we can do in this case
	_ = controller.JobTrackMessage(messages)
}

func readDynamicTargets(job *orc.Job, jobFolder string) []orc.DynamicTarget {
	dynamicTargets := map[string]orc.DynamicTarget{}

	var dynTargetLists [][]orc.DynamicTarget
	for rank := 0; rank < job.Specification.Replicas; rank++ {
		file, ok := filesystem.OpenFile(filepath.Join(jobFolder, fmt.Sprintf(".script-targets-%d.yaml", rank)), unix.O_RDONLY, 0)
		if !ok {
			continue
		}

		var data []byte

		info, err := file.Stat()
		if err == nil {
			size := info.Size()
			if size < 1024*1024 {
				data = make([]byte, size)
				n, _ := file.Read(data)
				data = data[:n]
			}
		}
		_ = file.Close()

		var list []orc.DynamicTarget
		_ = yaml.Unmarshal(data, &list)
		dynTargetLists = append(dynTargetLists, list)
	}

	// Merge all the lists but give priority to the self-reported targets. Lower ranks have priority otherwise.
	for rank := len(dynTargetLists) - 1; rank >= 0; rank-- {
		list := dynTargetLists[rank]
		for _, target := range list {
			dynamicTargets[fmt.Sprintf("%v/%v", target.Rank, target.Target)] = target
		}
	}

	for rank := 0; rank < len(dynTargetLists); rank++ {
		list := dynTargetLists[rank]
		for _, target := range list {
			if target.Rank == rank {
				dynamicTargets[fmt.Sprintf("%v/%v", target.Rank, target.Target)] = target
			}
		}
	}

	var targetList []orc.DynamicTarget
	for _, target := range dynamicTargets {
		targetList = append(targetList, target)
	}

	slices.SortFunc(targetList, func(a, b orc.DynamicTarget) int {
		if a.Rank < b.Rank {
			return -1
		} else if a.Rank > b.Rank {
			return 1
		} else {
			return strings.Compare(a.Target, b.Target)
		}
	})

	return targetList
}
