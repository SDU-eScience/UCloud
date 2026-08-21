package initcache

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/url"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/unix"
	batch "k8s.io/api/batch/v1"
	core "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/registry"
	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	DynamicParameterName = "ucCacheInitScript"
	initScriptParameter  = "initScript"
	cacheRepository      = "ucloud-init-cache"
	preparationContainer = "prepare"
	snapshotContainer    = "snapshot"
	preparationDeadline  = int64(30 * 60)

	preparationLabel        = "ucloud.dk/init-cache-preparation"
	preparationJobLabel     = "ucloud.dk/init-cache-job-id"
	preparationTokenAnno    = "ucloud.dk/init-cache-pull-token"
	snapshotLabel           = "ucloud.dk/init-cache-snapshot"
	snapshotJobLabel        = "ucloud.dk/init-cache-snapshot-job-id"
	snapshotDestinationAnno = "ucloud.dk/init-cache-destination"
	snapshotTokenAnno       = "ucloud.dk/init-cache-snapshot-token"
)

type cacheEntry struct {
	WorkspaceType  string
	WorkspaceId    string
	CacheKey       string
	RepositoryName string
	Tag            string
	ImageDigest    sql.NullString
	ExactBytes     int64
	State          string
	BuilderJobId   sql.NullString
	CreatedAt      time.Time
	LastUsedAt     time.Time
}

type preparationDecision struct {
	Build bool
	Wait  bool
	Entry cacheEntry
}

var monitors = struct {
	sync.Mutex
	jobs map[string]bool
}{jobs: map[string]bool{}}

var maintenanceMu sync.Mutex

func EnabledFor(job *orc.Job) bool {
	if !shared.ServiceConfig.Registry.Enabled || !job.Status.ResolvedApplication.Present {
		return false
	}
	hasInitScript := false
	for _, parameter := range job.Status.ResolvedApplication.Value.Invocation.Parameters {
		if parameter.Name == DynamicParameterName {
			return false
		}
		if parameter.Name == initScriptParameter && parameter.Type == orc.ApplicationParameterTypeInputFile {
			hasInitScript = true
		}
	}
	if !hasInitScript {
		return false
	}
	cacheValue, ok := job.Specification.Parameters[DynamicParameterName]
	if !ok || cacheValue.Type != orc.AppParameterValueTypeBoolean {
		return false
	}
	enabled, _ := cacheValue.Value.(bool)
	if !enabled {
		return false
	}
	script, ok := job.Specification.Parameters[initScriptParameter]
	return ok && script.Type == orc.AppParameterValueTypeFile && script.Path != ""
}

func Prepare(job *orc.Job) (bool, *util.HttpError) {
	if !EnabledFor(job) {
		return false, nil
	}
	trackMessage(job.Id, "Checking the initialization image cache")

	script := job.Specification.Parameters[initScriptParameter]
	internalPath, ok, _ := filesystem.UCloudToInternal(script.Path)
	if !ok {
		return false, util.UserHttpError("Unable to resolve initialization script")
	}
	cacheKey, herr := calculateKey(job, script.Path, internalPath)
	if herr != nil {
		return false, herr
	}

	rootRepository, herr := registry.RepositoryFindDefault(job.Owner)
	if herr != nil {
		return false, herr
	}
	entry := cacheEntry{
		WorkspaceType:  workspaceType(job.Owner),
		WorkspaceId:    workspaceId(job.Owner),
		CacheKey:       cacheKey,
		RepositoryName: rootRepository + "/" + cacheRepository,
		Tag:            "sha256-" + cacheKey,
	}
	decision := reserve(job.Id, entry)
	if decision.Entry.State == "ready" && decision.Entry.ImageDigest.Valid {
		if image, _, resolveErr := registry.InitScriptCacheResolve(job.Owner, decision.Entry.RepositoryName, decision.Entry.Tag); resolveErr == nil {
			markJobReady(job.Id, image, decision.Entry)
			return false, nil
		}
		decision = claimStale(job.Id, decision.Entry)
	}
	if decision.Wait {
		if decision.Entry.BuilderJobId.Valid && decision.Entry.BuilderJobId.String == job.Id {
			monitor(job.Id)
			return true, nil
		}
		if !builderIsActive(decision.Entry) {
			_ = abandonBuilder(decision.Entry)
			decision = reserve(job.Id, decision.Entry)
			if decision.Build {
				trackMessage(job.Id, "Preparing a reusable container environment")
				if herr = createPreparationJob(job, decision.Entry, internalPath); herr != nil {
					markPreparationFailed(decision.Entry, "Unable to start image preparation: "+herr.Why, nil)
					return false, herr
				}
				monitor(job.Id)
				return true, nil
			}
		}
		trackMessage(job.Id, "Another job is preparing the requested container environment")
		return true, nil
	}
	if !decision.Build {
		return false, nil
	}

	trackMessage(job.Id, "Preparing a reusable container environment")
	if herr = createPreparationJob(job, decision.Entry, internalPath); herr != nil {
		markPreparationFailed(decision.Entry, "Unable to start image preparation: "+herr.Why, nil)
		return false, herr
	}
	monitor(job.Id)
	return true, nil
}

