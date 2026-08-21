package k8s

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"io"
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

// Initialization script images
// =====================================================================================================================
// This subsystem runs an initialization script in a temporary pod and stores the result as a container image.
// Jobs with the same application and script can use this image instead of running the script again.
//
// A cache entry belongs to one personal or project workspace. A job either builds the entry or waits for its builder.
// The database is the source of truth. Kubernetes jobs only perform preparation and snapshot work.
//
// The subsystem keeps ready images for 60 days. It also removes old images when a workspace exceeds its limit.

const (
	initScriptImagesDynamicParameterName = "ucCacheInitScript"
	initScriptImagesScriptParameterName  = "initScript"
	initScriptImagesCacheRepository      = "ucloud-init-cache"
	initScriptImagesPreparationContainer = "prepare"
	initScriptImagesPreparationDeadline  = int64(30 * 60)

	initScriptImagesPreparationLabel     = "ucloud.dk/init-cache-preparation"
	initScriptImagesPreparationJobLabel  = "ucloud.dk/init-cache-job-id"
	initScriptImagesPreparationTokenAnno = "ucloud.dk/init-cache-pull-token"
	initScriptImagesSnapshotLabel        = "ucloud.dk/init-cache-snapshot"
	initScriptImagesSnapshotJobLabel     = "ucloud.dk/init-cache-snapshot-job-id"
)

// Cache state
// ---------------------------------------------------------------------------------------------------------------------