func Recover(job *orc.Job) bool {
	if !EnabledFor(job) {
		return false
	}
	if image, ok := ImageForJob(job.Id); ok {
		_ = image
		cleanupPreparation(job.Id)
		return false
	}
	delayed, err := Prepare(job)
	if err != nil {
		failJob(job, "Unable to recover initialization image preparation: "+err.Why, nil)
		return true
	}
	return delayed
}

func Cancel(jobId string) {
	entry, found := entryForJob(jobId)
	if !found {
		return
	}
	wasBuilder := entry.BuilderJobId.Valid && entry.BuilderJobId.String == jobId && entry.State == "building"
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from init_script_image_cache_jobs where job_id = :job`, db.Params{"job": jobId})
		if wasBuilder {
			db.Exec(tx, `
				update init_script_image_cache set state = 'failed', builder_job_id = null
				where workspace_type = :wt and workspace_id = :wid and cache_key = :key
			`, entryParams(entry))
		}
	})
	cleanupPreparation(jobId)
	if wasBuilder {
		_ = registry.InitScriptCacheDelete(ownerFromEntry(entry), entry.RepositoryName, entry.Tag)
		go restartFirstWaiter(entry)
	}
}

func ImageForJob(jobId string) (string, bool) {
	return db.NewTx2[string, bool](func(tx *db.Transaction) (string, bool) {
		row, ok := db.Get[struct{ ImageDigest sql.NullString }](tx, `
			select c.image_digest
			from init_script_image_cache_jobs j
			join init_script_image_cache c using(workspace_type, workspace_id, cache_key)
			where j.job_id = :job and j.state = 'ready' and c.state = 'ready'
		`, db.Params{"job": jobId})
		return row.ImageDigest.String, ok && row.ImageDigest.Valid && row.ImageDigest.String != ""
	})
}

func ConsumesInitScript(jobId string) bool {
	_, ok := ImageForJob(jobId)
	return ok
}

func StartMaintenance() {
	go func() {
		for util.IsAlive {
			maintainCache()
			time.Sleep(time.Hour)
		}
	}()
}

func calculateKey(job *orc.Job, ucloudPath, internalPath string) (string, *util.HttpError) {
	fd, ok := filesystem.OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return "", util.UserHttpError("Unable to read initialization script")
	}
	defer util.SilentClose(fd)
	hash := sha256.New()
	_, _ = io.WriteString(hash, "ucloud-init-cache-v1\x00")
	_, _ = io.WriteString(hash, job.Specification.Application.Name+"\x00")
	_, _ = io.WriteString(hash, job.Specification.Application.Version+"\x00")
	_, _ = io.WriteString(hash, filepath.Clean(ucloudPath)+"\x00")
	if _, err := io.Copy(hash, fd); err != nil {
		return "", util.UserHttpError("Unable to read initialization script")
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

func reserve(jobId string, desired cacheEntry) preparationDecision {
	return db.NewTx(func(tx *db.Transaction) preparationDecision {
		row, found := db.Get[cacheEntry](tx, `
			select workspace_type, workspace_id, cache_key, repository_name, tag, image_digest,
				exact_bytes, state, builder_job_id, created_at, last_used_at
			from init_script_image_cache
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
			for update
		`, entryParams(desired))
		if found && row.State == "ready" {
			upsertJobBinding(tx, jobId, row, "ready")
			return preparationDecision{Entry: row}
		}
		if found && row.State == "building" {
			upsertJobBinding(tx, jobId, row, "waiting")
			return preparationDecision{Wait: true, Entry: row}
		}
		db.Exec(tx, `
			insert into init_script_image_cache(
				workspace_type, workspace_id, cache_key, repository_name, tag, state, builder_job_id
			) values (:wt, :wid, :key, :repository, :tag, 'building', :job)
			on conflict(workspace_type, workspace_id, cache_key) do update set
				repository_name = excluded.repository_name,
				tag = excluded.tag,
				image_digest = null,
				exact_bytes = 0,
				state = 'building',
				builder_job_id = excluded.builder_job_id,
				created_at = now(),
				last_used_at = now()
		`, mergeParams(entryParams(desired), db.Params{"repository": desired.RepositoryName, "tag": desired.Tag, "job": jobId}))
		desired.State = "building"
		desired.BuilderJobId = sql.NullString{String: jobId, Valid: true}
		upsertJobBinding(tx, jobId, desired, "preparing")
		return preparationDecision{Build: true, Entry: desired}
	})
}

func claimStale(jobId string, entry cacheEntry) preparationDecision {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			update init_script_image_cache set state = 'failed', image_digest = null, builder_job_id = null
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key and state = 'ready'
		`, entryParams(entry))
	})
	return reserve(jobId, entry)
}

func upsertJobBinding(tx *db.Transaction, jobId string, entry cacheEntry, state string) {
	db.Exec(tx, `
		insert into init_script_image_cache_jobs(job_id, workspace_type, workspace_id, cache_key, state)
		values (:job, :wt, :wid, :key, :state)
		on conflict(job_id) do update set
			workspace_type = excluded.workspace_type,
			workspace_id = excluded.workspace_id,
			cache_key = excluded.cache_key,
			state = excluded.state
	`, mergeParams(entryParams(entry), db.Params{"job": jobId, "state": state}))
}

func createPreparationJob(job *orc.Job, entry cacheEntry, internalPath string) *util.HttpError {
	app := job.Status.ResolvedApplication.Value
	image := app.Invocation.Tool.Tool.Value.Description.Image
	if image == "" {
		image = app.Invocation.Tool.Tool.Value.Description.Container
	}
	if image == "" {
		return util.UserHttpError("Application does not define a container image")
	}

	relativePath, err := filepath.Rel(filepath.Clean(shared.ServiceConfig.FileSystem.MountPoint), filepath.Clean(internalPath))
	if err != nil || relativePath == "." || strings.HasPrefix(relativePath, ".."+string(filepath.Separator)) || relativePath == ".." {
		return util.UserHttpError("Initialization script is outside the provider filesystem")
	}
	mountSubPath := relativePath
	mountPath := "/ucloud-init-script"
	scriptPath := mountPath
	if util.DevelopmentModeEnabled() {
		mountSubPath = filepath.Dir(relativePath)
		mountPath = "/ucloud-init-script-directory"
		scriptPath = filepath.Join(mountPath, filepath.Base(relativePath))
	}

	name := preparationName(job.Id)
	pullSecret, token, herr := createPullSecret(job.Owner, name)
	if herr != nil {
		return herr
	}
	backoff := int32(0)
	preparation := &batch.Job{
		ObjectMeta: meta.ObjectMeta{
			Name: name, Namespace: shared.ServiceConfig.Compute.TaskNamespace,
			Labels:      map[string]string{preparationLabel: "true", preparationJobLabel: job.Id},
			Annotations: map[string]string{preparationTokenAnno: token.Id},
		},
		Spec: batch.JobSpec{
			BackoffLimit: &backoff, ActiveDeadlineSeconds: util.Pointer(preparationDeadline),
			Template: core.PodTemplateSpec{Spec: core.PodSpec{
				AutomountServiceAccountToken: util.Pointer(false), EnableServiceLinks: util.Pointer(false),
				RestartPolicy: core.RestartPolicyNever, NodeSelector: shared.ServiceConfig.Compute.TaskNodeSelector,
				ImagePullSecrets: []core.LocalObjectReference{{Name: pullSecret.Name}},
				Volumes: []core.Volume{{Name: "init-script", VolumeSource: core.VolumeSource{
					PersistentVolumeClaim: &core.PersistentVolumeClaimVolumeSource{ClaimName: shared.ServiceConfig.FileSystem.ClaimName, ReadOnly: true},
				}}},
				Containers: []core.Container{{
					Name: preparationContainer, Image: image, ImagePullPolicy: core.PullIfNotPresent,
					Command: []string{"bash", "-lc"},
					Args: []string{`set -e
bash "$UCLOUD_INIT_SCRIPT"
touch /tmp/ucloud-init-script-complete
while true; do sleep 3600; done`},
					Env:            []core.EnvVar{{Name: "UCLOUD_INIT_SCRIPT", Value: scriptPath}},
					VolumeMounts:   []core.VolumeMount{{Name: "init-script", MountPath: mountPath, SubPath: mountSubPath, ReadOnly: true}},
					ReadinessProbe: &core.Probe{ProbeHandler: core.ProbeHandler{Exec: &core.ExecAction{Command: []string{"test", "-f", "/tmp/ucloud-init-script-complete"}}}, PeriodSeconds: 2, FailureThreshold: 900},
					Resources: core.ResourceRequirements{
						Requests: core.ResourceList{core.ResourceCPU: resource.MustParse("500m"), core.ResourceMemory: resource.MustParse("2Gi"), core.ResourceEphemeralStorage: resource.MustParse("5Gi")},
						Limits:   core.ResourceList{core.ResourceCPU: resource.MustParse("4"), core.ResourceMemory: resource.MustParse("8Gi")},
					},
					SecurityContext: &core.SecurityContext{
						RunAsNonRoot:             util.BoolPointer(!app.Invocation.Container.RunAsRoot),
						AllowPrivilegeEscalation: util.BoolPointer(app.Invocation.Container.RunAsRoot),
					},
				}},
			}},
		},
	}
	if util.DevelopmentModeEnabled() {
		preparation.Spec.Template.Spec.HostAliases = []core.HostAlias{{IP: shared.ProviderHostname, Hostnames: []string{shared.ServiceConfig.Registry.Host}}}
	}
	_, err = shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Create(context.Background(), preparation, meta.CreateOptions{})
	if err != nil {
		deleteSecret(pullSecret.Name)
		registry.ApiTokensRevoke(token.Id)
		return util.HttpErrorFromErr(err)
	}
	return nil
}