type initScriptImagesCacheEntry struct {
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

type initScriptImagesPreparationDecision struct {
	Build bool
	Wait  bool
	Entry initScriptImagesCacheEntry
}

var initScriptImagesMonitors = struct {
	sync.Mutex
	jobs map[string]bool
}{jobs: map[string]bool{}}

var initScriptImagesMaintenanceMu sync.Mutex

// Job preparation
// ---------------------------------------------------------------------------------------------------------------------
// Preparation runs before scheduling. A ready entry lets scheduling continue. A build or wait keeps the job queued.

func initScriptImagesEnabledFor(job *orc.Job) bool {
	if !shared.ServiceConfig.Registry.Enabled || !job.Status.ResolvedApplication.Present {
		return false
	}

	cacheValue, ok := job.Specification.Parameters[initScriptImagesDynamicParameterName]
	if !ok || cacheValue.Type != orc.AppParameterValueTypeBoolean {
		return false
	}
	enabled, _ := cacheValue.Value.(bool)
	if !enabled {
		return false
	}
	script, ok := job.Specification.Parameters[initScriptImagesScriptParameterName]
	return ok && script.Type == orc.AppParameterValueTypeFile && script.Path != ""
}

func initScriptImagesPrepare(job *orc.Job, recoverMissingBuilder bool) (bool, *util.HttpError) {
	if !initScriptImagesEnabledFor(job) {
		return false, nil
	}

	script := job.Specification.Parameters[initScriptImagesScriptParameterName]
	internalPath, ok, _ := filesystem.UCloudToInternal(script.Path)
	if !ok {
		return false, util.UserHttpError("Unable to resolve initialization script")
	}
	fd, ok := filesystem.OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return false, util.UserHttpError("Unable to read initialization script")
	}
	defer util.SilentClose(fd)
	hash := sha256.New()
	_, _ = io.WriteString(hash, "ucloud-init-cache-v1\x00")
	_, _ = io.WriteString(hash, job.Specification.Application.Name+"\x00")
	_, _ = io.WriteString(hash, job.Specification.Application.Version+"\x00")
	_, _ = io.WriteString(hash, filepath.Clean(script.Path)+"\x00")
	if _, err := io.Copy(hash, fd); err != nil {
		return false, util.UserHttpError("Unable to read initialization script")
	}
	cacheKey := hex.EncodeToString(hash.Sum(nil))

	rootRepository, herr := registry.RepositoryFindDefault(job.Owner)
	if herr != nil {
		return false, herr
	}
	workspaceType := "personal"
	workspaceId := job.Owner.CreatedBy
	if job.Owner.Project.Present {
		workspaceType = "project"
		workspaceId = job.Owner.Project.Value
	}
	entry := initScriptImagesCacheEntry{
		WorkspaceType:  workspaceType,
		WorkspaceId:    workspaceId,
		CacheKey:       cacheKey,
		RepositoryName: rootRepository + "/" + initScriptImagesCacheRepository,
		Tag:            "sha256-" + cacheKey,
	}
	decision := initScriptImagesReserve(job.Id, entry)
	if decision.Entry.State == "ready" && decision.Entry.ImageDigest.Valid {
		if image, _, resolveErr := registry.InitScriptImagesResolve(job.Owner, decision.Entry.RepositoryName, decision.Entry.Tag); resolveErr == nil {
			initScriptImagesMarkJobReady(job.Id, image, decision.Entry)
			return false, nil
		}
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update k8s.init_script_image_cache
					set state = 'failed', image_digest = null, builder_job_id = null
					where
						workspace_type = :workspace_type
						and workspace_id = :workspace_id
						and cache_key = :cache_key
						and state = 'ready'
				`,
				db.Params{
					"workspace_type": decision.Entry.WorkspaceType,
					"workspace_id":   decision.Entry.WorkspaceId,
					"cache_key":      decision.Entry.CacheKey,
				},
			)
		})
		decision = initScriptImagesReserve(job.Id, decision.Entry)
	}
	if recoverMissingBuilder && decision.Wait && decision.Entry.BuilderJobId.Valid && decision.Entry.BuilderJobId.String == job.Id {
		_, getErr := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Get(
			context.Background(),
			initScriptImagesPreparationName(job.Id),
			meta.GetOptions{},
		)
		if apierrors.IsNotFound(getErr) {
			released := db.NewTx(func(tx *db.Transaction) bool {
				_, found := db.Get[struct{ CacheKey string }](
					tx,
					`
						update k8s.init_script_image_cache
						set state = 'failed', builder_job_id = null
						where
							workspace_type = :workspace_type
							and workspace_id = :workspace_id
							and cache_key = :cache_key
							and state = 'building'
							and builder_job_id = :builder_job_id
						returning cache_key
					`,
					db.Params{
						"workspace_type": decision.Entry.WorkspaceType,
						"workspace_id":   decision.Entry.WorkspaceId,
						"cache_key":      decision.Entry.CacheKey,
						"builder_job_id": job.Id,
					},
				)
				return found
			})
			if released {
				initScriptImagesCleanupPreparation(job.Id)
			}
			decision = initScriptImagesReserve(job.Id, decision.Entry)
		}
	}
	if decision.Wait {
		if decision.Entry.BuilderJobId.Valid && decision.Entry.BuilderJobId.String == job.Id {
			initScriptImagesMonitor(job.Id)
			return true, nil
		}
		builderActive := false
		if decision.Entry.BuilderJobId.Valid {
			builder, found := controller.JobRetrieve(decision.Entry.BuilderJobId.String)
			builderActive = found && builder.Status.State == orc.JobStateInQueue
		}
		if !builderActive {
			if decision.Entry.BuilderJobId.Valid {
				claimed := db.NewTx(func(tx *db.Transaction) bool {
					_, found := db.Get[struct{ CacheKey string }](
						tx,
						`
							update k8s.init_script_image_cache
							set state = 'failed', builder_job_id = null
							where
								workspace_type = :workspace_type
								and workspace_id = :workspace_id
								and cache_key = :cache_key
								and state = 'building'
								and builder_job_id = :builder_job_id
							returning cache_key
						`,
						db.Params{
							"workspace_type": decision.Entry.WorkspaceType,
							"workspace_id":   decision.Entry.WorkspaceId,
							"cache_key":      decision.Entry.CacheKey,
							"builder_job_id": decision.Entry.BuilderJobId.String,
						},
					)
					if found {
						db.Exec(
							tx,
							`
								delete from k8s.init_script_image_cache_jobs
								where job_id = :job_id
							`,
							db.Params{
								"job_id": decision.Entry.BuilderJobId.String,
							},
						)
					}
					return found
				})
				if claimed {
					initScriptImagesCleanupPreparation(decision.Entry.BuilderJobId.String)
					_ = registry.InitScriptImagesDelete(
						initScriptImagesOwnerFromEntry(decision.Entry),
						decision.Entry.RepositoryName,
						decision.Entry.Tag,
					)
				}
			}
			decision = initScriptImagesReserve(job.Id, decision.Entry)
			if decision.Build {
				initScriptImagesTrackMessage(job.Id, "Preparing a builder for your initialization script")
				if herr = initScriptImagesCreatePreparationJob(job, decision.Entry, internalPath); herr != nil {
					initScriptImagesMarkPreparationFailed(decision.Entry, "Unable to start image preparation: "+herr.Why, nil)
					return false, herr
				}
				initScriptImagesMonitor(job.Id)
				return true, nil
			}
		}
		initScriptImagesTrackMessage(job.Id, "Another job is preparing the requested container environment")
		return true, nil
	}
	if !decision.Build {
		return false, nil
	}

	initScriptImagesTrackMessage(job.Id, "Preparing a reusable container environment")
	if herr = initScriptImagesCreatePreparationJob(job, decision.Entry, internalPath); herr != nil {
		initScriptImagesMarkPreparationFailed(decision.Entry, "Unable to start image preparation: "+herr.Why, nil)
		return false, herr
	}
	initScriptImagesMonitor(job.Id)
	return true, nil
}

func initScriptImagesRecover(job *orc.Job) bool {
	if !initScriptImagesEnabledFor(job) {
		return false
	}
	if image, ok := initScriptImagesImageForJob(job.Id); ok {
		_ = image
		initScriptImagesCleanupPreparation(job.Id)
		return false
	}
	delayed, err := initScriptImagesPrepare(job, true)
	if err != nil {
		initScriptImagesFailJob(job, "Unable to recover initialization image preparation: "+err.Why, nil)
		return true
	}
	return delayed
}

func initScriptImagesCancel(jobId string) {
	entry, found := initScriptImagesEntryForJob(jobId)
	if !found {
		return
	}
	wasBuilder := false
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from k8s.init_script_image_cache_jobs
				where job_id = :job_id
			`,
			db.Params{
				"job_id": jobId,
			},
		)
		if entry.BuilderJobId.Valid && entry.BuilderJobId.String == jobId && entry.State == "building" {
			_, wasBuilder = db.Get[struct{ CacheKey string }](
				tx,
				`
					update k8s.init_script_image_cache
					set state = 'failed', builder_job_id = null
					where
						workspace_type = :workspace_type
						and workspace_id = :workspace_id
						and cache_key = :cache_key
						and state = 'building'
						and builder_job_id = :builder_job_id
					returning cache_key
				`,
				db.Params{
					"workspace_type": entry.WorkspaceType,
					"workspace_id":   entry.WorkspaceId,
					"cache_key":      entry.CacheKey,
					"builder_job_id": jobId,
				},
			)
		}
	})
	initScriptImagesCleanupPreparation(jobId)
	if wasBuilder {
		_ = registry.InitScriptImagesDelete(initScriptImagesOwnerFromEntry(entry), entry.RepositoryName, entry.Tag)
		go func() {
			for _, row := range initScriptImagesLinkedJobs(entry) {
				if job, ok := controller.JobRetrieve(row.JobId); ok && job.Status.State == orc.JobStateInQueue {
					_, _ = initScriptImagesPrepare(job, false)
					return
				}
			}
		}()
	}
}