func monitor(jobId string) {
	monitors.Lock()
	if monitors.jobs[jobId] {
		monitors.Unlock()
		return
	}
	monitors.jobs[jobId] = true
	monitors.Unlock()
	go runMonitor(jobId)
}

func runMonitor(jobId string) {
	defer func() {
		monitors.Lock()
		delete(monitors.jobs, jobId)
		monitors.Unlock()
	}()
	entry, found := entryForJob(jobId)
	if !found {
		return
	}
	name := preparationName(jobId)
	var preparationPod *core.Pod
	for util.IsAlive {
		if _, found = entryForJob(jobId); !found {
			return
		}
		job, err := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Get(context.Background(), name, meta.GetOptions{})
		if apierrors.IsNotFound(err) {
			markPreparationFailed(entry, "Image preparation job disappeared", nil)
			return
		}
		if err != nil {
			time.Sleep(2 * time.Second)
			continue
		}
		pods := podsForJob(name)
		if len(pods) > 0 {
			preparationPod = &pods[0]
			for _, status := range preparationPod.Status.ContainerStatuses {
				if status.Name == preparationContainer && status.Ready && status.State.Running != nil {
					goto ready
				}
			}
		}
		if kubernetesJobFailed(job) {
			logs := logsForJob(name, preparationContainer)
			markPreparationFailed(entry, "Initialization script failed", logs)
			return
		}
		time.Sleep(2 * time.Second)
	}
	return

ready:
	trackForEntry(entry, "Initialization script completed; publishing the container environment")
	image, snapshotLogs, herr := snapshotPreparation(jobId, entry, preparationPod)
	if herr != nil {
		currentEntry, active := entryForJob(jobId)
		if !active || !currentEntry.BuilderJobId.Valid || currentEntry.BuilderJobId.String != jobId {
			return
		}
		logs := logsForJob(name, preparationContainer)
		if len(snapshotLogs) > 0 {
			logs = append(logs, []byte("\n\nSnapshot helper:\n")...)
			logs = append(logs, snapshotLogs...)
		}
		markPreparationFailed(entry, "Unable to publish the prepared container environment: "+herr.Why, logs)
		return
	}
	_, exactBytes, herr := registry.InitScriptCacheResolve(ownerFromEntry(entry), entry.RepositoryName, entry.Tag)
	if herr != nil {
		markPreparationFailed(entry, "Unable to validate the prepared container environment: "+herr.Why, nil)
		return
	}
	currentEntry, active := entryForJob(jobId)
	if !active || !currentEntry.BuilderJobId.Valid || currentEntry.BuilderJobId.String != jobId {
		return
	}
	complete(entry, image, exactBytes)
	cleanupPreparation(jobId)
	scheduleWaiting(entry)
	go maintainCache()
}

func snapshotPreparation(jobId string, entry cacheEntry, pod *core.Pod) (string, []byte, *util.HttpError) {
	containerId := ""
	for _, status := range pod.Status.ContainerStatuses {
		if status.Name == preparationContainer && status.State.Running != nil {
			containerId = status.ContainerID
			break
		}
	}
	if _, value, ok := strings.Cut(containerId, "://"); ok {
		containerId = value
	}
	if containerId == "" || pod.Spec.NodeName == "" {
		return "", nil, util.ServerHttpError("preparation container is not available for snapshotting")
	}
	server, err := url.Parse(registry.Server())
	if err != nil || server.Host == "" {
		return "", nil, util.ServerHttpError("invalid registry configuration")
	}
	destination := server.Host + "/" + entry.RepositoryName + ":" + entry.Tag
	token, herr := registry.ApiTokensCreateForSnapshot(ownerFromEntry(entry), time.Hour)
	if herr != nil {
		return "", nil, herr
	}
	name := snapshotName(jobId)
	backoff := int32(0)
	deadline := min(shared.ServiceConfig.Registry.Snapshot.DeadlineSeconds, int(preparationDeadline))
	helper := &batch.Job{
		ObjectMeta: meta.ObjectMeta{
			Name: name, Namespace: shared.ServiceConfig.Compute.TaskNamespace,
			Labels:      map[string]string{snapshotLabel: "true", snapshotJobLabel: jobId},
			Annotations: map[string]string{snapshotDestinationAnno: destination, snapshotTokenAnno: token.Id},
		},
		Spec: batch.JobSpec{BackoffLimit: &backoff, ActiveDeadlineSeconds: util.Pointer(int64(deadline)), Template: core.PodTemplateSpec{Spec: core.PodSpec{
			AutomountServiceAccountToken: util.Pointer(false), EnableServiceLinks: util.Pointer(false), NodeName: pod.Spec.NodeName, RestartPolicy: core.RestartPolicyNever,
			Volumes: []core.Volume{{Name: "containerd-socket", VolumeSource: core.VolumeSource{HostPath: &core.HostPathVolumeSource{Path: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket, Type: util.Pointer(core.HostPathSocket)}}}},
			Containers: []core.Container{{
				Name: snapshotContainer, Image: shared.ServiceConfig.Registry.Snapshot.HelperImage, ImagePullPolicy: core.PullIfNotPresent,
				Command: []string{"/bin/sh", "-c"}, SecurityContext: &core.SecurityContext{SELinuxOptions: &core.SELinuxOptions{Type: "spc_t"}},
				Args: []string{`set -e
printf %s "$REGISTRY_TOKEN" | nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" $NERDCTL_REGISTRY_FLAGS login --username ucloud --password-stdin "$REGISTRY_SERVER"
nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" commit --pause=true --compression=gzip "$CONTAINER_ID" "$DESTINATION_IMAGE"
cleanup() { nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" image rm "$DESTINATION_IMAGE" >/dev/null 2>&1 || true; }
trap cleanup EXIT
nerdctl --address "$CONTAINERD_ADDRESS" --namespace "$CONTAINERD_NAMESPACE" $NERDCTL_REGISTRY_FLAGS push "$DESTINATION_IMAGE"`},
				Env: []core.EnvVar{
					{Name: "CONTAINER_ID", Value: containerId}, {Name: "DESTINATION_IMAGE", Value: destination},
					{Name: "REGISTRY_SERVER", Value: server.Host}, {Name: "REGISTRY_TOKEN", Value: token.Secret},
					{Name: "CONTAINERD_ADDRESS", Value: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket},
					{Name: "CONTAINERD_NAMESPACE", Value: shared.ServiceConfig.Registry.Snapshot.ContainerdNamespace},
				},
				VolumeMounts: []core.VolumeMount{{Name: "containerd-socket", MountPath: shared.ServiceConfig.Registry.Snapshot.ContainerdSocket}},
			}},
		}}},
	}
	if util.DevelopmentModeEnabled() {
		helper.Spec.Template.Spec.HostAliases = []core.HostAlias{{IP: shared.ProviderHostname, Hostnames: []string{shared.ServiceConfig.Registry.Host}}}
		helper.Spec.Template.Spec.Containers[0].Env = append(helper.Spec.Template.Spec.Containers[0].Env, core.EnvVar{Name: "NERDCTL_REGISTRY_FLAGS", Value: "--insecure-registry"})
	}
	_, err = shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Create(context.Background(), helper, meta.CreateOptions{})
	if err != nil && !apierrors.IsAlreadyExists(err) {
		registry.ApiTokensRevoke(token.Id)
		return "", nil, util.HttpErrorFromErr(err)
	}
	if apierrors.IsAlreadyExists(err) {
		registry.ApiTokensRevoke(token.Id)
	}
	for util.IsAlive {
		if _, found := entryForJob(jobId); !found {
			return "", nil, util.UserHttpError("Job was cancelled")
		}
		current, getErr := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Get(context.Background(), name, meta.GetOptions{})
		if getErr != nil {
			if apierrors.IsNotFound(getErr) {
				return "", nil, util.ServerHttpError("snapshot helper disappeared")
			}
			time.Sleep(2 * time.Second)
			continue
		}
		if current.Status.Succeeded > 0 {
			resolved, _, resolveErr := registry.InitScriptCacheResolve(ownerFromEntry(entry), entry.RepositoryName, entry.Tag)
			return resolved, nil, resolveErr
		}
		if kubernetesJobFailed(current) {
			logs := logsForJob(name, snapshotContainer)
			return "", logs, util.ServerHttpError("snapshot helper failed")
		}
		time.Sleep(2 * time.Second)
	}
	return "", nil, util.ServerHttpError("provider is shutting down")
}