func initScriptImagesImageForJob(jobId string) (string, bool) {
	return db.NewTx2[string, bool](func(tx *db.Transaction) (string, bool) {
		row, ok := db.Get[struct{ ImageDigest sql.NullString }](
			tx,
			`
				select
					c.image_digest
				from
					k8s.init_script_image_cache_jobs j
					join k8s.init_script_image_cache c using(workspace_type, workspace_id, cache_key)
				where
					j.job_id = :job_id
					and j.state = 'ready'
					and c.state = 'ready'
			`,
			db.Params{
				"job_id": jobId,
			},
		)
		return row.ImageDigest.String, ok && row.ImageDigest.Valid && row.ImageDigest.String != ""
	})
}

func initScriptImagesConsumesInitScript(jobId string) bool {
	_, ok := initScriptImagesImageForJob(jobId)
	return ok
}

func initScriptImagesStartMaintenance() {
	go func() {
		for util.IsAlive {
			initScriptImagesMaintainCache(util.OptNone[orc.ResourceOwner]())
			time.Sleep(time.Hour)
		}
	}()
}

// Cache reservation
// ---------------------------------------------------------------------------------------------------------------------
// One queued job owns a build. Other jobs bind to the same entry and wait until the builder publishes the image.

func initScriptImagesReserve(jobId string, desired initScriptImagesCacheEntry) initScriptImagesPreparationDecision {
	return db.NewTx(func(tx *db.Transaction) initScriptImagesPreparationDecision {
		_, inserted := db.Get[struct{ CacheKey string }](
			tx,
			`
				insert into k8s.init_script_image_cache(
					workspace_type,
					workspace_id,
					cache_key,
					repository_name,
					tag,
					state,
					builder_job_id
				) values (
					:workspace_type,
					:workspace_id,
					:cache_key,
					:repository_name,
					:tag,
					'building',
					:job_id
				)
				on conflict(workspace_type, workspace_id, cache_key) do nothing
				returning cache_key
			`,
			db.Params{
				"workspace_type":  desired.WorkspaceType,
				"workspace_id":    desired.WorkspaceId,
				"cache_key":       desired.CacheKey,
				"repository_name": desired.RepositoryName,
				"tag":             desired.Tag,
				"job_id":          jobId,
			},
		)
		if inserted {
			desired.State = "building"
			desired.BuilderJobId = sql.NullString{String: jobId, Valid: true}
			initScriptImagesUpsertJobBinding(tx, jobId, desired, "preparing")
			return initScriptImagesPreparationDecision{Build: true, Entry: desired}
		}

		row, found := db.Get[initScriptImagesCacheEntry](
			tx,
			`
				select
					workspace_type,
					workspace_id,
					cache_key,
					repository_name,
					tag,
					image_digest,
					exact_bytes,
					state,
					builder_job_id,
					created_at,
					last_used_at
				from
					k8s.init_script_image_cache
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
				for update
			`,
			db.Params{
				"workspace_type": desired.WorkspaceType,
				"workspace_id":   desired.WorkspaceId,
				"cache_key":      desired.CacheKey,
			},
		)
		if found && row.State == "ready" {
			initScriptImagesUpsertJobBinding(tx, jobId, row, "ready")
			return initScriptImagesPreparationDecision{Entry: row}
		}
		if found && row.State == "building" {
			initScriptImagesUpsertJobBinding(tx, jobId, row, "waiting")
			return initScriptImagesPreparationDecision{Wait: true, Entry: row}
		}
		if !found {
			log.Fatal("Init-script image cache entry disappeared during reservation")
		}
		db.Exec(
			tx,
			`
				update k8s.init_script_image_cache
				set
					repository_name = :repository_name,
					tag = :tag,
					image_digest = null,
					exact_bytes = 0,
					state = 'building',
					builder_job_id = :job_id,
					created_at = now(),
					last_used_at = now()
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"workspace_type":  desired.WorkspaceType,
				"workspace_id":    desired.WorkspaceId,
				"cache_key":       desired.CacheKey,
				"repository_name": desired.RepositoryName,
				"tag":             desired.Tag,
				"job_id":          jobId,
			},
		)
		desired.State = "building"
		desired.BuilderJobId = sql.NullString{String: jobId, Valid: true}
		initScriptImagesUpsertJobBinding(tx, jobId, desired, "preparing")
		return initScriptImagesPreparationDecision{Build: true, Entry: desired}
	})
}

func initScriptImagesUpsertJobBinding(tx *db.Transaction, jobId string, entry initScriptImagesCacheEntry, state string) {
	db.Exec(
		tx,
		`
			insert into k8s.init_script_image_cache_jobs(
				job_id,
				workspace_type,
				workspace_id,
				cache_key,
				state
			) values (
				:job_id,
				:workspace_type,
				:workspace_id,
				:cache_key,
				:state
			)
			on conflict(job_id) do update set
				workspace_type = excluded.workspace_type,
				workspace_id = excluded.workspace_id,
				cache_key = excluded.cache_key,
				state = excluded.state
		`,
		db.Params{
			"job_id":         jobId,
			"workspace_type": entry.WorkspaceType,
			"workspace_id":   entry.WorkspaceId,
			"cache_key":      entry.CacheKey,
			"state":          state,
		},
	)
}

// Image construction
// ---------------------------------------------------------------------------------------------------------------------
// The preparation pod runs the script with a read-only file mount. A second job snapshots its container on that node.

func initScriptImagesCreatePreparationJob(job *orc.Job, entry initScriptImagesCacheEntry, internalPath string) *util.HttpError {
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

	name := initScriptImagesPreparationName(job.Id)
	token, herr := registry.ApiTokensCreateForPull(job.Owner, time.Hour)
	if herr != nil {
		return herr
	}
	credentials := base64.StdEncoding.EncodeToString([]byte("ucloud:" + token.Secret))
	configuration, _ := json.Marshal(map[string]any{"auths": map[string]any{
		registry.Service(): map[string]string{"username": "ucloud", "password": token.Secret, "auth": credentials},
	}})
	secret := &core.Secret{
		ObjectMeta: meta.ObjectMeta{
			Name:      initScriptImagesPreparationSecretName(job.Id),
			Namespace: shared.ServiceConfig.Compute.TaskNamespace,
		},
		Type: core.SecretTypeDockerConfigJson,
		Data: map[string][]byte{
			core.DockerConfigJsonKey: configuration,
		},
	}
	pullSecret, err := shared.K8sClient.CoreV1().Secrets(shared.ServiceConfig.Compute.TaskNamespace).Create(
		context.Background(),
		secret,
		meta.CreateOptions{},
	)
	if err != nil {
		registry.ApiTokensRevoke(token.Id)
		return util.HttpErrorFromErr(err)
	}
	backoff := int32(0)
	preparation := &batch.Job{
		ObjectMeta: meta.ObjectMeta{
			Name: name, Namespace: shared.ServiceConfig.Compute.TaskNamespace,
			Labels:      map[string]string{initScriptImagesPreparationLabel: "true", initScriptImagesPreparationJobLabel: job.Id},
			Annotations: map[string]string{initScriptImagesPreparationTokenAnno: token.Id},
		},
		Spec: batch.JobSpec{
			BackoffLimit: &backoff, ActiveDeadlineSeconds: util.Pointer(initScriptImagesPreparationDeadline),
			Template: core.PodTemplateSpec{Spec: core.PodSpec{
				AutomountServiceAccountToken: util.Pointer(false), EnableServiceLinks: util.Pointer(false),
				RestartPolicy: core.RestartPolicyNever, NodeSelector: shared.ServiceConfig.Compute.TaskNodeSelector,
				ImagePullSecrets: []core.LocalObjectReference{{Name: pullSecret.Name}},
				Volumes: []core.Volume{{Name: "init-script", VolumeSource: core.VolumeSource{
					PersistentVolumeClaim: &core.PersistentVolumeClaimVolumeSource{ClaimName: shared.ServiceConfig.FileSystem.ClaimName, ReadOnly: true},
				}}},
				Containers: []core.Container{{
					Name:            initScriptImagesPreparationContainer,
					Image:           image,
					ImagePullPolicy: core.PullIfNotPresent,
					Command:         []string{"bash", "-lc"},
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
		initScriptImagesDeleteSecret(pullSecret.Name)
		registry.ApiTokensRevoke(token.Id)
		return util.HttpErrorFromErr(err)
	}
	return nil
}

// Build monitoring
// ---------------------------------------------------------------------------------------------------------------------
// Each active builder has one local monitor. Database checks stop monitors after cancellation or ownership changes.

func initScriptImagesMonitor(jobId string) {
	initScriptImagesMonitors.Lock()
	if initScriptImagesMonitors.jobs[jobId] {
		initScriptImagesMonitors.Unlock()
		return
	}
	initScriptImagesMonitors.jobs[jobId] = true
	initScriptImagesMonitors.Unlock()
	go initScriptImagesRunMonitor(jobId)
}

func initScriptImagesRunMonitor(jobId string) {
	defer func() {
		initScriptImagesMonitors.Lock()
		delete(initScriptImagesMonitors.jobs, jobId)
		initScriptImagesMonitors.Unlock()
	}()
	entry, found := initScriptImagesEntryForJob(jobId)
	if !found {
		return
	}
	name := initScriptImagesPreparationName(jobId)
	observed := false
	var preparationPod *core.Pod
	for util.IsAlive {
		if _, found = initScriptImagesEntryForJob(jobId); !found {
			return
		}
		job, present := shared.BatchBackgroundJobs.Retrieve(shared.ServiceConfig.Compute.TaskNamespace + "/" + name)
		if !present {
			if observed {
				initScriptImagesMarkPreparationFailed(entry, "Image preparation job disappeared", nil)
				return
			}
			time.Sleep(2 * time.Second)
			continue
		}
		observed = true
		if initScriptImagesKubernetesJobFailed(job) {
			logs := initScriptImagesLogsForJob(name, initScriptImagesPreparationContainer)
			initScriptImagesMarkPreparationFailed(entry, "Initialization script failed", logs)
			return
		}
		for _, pod := range shared.BatchBackgroundPods.List() {
			if pod.Labels["job-name"] != name && pod.Labels["batch.kubernetes.io/job-name"] != name {
				continue
			}
			for _, status := range pod.Status.ContainerStatuses {
				if status.Name == initScriptImagesPreparationContainer && status.Ready && status.State.Running != nil {
					preparationPod = pod
					break
				}
			}
			if preparationPod != nil {
				break
			}
		}
		if preparationPod != nil {
			break
		}
		time.Sleep(2 * time.Second)
	}
	if preparationPod == nil {
		return
	}
	initScriptImagesTrackForEntry(entry, "Initialization script completed; publishing the container environment")
	image, snapshotLogs, herr := initScriptImagesSnapshotPreparation(jobId, entry, preparationPod)
	if herr != nil {
		currentEntry, active := initScriptImagesEntryForJob(jobId)
		if !active || !currentEntry.BuilderJobId.Valid || currentEntry.BuilderJobId.String != jobId {
			return
		}
		logs := initScriptImagesLogsForJob(name, initScriptImagesPreparationContainer)
		if len(snapshotLogs) > 0 {
			logs = append(logs, []byte("\n\nSnapshot helper:\n")...)
			logs = append(logs, snapshotLogs...)
		}
		initScriptImagesMarkPreparationFailed(entry, "Unable to publish the prepared container environment: "+herr.Why, logs)
		return
	}
	_, exactBytes, herr := registry.InitScriptImagesResolve(initScriptImagesOwnerFromEntry(entry), entry.RepositoryName, entry.Tag)
	if herr != nil {
		initScriptImagesMarkPreparationFailed(entry, "Unable to validate the prepared container environment: "+herr.Why, nil)
		return
	}
	currentEntry, active := initScriptImagesEntryForJob(jobId)
	if !active || !currentEntry.BuilderJobId.Valid || currentEntry.BuilderJobId.String != jobId {
		return
	}
	published := db.NewTx(func(tx *db.Transaction) bool {
		_, found := db.Get[struct{ CacheKey string }](
			tx,
			`
				update k8s.init_script_image_cache
				set
					state = 'ready',
					image_digest = :image,
					exact_bytes = :exact_bytes,
					builder_job_id = null,
					last_used_at = now()
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
					and state = 'building'
					and builder_job_id = :builder_job_id
				returning cache_key
			`,
			db.Params{
				"image":          image,
				"exact_bytes":    exactBytes,
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
				"builder_job_id": jobId,
			},
		)
		if !found {
			return false
		}
		db.Exec(
			tx,
			`
				update k8s.init_script_image_cache_jobs
				set state = 'ready'
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
		return true
	})
	if !published {
		return
	}
	initScriptImagesTrackForEntry(entry, "Container environment is ready; waiting for compute resources")
	initScriptImagesCleanupPreparation(jobId)
	jobIds := db.NewTx(func(tx *db.Transaction) []struct{ JobId string } {
		return db.Select[struct{ JobId string }](
			tx,
			`
				select
					job_id
				from
					k8s.init_script_image_cache_jobs
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
					and state = 'ready'
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
	})
	for _, row := range jobIds {
		if job, ok := controller.JobRetrieve(row.JobId); ok && job.Status.State == orc.JobStateInQueue {
			shared.RequestSchedule(job)
		}
	}
	go initScriptImagesMaintainCache(util.OptValue(initScriptImagesOwnerFromEntry(entry)))
}

func initScriptImagesSnapshotPreparation(jobId string, entry initScriptImagesCacheEntry, pod *core.Pod) (string, []byte, *util.HttpError) {
	containerId := ""
	for _, status := range pod.Status.ContainerStatuses {
		if status.Name == initScriptImagesPreparationContainer && status.State.Running != nil {
			containerId = status.ContainerID
			break
		}
	}
	if containerId == "" || pod.Spec.NodeName == "" {
		return "", nil, util.ServerHttpError("preparation container is not available for snapshotting")
	}
	destination, herr := containerSnapshotOperationDestination(entry.RepositoryName + ":" + entry.Tag)
	if herr != nil {
		return "", nil, herr
	}
	name := initScriptImagesSnapshotName(jobId)
	deadline := min(shared.ServiceConfig.Registry.Snapshot.DeadlineSeconds, int(initScriptImagesPreparationDeadline))
	execution, herr := containerSnapshotOperationStart(containerSnapshotOperationRequest{
		Name:          name,
		NodeName:      pod.Spec.NodeName,
		ContainerId:   containerId,
		Destination:   destination,
		Owner:         initScriptImagesOwnerFromEntry(entry),
		Deadline:      deadline,
		AllowExisting: true,
		Labels: map[string]string{
			initScriptImagesSnapshotLabel:    "true",
			initScriptImagesSnapshotJobLabel: jobId,
		},
	})
	if herr != nil {
		return "", nil, herr
	}
	result := <-execution.Done
	close(execution.Cleanup)
	if result.Err != "" {
		return "", result.Logs, util.ServerHttpError("%s", result.Err)
	}
	resolved, _, resolveErr := registry.InitScriptImagesResolve(initScriptImagesOwnerFromEntry(entry), entry.RepositoryName, entry.Tag)
	return resolved, nil, resolveErr
}

func initScriptImagesMarkJobReady(jobId, image string, entry initScriptImagesCacheEntry) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update k8s.init_script_image_cache
				set image_digest = :image, last_used_at = now()
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"image":          image,
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
		db.Exec(
			tx,
			`
				update k8s.init_script_image_cache_jobs
				set state = 'ready'
				where job_id = :job_id
			`,
			db.Params{
				"job_id": jobId,
			},
		)
	})
	initScriptImagesTrackMessage(jobId, "Reusing a cached environment")
}

func initScriptImagesMarkPreparationFailed(entry initScriptImagesCacheEntry, message string, logs []byte) {
	if !entry.BuilderJobId.Valid {
		return
	}
	linked := []struct{ JobId string }{}
	claimed := db.NewTx(func(tx *db.Transaction) bool {
		_, found := db.Get[struct{ CacheKey string }](
			tx,
			`
				update k8s.init_script_image_cache
				set state = 'failed', builder_job_id = null
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
					and state = 'building'
					and builder_job_id = :builder_job_id
				returning cache_key
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
				"builder_job_id": entry.BuilderJobId.String,
			},
		)
		if !found {
			return false
		}
		linked = db.Select[struct{ JobId string }](
			tx,
			`
				select
					job_id
				from
					k8s.init_script_image_cache_jobs
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
		db.Exec(
			tx,
			`
				delete from k8s.init_script_image_cache_jobs
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
		return true
	})
	if !claimed {
		return
	}
	_ = registry.InitScriptImagesDelete(initScriptImagesOwnerFromEntry(entry), entry.RepositoryName, entry.Tag)
	initScriptImagesCleanupPreparation(entry.BuilderJobId.String)
	for _, row := range linked {
		if job, ok := controller.JobRetrieve(row.JobId); ok && job.Status.State == orc.JobStateInQueue {
			initScriptImagesFailJob(job, message, logs)
		}
	}
}

func initScriptImagesFailJob(job *orc.Job, message string, logs []byte) {
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

// Cache maintenance
// ---------------------------------------------------------------------------------------------------------------------
// Maintenance removes old job bindings first. It then expires images and reduces excess usage to 80 percent.
// Builder completion limits this work to one owner. The hourly loop processes all owners.

func initScriptImagesMaintainCache(owner util.Option[orc.ResourceOwner]) {
	initScriptImagesMaintenanceMu.Lock()
	defer initScriptImagesMaintenanceMu.Unlock()
	var workspaceType any
	var workspaceId any
	if owner.Present {
		workspaceType = "personal"
		workspaceId = owner.Value.CreatedBy
		if owner.Value.Project.Present {
			workspaceType = "project"
			workspaceId = owner.Value.Project.Value
		}
	}

	jobBindings := db.NewTx(func(tx *db.Transaction) []struct{ JobId string } {
		return db.Select[struct{ JobId string }](
			tx,
			`
				select
					job_id
				from
					k8s.init_script_image_cache_jobs
				where
					(
						cast(:workspace_type as text) is null
						or (
							workspace_type = cast(:workspace_type as text)
							and workspace_id = cast(:workspace_id as text)
						)
					)
			`,
			db.Params{
				"workspace_type": workspaceType,
				"workspace_id":   workspaceId,
			},
		)
	})
	for _, row := range jobBindings {
		job, ok := controller.JobRetrieve(row.JobId)
		if !ok || job.Status.State == orc.JobStateSuccess || job.Status.State == orc.JobStateFailure || job.Status.State == orc.JobStateExpired {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						delete from k8s.init_script_image_cache_jobs
						where job_id = :job_id
					`,
					db.Params{
						"job_id": row.JobId,
					},
				)
			})
		}
	}

	entries := db.NewTx(func(tx *db.Transaction) []initScriptImagesCacheEntry {
		return db.Select[initScriptImagesCacheEntry](
			tx,
			`
				select
					workspace_type,
					workspace_id,
					cache_key,
					repository_name,
					tag,
					image_digest,
					exact_bytes,
					state,
					builder_job_id,
					created_at,
					last_used_at
				from
					k8s.init_script_image_cache
				where
					state = 'ready'
					and (
						cast(:workspace_type as text) is null
						or (
							workspace_type = cast(:workspace_type as text)
							and workspace_id = cast(:workspace_id as text)
						)
					)
				order by last_used_at asc
			`,
			db.Params{
				"workspace_type": workspaceType,
				"workspace_id":   workspaceId,
			},
		)
	})
	byWorkspace := map[string][]initScriptImagesCacheEntry{}
	for _, entry := range entries {
		key := entry.WorkspaceType + "\x00" + entry.WorkspaceId
		byWorkspace[key] = append(byWorkspace[key], entry)
	}
	cutoff := time.Now().Add(-60 * 24 * time.Hour)
	for _, workspaceEntries := range byWorkspace {
		for _, entry := range workspaceEntries {
			if entry.LastUsedAt.Before(cutoff) && !initScriptImagesEntryLeased(entry) {
				initScriptImagesDeleteEntry(entry)
			}
		}
		workspaceEntries = initScriptImagesReadyEntriesForWorkspace(workspaceEntries[0])
		if len(workspaceEntries) == 0 {
			continue
		}
		limit := fnd.DefaultInitScriptImageCacheLimitBytes
		if workspaceEntries[0].WorkspaceType == "project" {
			if project, ok := controller.ProjectRetrieve(workspaceEntries[0].WorkspaceId); ok {
				limit = project.Status.Settings.InitScriptImageCacheLimitBytes
			}
		}
		usage := initScriptImagesCacheUsage(workspaceEntries)
		if usage <= limit {
			continue
		}
		target := limit * 80 / 100
		for _, entry := range workspaceEntries {
			if usage <= target {
				break
			}
			if initScriptImagesEntryLeased(entry) {
				continue
			}
			initScriptImagesDeleteEntry(entry)
			remaining := initScriptImagesReadyEntriesForWorkspace(entry)
			usage = initScriptImagesCacheUsage(remaining)
		}
	}
}

func initScriptImagesDeleteEntry(entry initScriptImagesCacheEntry) {
	if err := registry.InitScriptImagesDelete(initScriptImagesOwnerFromEntry(entry), entry.RepositoryName, entry.Tag); err != nil {
		log.Warn("Unable to evict init-script image %s:%s: %s", entry.RepositoryName, entry.Tag, err.Why)
		return
	}
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from k8s.init_script_image_cache
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
	})
}

func initScriptImagesCacheUsage(entries []initScriptImagesCacheEntry) int64 {
	if len(entries) == 0 {
		return 0
	}
	tags := make([]string, 0, len(entries))
	for _, entry := range entries {
		tags = append(tags, entry.Tag)
	}
	usage, err := registry.InitScriptImagesUsage(entries[0].RepositoryName, tags)
	if err != nil {
		log.Warn("Unable to calculate init-script image cache usage: %s", err.Why)
		return 0
	}
	return usage
}

func initScriptImagesReadyEntriesForWorkspace(entry initScriptImagesCacheEntry) []initScriptImagesCacheEntry {
	return db.NewTx(func(tx *db.Transaction) []initScriptImagesCacheEntry {
		return db.Select[initScriptImagesCacheEntry](
			tx,
			`
				select
					workspace_type,
					workspace_id,
					cache_key,
					repository_name,
					tag,
					image_digest,
					exact_bytes,
					state,
					builder_job_id,
					created_at,
					last_used_at
				from
					k8s.init_script_image_cache
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and state = 'ready'
				order by last_used_at asc
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
			},
		)
	})
}