func complete(entry cacheEntry, image string, exactBytes int64) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			update init_script_image_cache
			set state = 'ready', image_digest = :image, exact_bytes = :bytes, builder_job_id = null, last_used_at = now()
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, mergeParams(entryParams(entry), db.Params{"image": image, "bytes": exactBytes}))
		db.Exec(tx, `
			update init_script_image_cache_jobs set state = 'ready'
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, entryParams(entry))
	})
	trackForEntry(entry, "Container environment is ready; waiting for compute resources")
}

func scheduleWaiting(entry cacheEntry) {
	jobIds := db.NewTx(func(tx *db.Transaction) []struct{ JobId string } {
		return db.Select[struct{ JobId string }](tx, `
			select job_id from init_script_image_cache_jobs
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key and state = 'ready'
		`, entryParams(entry))
	})
	for _, row := range jobIds {
		if job, ok := controller.JobRetrieve(row.JobId); ok && job.Status.State == orc.JobStateInQueue {
			shared.RequestSchedule(job)
		}
	}
}

func markJobReady(jobId, image string, entry cacheEntry) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			update init_script_image_cache set image_digest = :image, last_used_at = now()
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, mergeParams(entryParams(entry), db.Params{"image": image}))
		db.Exec(tx, `update init_script_image_cache_jobs set state = 'ready' where job_id = :job`, db.Params{"job": jobId})
	})
	trackMessage(jobId, "Using a cached container environment")
}

func markPreparationFailed(entry cacheEntry, message string, logs []byte) {
	linked := linkedJobs(entry)
	_ = registry.InitScriptCacheDelete(ownerFromEntry(entry), entry.RepositoryName, entry.Tag)
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			update init_script_image_cache set state = 'failed', builder_job_id = null
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, entryParams(entry))
		db.Exec(tx, `
			delete from init_script_image_cache_jobs
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, entryParams(entry))
	})
	cleanupPreparation(entry.BuilderJobId.String)
	for _, row := range linked {
		if job, ok := controller.JobRetrieve(row.JobId); ok && job.Status.State == orc.JobStateInQueue {
			failJob(job, message, logs)
		}
	}
}

func failJob(job *orc.Job, message string, logs []byte) {
	update := orc.JobUpdate{State: util.OptValue(orc.JobStateFailure), Status: util.OptValue(message)}
	if len(logs) == 0 {
		logs = []byte(message + "\n")
	}
	root, drive, herr := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, job.Owner.Project)
	if herr == nil {
		title := job.Status.ResolvedApplication.Value.Metadata.Title
		folder := filepath.Join(root, "Jobs", title, job.Id)
		if filesystem.WriteFileAtomic(filepath.Join(folder, "init-script-preparation.log"), logs, 0660) == nil {
			if ucloudFolder, ok := filesystem.InternalToUCloudWithDrive(drive, folder); ok {
				update.OutputFolder = util.OptValue(ucloudFolder)
			}
		}
	}
	_ = controller.JobTrackRawUpdates([]orc.ResourceUpdateAndId[orc.JobUpdate]{{Id: job.Id, Update: update}})
}

func maintainCache() {
	maintenanceMu.Lock()
	defer maintenanceMu.Unlock()

	cleanupJobBindings()
	entries := db.NewTx(func(tx *db.Transaction) []cacheEntry {
		return db.Select[cacheEntry](tx, `
			select workspace_type, workspace_id, cache_key, repository_name, tag, image_digest,
				exact_bytes, state, builder_job_id, created_at, last_used_at
			from init_script_image_cache where state = 'ready'
			order by last_used_at asc
		`, db.Params{})
	})
	byWorkspace := map[string][]cacheEntry{}
	for _, entry := range entries {
		key := entry.WorkspaceType + "\x00" + entry.WorkspaceId
		byWorkspace[key] = append(byWorkspace[key], entry)
	}
	cutoff := time.Now().Add(-60 * 24 * time.Hour)
	for _, workspaceEntries := range byWorkspace {
		for _, entry := range workspaceEntries {
			if entry.LastUsedAt.Before(cutoff) && !entryLeased(entry) {
				deleteEntry(entry)
			}
		}
		workspaceEntries = readyEntriesForWorkspace(workspaceEntries[0])
		if len(workspaceEntries) == 0 {
			continue
		}
		limit := workspaceLimit(workspaceEntries[0])
		usage := cacheUsage(workspaceEntries)
		if usage <= limit {
			continue
		}
		target := limit * 80 / 100
		for _, entry := range workspaceEntries {
			if usage <= target {
				break
			}
			if entryLeased(entry) {
				continue
			}
			deleteEntry(entry)
			remaining := readyEntriesForWorkspace(entry)
			usage = cacheUsage(remaining)
		}
	}
}

func cleanupJobBindings() {
	rows := db.NewTx(func(tx *db.Transaction) []struct{ JobId string } {
		return db.Select[struct{ JobId string }](tx, `select job_id from init_script_image_cache_jobs`, db.Params{})
	})
	for _, row := range rows {
		job, ok := controller.JobRetrieve(row.JobId)
		if !ok || job.Status.State == orc.JobStateSuccess || job.Status.State == orc.JobStateFailure || job.Status.State == orc.JobStateExpired {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(tx, `delete from init_script_image_cache_jobs where job_id = :job`, db.Params{"job": row.JobId})
			})
		}
	}
}