func initScriptImagesEntryLeased(entry initScriptImagesCacheEntry) bool {
	return db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ JobId string }](
			tx,
			`
				select
					job_id
				from
					k8s.init_script_image_cache_jobs
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
				limit 1
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
		return ok
	})
}

func initScriptImagesEntryForJob(jobId string) (initScriptImagesCacheEntry, bool) {
	return db.NewTx2[initScriptImagesCacheEntry, bool](func(tx *db.Transaction) (initScriptImagesCacheEntry, bool) {
		return db.Get[initScriptImagesCacheEntry](
			tx,
			`
				select
					c.workspace_type,
					c.workspace_id,
					c.cache_key,
					c.repository_name,
					c.tag,
					c.image_digest,
					c.exact_bytes,
					c.state,
					c.builder_job_id,
					c.created_at,
					c.last_used_at
				from
					k8s.init_script_image_cache_jobs j
					join k8s.init_script_image_cache c using(workspace_type, workspace_id, cache_key)
				where
					j.job_id = :job_id
			`,
			db.Params{
				"job_id": jobId,
			},
		)
	})
}

func initScriptImagesLinkedJobs(entry initScriptImagesCacheEntry) []struct{ JobId string } {
	return db.NewTx(func(tx *db.Transaction) []struct{ JobId string } {
		return db.Select[struct{ JobId string }](
			tx,
			`
				select
					job_id
				from
					k8s.init_script_image_cache_jobs
				where
					workspace_type = :workspace_type
					and workspace_id = :workspace_id
					and cache_key = :cache_key
			`,
			db.Params{
				"workspace_type": entry.WorkspaceType,
				"workspace_id":   entry.WorkspaceId,
				"cache_key":      entry.CacheKey,
			},
		)
	})
}

func initScriptImagesTrackForEntry(entry initScriptImagesCacheEntry, message string) {
	for _, row := range initScriptImagesLinkedJobs(entry) {
		initScriptImagesTrackMessage(row.JobId, message)
	}
}

func initScriptImagesTrackMessage(jobId, message string) {
	_ = controller.JobTrackMessage([]controller.JobMessage{{JobId: jobId, Message: message}})
}

// Kubernetes resource cleanup
// ---------------------------------------------------------------------------------------------------------------------

func initScriptImagesCleanupPreparation(jobId string) {
	if jobId == "" {
		return
	}
	for _, name := range []string{initScriptImagesPreparationName(jobId), initScriptImagesSnapshotName(jobId)} {
		if current, present := shared.BatchBackgroundJobs.Retrieve(shared.ServiceConfig.Compute.TaskNamespace + "/" + name); present {
			registry.ApiTokensRevoke(current.Annotations[initScriptImagesPreparationTokenAnno])
			registry.ApiTokensRevoke(current.Annotations[containerSnapshotTokenAnnotation])
		}
		propagation := meta.DeletePropagationBackground
		err := shared.K8sClient.BatchV1().Jobs(shared.ServiceConfig.Compute.TaskNamespace).Delete(
			context.Background(),
			name,
			meta.DeleteOptions{PropagationPolicy: &propagation},
		)
		if err != nil && !apierrors.IsNotFound(err) {
			log.Warn("Unable to delete init-script cache job %s: %v", name, err)
		}
	}
	initScriptImagesDeleteSecret(initScriptImagesPreparationSecretName(jobId))
}

func initScriptImagesPodsForJob(name string) ([]core.Pod, error) {
	pods, err := shared.K8sClient.CoreV1().Pods(shared.ServiceConfig.Compute.TaskNamespace).List(
		context.Background(), meta.ListOptions{LabelSelector: labels.Set{"job-name": name}.AsSelector().String()},
	)
	if err != nil {
		return nil, err
	}
	return pods.Items, nil
}

func initScriptImagesLogsForJob(name, container string) []byte {
	pods, err := initScriptImagesPodsForJob(name)
	if err != nil || len(pods) == 0 {
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

func initScriptImagesKubernetesJobFailed(job *batch.Job) bool {
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

func initScriptImagesDeleteSecret(name string) {
	err := shared.K8sClient.CoreV1().Secrets(shared.ServiceConfig.Compute.TaskNamespace).Delete(context.Background(), name, meta.DeleteOptions{})
	if err != nil && !apierrors.IsNotFound(err) {
		log.Warn("Unable to delete init-script cache secret %s: %v", name, err)
	}
}

func initScriptImagesPreparationName(jobId string) string {
	return initScriptImagesResourceName("init-cache-", jobId)
}

func initScriptImagesSnapshotName(jobId string) string {
	return initScriptImagesResourceName("init-cache-snapshot-", jobId)
}

func initScriptImagesPreparationSecretName(jobId string) string {
	return initScriptImagesResourceName("init-cache-pull-", jobId)
}

func initScriptImagesResourceName(prefix, id string) string {
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

func initScriptImagesOwnerFromEntry(entry initScriptImagesCacheEntry) orc.ResourceOwner {
	if entry.WorkspaceType == "project" {
		return orc.ResourceOwner{Project: util.OptValue(entry.WorkspaceId)}
	}
	return orc.ResourceOwner{CreatedBy: entry.WorkspaceId}
}