func deleteEntry(entry cacheEntry) {
	if err := registry.InitScriptCacheDelete(ownerFromEntry(entry), entry.RepositoryName, entry.Tag); err != nil {
		log.Warn("Unable to evict init-script image %s:%s: %s", entry.RepositoryName, entry.Tag, err.Why)
		return
	}
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `
			delete from init_script_image_cache
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, entryParams(entry))
	})
}

func cacheUsage(entries []cacheEntry) int64 {
	if len(entries) == 0 {
		return 0
	}
	tags := make([]string, 0, len(entries))
	for _, entry := range entries {
		tags = append(tags, entry.Tag)
	}
	usage, err := registry.InitScriptCacheUsage(entries[0].RepositoryName, tags)
	if err != nil {
		log.Warn("Unable to calculate init-script image cache usage: %s", err.Why)
		return 0
	}
	return usage
}

func workspaceLimit(entry cacheEntry) int64 {
	if entry.WorkspaceType == "personal" {
		return fnd.DefaultInitScriptImageCacheLimitBytes
	}
	project, ok := controller.ProjectRetrieve(entry.WorkspaceId)
	if !ok {
		return fnd.DefaultInitScriptImageCacheLimitBytes
	}
	return project.Status.Settings.InitScriptImageCacheLimitBytes
}

func readyEntriesForWorkspace(entry cacheEntry) []cacheEntry {
	return db.NewTx(func(tx *db.Transaction) []cacheEntry {
		return db.Select[cacheEntry](tx, `
			select workspace_type, workspace_id, cache_key, repository_name, tag, image_digest,
				exact_bytes, state, builder_job_id, created_at, last_used_at
			from init_script_image_cache
			where workspace_type = :wt and workspace_id = :wid and state = 'ready'
			order by last_used_at asc
		`, entryParams(entry))
	})
}

func entryLeased(entry cacheEntry) bool {
	return db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ JobId string }](tx, `
			select job_id from init_script_image_cache_jobs
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key limit 1
		`, entryParams(entry))
		return ok
	})
}

func entryForJob(jobId string) (cacheEntry, bool) {
	return db.NewTx2[cacheEntry, bool](func(tx *db.Transaction) (cacheEntry, bool) {
		return db.Get[cacheEntry](tx, `
			select c.workspace_type, c.workspace_id, c.cache_key, c.repository_name, c.tag, c.image_digest,
				c.exact_bytes, c.state, c.builder_job_id, c.created_at, c.last_used_at
			from init_script_image_cache_jobs j
			join init_script_image_cache c using(workspace_type, workspace_id, cache_key)
			where j.job_id = :job
		`, db.Params{"job": jobId})
	})
}

func linkedJobs(entry cacheEntry) []struct{ JobId string } {
	return db.NewTx(func(tx *db.Transaction) []struct{ JobId string } {
		return db.Select[struct{ JobId string }](tx, `
			select job_id from init_script_image_cache_jobs
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
		`, entryParams(entry))
	})
}

func restartFirstWaiter(entry cacheEntry) {
	for _, row := range linkedJobs(entry) {
		if job, ok := controller.JobRetrieve(row.JobId); ok && job.Status.State == orc.JobStateInQueue {
			_, _ = Prepare(job)
			return
		}
	}
}

func builderIsActive(entry cacheEntry) bool {
	if !entry.BuilderJobId.Valid {
		return false
	}
	job, ok := controller.JobRetrieve(entry.BuilderJobId.String)
	return ok && job.Status.State == orc.JobStateInQueue
}

func abandonBuilder(entry cacheEntry) bool {
	if !entry.BuilderJobId.Valid {
		return false
	}
	claimed := db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ CacheKey string }](tx, `
			update init_script_image_cache set state = 'failed', builder_job_id = null
			where workspace_type = :wt and workspace_id = :wid and cache_key = :key
				and state = 'building' and builder_job_id = :builder
			returning cache_key
		`, mergeParams(entryParams(entry), db.Params{"builder": entry.BuilderJobId.String}))
		if ok {
			db.Exec(tx, `delete from init_script_image_cache_jobs where job_id = :job`, db.Params{"job": entry.BuilderJobId.String})
		}
		return ok
	})
	if claimed {
		cleanupPreparation(entry.BuilderJobId.String)
		_ = registry.InitScriptCacheDelete(ownerFromEntry(entry), entry.RepositoryName, entry.Tag)
	}
	return claimed
}

func trackForEntry(entry cacheEntry, message string) {
	for _, row := range linkedJobs(entry) {
		trackMessage(row.JobId, message)
	}
}

func trackMessage(jobId, message string) {
	_ = controller.JobTrackMessage([]controller.JobMessage{{JobId: jobId, Message: message}})
}

func cleanupPreparation(jobId string) {
	if jobId == "" {
		return
	}
	for _, name := range []string{preparationName(jobId), snapshotName(jobId)} {
		if current, err := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Get(context.Background(), name, meta.GetOptions{}); err == nil {
			registry.ApiTokensRevoke(current.Annotations[preparationTokenAnno])
			registry.ApiTokensRevoke(current.Annotations[snapshotTokenAnno])
		}
		deleteBackgroundJob(name)
	}
	deleteSecret(preparationSecretName(jobId))
}

func podsForJob(name string) []core.Pod {
	pods, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).List(
		context.Background(), meta.ListOptions{LabelSelector: labels.Set{"job-name": name}.AsSelector().String()},
	)
	if err != nil {
		return nil
	}
	return pods.Items
}

func logsForJob(name, container string) []byte {
	pods := podsForJob(name)
	if len(pods) == 0 {
		return nil
	}
	stream, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).
		GetLogs(pods[0].Name, &core.PodLogOptions{Container: container}).Stream(context.Background())
	if err != nil {
		return nil
	}
	defer util.SilentClose(stream)
	data, _ := io.ReadAll(stream)
	return data
}

func kubernetesJobFailed(job *batch.Job) bool {
	if job.Status.Failed > 0 {
		return true
	}
	for _, condition := range job.Status.Conditions {
		if condition.Type == batch.JobFailed && condition.Status == core.ConditionTrue {
			return true
		}
	}
	return false
}

func createPullSecret(owner orc.ResourceOwner, jobId string) (*core.Secret, registry.SnapshotToken, *util.HttpError) {
	token, herr := registry.ApiTokensCreateForPull(owner, time.Hour)
	if herr != nil {
		return nil, registry.SnapshotToken{}, herr
	}
	credentials := base64.StdEncoding.EncodeToString([]byte("ucloud:" + token.Secret))
	configuration, _ := json.Marshal(map[string]any{"auths": map[string]any{
		registry.Service(): map[string]string{"username": "ucloud", "password": token.Secret, "auth": credentials},
	}})
	secret := &core.Secret{ObjectMeta: meta.ObjectMeta{Name: preparationSecretName(jobId), Namespace: shared.ServiceConfig.Compute.TaskNamespace}, Type: core.SecretTypeDockerConfigJson, Data: map[string][]byte{core.DockerConfigJsonKey: configuration}}
	created, err := shared.K8sClient.CoreV1().Secrets(shared.ServiceConfig.Compute.TaskNamespace).Create(context.Background(), secret, meta.CreateOptions{})
	if err != nil {
		registry.ApiTokensRevoke(token.Id)
		return nil, registry.SnapshotToken{}, util.HttpErrorFromErr(err)
	}
	return created, token, nil
}

func deleteBackgroundJob(name string) {
	propagation := meta.DeletePropagationBackground
	err := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Delete(context.Background(), name, meta.DeleteOptions{PropagationPolicy: &propagation})
	if err != nil && !apierrors.IsNotFound(err) {
		log.Warn("Unable to delete init-script cache job %s: %v", name, err)
	}
}

func deleteSecret(name string) {
	err := shared.K8sClient.CoreV1().Secrets(shared.ServiceConfig.Compute.TaskNamespace).Delete(context.Background(), name, meta.DeleteOptions{})
	if err != nil && !apierrors.IsNotFound(err) {
		log.Warn("Unable to delete init-script cache secret %s: %v", name, err)
	}
}

func preparationName(jobId string) string       { return resourceName("init-cache-", jobId) }
func snapshotName(jobId string) string          { return resourceName("init-cache-snapshot-", jobId) }
func preparationSecretName(jobId string) string { return resourceName("init-cache-pull-", jobId) }

func resourceName(prefix, id string) string {
	id = strings.ToLower(id)
	var clean strings.Builder
	for _, char := range id {
		if char >= 'a' && char <= 'z' || char >= '0' && char <= '9' || char == '-' {
			clean.WriteRune(char)
		} else {
			clean.WriteByte('-')
		}
	}
	result := strings.Trim(prefix+clean.String(), "-")
	if len(result) > 63 {
		result = strings.TrimRight(result[:63], "-")
	}
	return result
}

func workspaceType(owner orc.ResourceOwner) string {
	if owner.Project.Present {
		return "project"
	}
	return "personal"
}

func workspaceId(owner orc.ResourceOwner) string {
	if owner.Project.Present {
		return owner.Project.Value
	}
	return owner.CreatedBy
}

func ownerFromEntry(entry cacheEntry) orc.ResourceOwner {
	if entry.WorkspaceType == "project" {
		return orc.ResourceOwner{Project: util.OptValue(entry.WorkspaceId)}
	}
	return orc.ResourceOwner{CreatedBy: entry.WorkspaceId}
}

func entryParams(entry cacheEntry) db.Params {
	return db.Params{"wt": entry.WorkspaceType, "wid": entry.WorkspaceId, "key": entry.CacheKey}
}

func mergeParams(left, right db.Params) db.Params {
	for key, value := range right {
		left[key] = value
	}
	return left
}
