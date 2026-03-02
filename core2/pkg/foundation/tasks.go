package foundation

import (
	"context"
	"database/sql"
	"fmt"
	"net/http"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Introduction
// =====================================================================================================================
// THe task system provides a system for managing asynchronous work items, known as tasks, originating from providers
// and belonging to end-users. It is designed for high read+write concurrency with low-latency lookups. This system
// is largely used for background tasks that a provider might create in response to user actions. For example, this
// might be used when copying large amounts of data.
//
// Conceptually, a task records:
//
// - A stable identifier
// - Creation and modification timestamps
// - Provider-specified payload describing status, progress and other metadata
// - Provider-specified capabilities around pausing and cancellation
// - Which user the task belongs to
//
// Buckets
// ---------------------------------------------------------------------------------------------------------------------
// Tasks are distributed across a set of independent buckets. Each bucket contains its own task map, per-user index,
// and subscription registry. This reduces lock contention and allows read/write operations to scale across CPUs
// without a central bottleneck.
//
// Persistence
// ---------------------------------------------------------------------------------------------------------------------
// A background loop periodically persists all dirty tasks to the database, while newly created tasks are written
// immediately. This maintains correctness for external systems that rely on strongly consistent reads of
// provider/task relationships. This is specifically required when implementing cancellation and pausing (see
// orchestrator deployment).
//
// Subscriptions
// ---------------------------------------------------------------------------------------------------------------------
// Users subscribe to server-pushed task events through WebSocket-backed channels. Buckets maintain per-user
// subscription sets and broadcast updates efficiently without global coordination.

func initTasks() {
	fndapi.TasksRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (fndapi.Task, *util.HttpError) {
		task, ok := TaskRetrieve(info.Actor, request.Id)
		if !ok {
			return fndapi.Task{}, util.HttpErr(http.StatusNotFound, "not found")
		} else {
			return task, nil
		}
	})

	fndapi.TasksBrowse.Handler(func(info rpc.RequestInfo, request fndapi.TasksBrowseRequest) (fndapi.PageV2[fndapi.Task], *util.HttpError) {
		return TaskBrowse(info.Actor, request.ItemsPerPage, request.Next), nil
	})

	fndapi.TasksCreate.Handler(func(info rpc.RequestInfo, request fndapi.TasksCreateRequest) (fndapi.Task, *util.HttpError) {
		return TaskCreate(info.Actor, request)
	})

	fndapi.TasksMarkAsComplete.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, TaskMarkAsComplete(info.Actor, request.Id)
	})

	fndapi.TasksPostStatus.Handler(func(info rpc.RequestInfo, request fndapi.TasksPostStatusRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, TaskPostStatus(info.Actor, request.Update)
	})

	fndapi.TasksListen.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		TaskListen(info.WebSocket)
		return util.Empty{}, nil
	})

	for i := 0; i < runtime.NumCPU(); i++ {
		taskGlobals.Buckets = append(taskGlobals.Buckets, &taskBucket{
			UserIndex:         map[string][]taskId{},
			UserSubscriptions: map[string]map[string]*taskSubscription{},
			Tasks:             map[taskId]*internalTask{},
		})
	}

	if !taskGlobals.TestingEnabled {
		db.NewTx0(func(tx *db.Transaction) {
			row, ok := db.Get[struct{ MaxId sql.Null[int] }](
				tx,
				`
					select coalesce(max(id), 0) as max_id
					from task.tasks_v2
			    `,
				db.Params{},
			)

			if ok && row.MaxId.Valid {
				taskGlobals.IdAcc.Store(int64(row.MaxId.V))
			}
		})
	}

	go func() {
		for {
			start := time.Now()
			taskPersistAll()
			timeToPersist := time.Now().Sub(start)
			time.Sleep(10*time.Second - timeToPersist)
		}
	}()
}

// Internal state
// =====================================================================================================================

type taskId int

var taskGlobals struct {
	Buckets        []*taskBucket
	IdAcc          atomic.Int64
	TestingEnabled bool
}

type taskBucket struct {
	Mu                sync.RWMutex
	UserIndex         map[string][]taskId
	UserSubscriptions map[string]map[string]*taskSubscription // user -> session id -> subscription
	Tasks             map[taskId]*internalTask
}

type internalTask struct {
	Mu    sync.RWMutex
	Id    taskId
	Task  *fndapi.Task
	Dirty bool
}

type taskSubscription struct {
	SessionId string
	Ctx       context.Context
	Channel   chan fndapi.Task
}

func taskBucketByUser(username string) *taskBucket {
	return taskGlobals.Buckets[util.NonCryptographicHash(username)%len(taskGlobals.Buckets)]
}

func taskBucketById(id taskId) *taskBucket {
	return taskGlobals.Buckets[util.NonCryptographicHash(id)%len(taskGlobals.Buckets)]
}

// Internal API
// =====================================================================================================================

func taskRetrieve(id taskId, loadIfNeeded bool) (*internalTask, bool) {
	b := taskBucketById(id)
	b.Mu.RLock()
	result, ok := b.Tasks[id]
	b.Mu.RUnlock()

	if ok {
		return result, true
	} else if loadIfNeeded {
		taskLoad(util.OptValue(id), util.OptNone[string]())
		return taskRetrieve(id, false)
	} else {
		return &internalTask{}, false
	}
}

func lTaskIsActive(task *internalTask) bool {
	// NOTE(Dan): This is also repeated in the load query
	return time.Now().Sub(task.Task.ModifiedAt.Time()) < 5*time.Minute
}

func lTaskPersist(b *db.Batch, task *internalTask) {
	// NOTE(Dan): Do not insert a check on the dirty flag here.
	task.Dirty = false

	db.BatchExec(
		b,
		`
			insert into task.tasks_v2(id, created_at, modified_at, created_by, owned_by, state, progress, 
				can_pause, can_cancel, progress_percentage, icon, body, title)  
			values (:id, :created_at, :modified_at, :created_by, :provider, :state, :progress, :can_pause, :can_cancel,
				:progress_percent, :icon, :body, :title)
			on conflict (id) do update set
				modified_at = excluded.modified_at,
				state = excluded.state,
				progress = excluded.progress,
				progress_percentage = excluded.progress_percentage,
				can_pause = excluded.can_pause,
				can_cancel = excluded.can_cancel,
				icon = excluded.icon,
				body = excluded.body,
				title = excluded.title
	    `,
		db.Params{
			"id":               task.Id,
			"created_at":       task.Task.CreatedAt.Time(),
			"modified_at":      task.Task.ModifiedAt.Time(),
			"created_by":       task.Task.CreatedBy,
			"provider":         task.Task.Provider,
			"state":            task.Task.Status.State,
			"progress":         task.Task.Status.Progress.Sql(),
			"can_pause":        task.Task.Specification.CanPause,
			"can_cancel":       task.Task.Specification.CanCancel,
			"progress_percent": task.Task.Status.ProgressPercentage.GetOrDefault(-1.0),
			"icon":             task.Task.Icon.Sql(),
			"body":             task.Task.Status.Body.Sql(),
			"title":            task.Task.Status.Title.Sql(),
		},
	)
}

func taskPersistAll() {
	if taskGlobals.TestingEnabled {
		return
	}

	persistedInTotal := 0
	start := time.Now()
	for i := 0; i < len(taskGlobals.Buckets); i++ {
		var dirtyTasks []*internalTask
		{
			b := taskGlobals.Buckets[i]
			b.Mu.RLock()
			for _, task := range b.Tasks {
				task.Mu.RLock()
				if task.Dirty {
					dirtyTasks = append(dirtyTasks, task)
				}
				task.Mu.RUnlock()
			}
			b.Mu.RUnlock()
		}

		if len(dirtyTasks) > 0 {
			persistedInTotal += len(dirtyTasks)

			db.NewTx0(func(tx *db.Transaction) {
				batch := db.BatchNew(tx)

				// NOTE(Dan): Holding the lock here is not a big problem since appending to the batch is _not_ a remote
				// operation.
				for _, task := range dirtyTasks {
					task.Mu.Lock()
					lTaskPersist(batch, task)
					task.Mu.Unlock()
				}

				db.BatchSend(batch)
			})
		}
	}
	end := time.Now()
	taskPersistCycleDuration.Observe(end.Sub(start).Seconds())
	taskPersistDirtyTasks.Add(float64(persistedInTotal))
}

func taskLoad(id util.Option[taskId], username util.Option[string]) {
	if !id.Present && !username.Present {
		log.Fatal("id or username must be present")
	}

	if taskGlobals.TestingEnabled {
		return
	}

	tasks := db.NewTx(func(tx *db.Transaction) []*internalTask {
		rows := db.Select[struct {
			Id                 int
			CreatedAt          time.Time
			ModifiedAt         time.Time
			CreatedBy          string
			ProviderId         string
			State              string
			Progress           sql.Null[string]
			CanPause           bool
			CanCancel          bool
			ProgressPercentage float64
			Icon               sql.Null[string]
			Body               sql.Null[string]
			Title              sql.Null[string]
		}](
			tx,
			`
				select
					id,
					created_at,
					modified_at,
					created_by,
					owned_by as provider_id,
					state,
					progress,
					can_pause,
					can_cancel,
					progress_percentage,
					icon,
					body,
					title
				from
					task.tasks_v2
				where
					(
						:id::int8 is null
						or (
							now() - modified_at < '5 minute'::interval
							or now() - created_at < '5 minute'::interval
						)
					)
					and (
						:username::text is null
						or created_by = :username::text
					)
					and (
						:id::int8 is null
						or :id::int8 = id
					)
				order by id
		    `,
			db.Params{
				"id":       id.Sql(),
				"username": username.Sql(),
			},
		)

		var result []*internalTask
		for _, row := range rows {
			progressPercent := util.OptValue(row.ProgressPercentage)
			if row.ProgressPercentage < 0 {
				progressPercent.Clear()
			}

			result = append(result, &internalTask{
				Id: taskId(row.Id),
				Task: &fndapi.Task{
					Id:         row.Id,
					CreatedAt:  fndapi.Timestamp(row.CreatedAt),
					ModifiedAt: fndapi.Timestamp(row.ModifiedAt),
					CreatedBy:  row.CreatedBy,
					Provider:   row.ProviderId,
					Icon:       util.SqlNullToOpt(row.Icon),
					Status: fndapi.TaskStatus{
						State:              fndapi.TaskState(row.State),
						Title:              util.SqlNullToOpt(row.Title),
						Body:               util.SqlNullToOpt(row.Body),
						Progress:           util.SqlNullToOpt(row.Progress),
						ProgressPercentage: progressPercent,
					},
					Specification: fndapi.TaskSpecification{
						CanPause:  row.CanPause,
						CanCancel: row.CanCancel,
					},
				},
			})
		}
		return result
	})

	for _, task := range tasks {
		b := taskBucketById(task.Id)
		b.Mu.Lock()
		existing, existsAlready := b.Tasks[task.Id]
		if existsAlready {
			existing.Mu.Lock()
			*existing.Task = *task.Task
			existing.Mu.Unlock()
			b.Mu.Unlock()
		} else {
			ownerOfTask := task.Task.CreatedBy
			b.Tasks[task.Id] = task
			b.Mu.Unlock()

			idxBucket := taskBucketByUser(ownerOfTask)
			idxBucket.Mu.Lock()
			idxBucket.UserIndex[ownerOfTask] = append(idxBucket.UserIndex[ownerOfTask], task.Id)
			idxBucket.Mu.Unlock()
		}
	}

	if len(tasks) == 0 && username.Present {
		// NOTE(Dan): In case no tasks are found, but a user was requested, then insert a nil entry in the
		// index to notify the browse call that there are no tasks in the database.

		idxBucket := taskBucketByUser(username.Value)
		idxBucket.Mu.Lock()
		if _, ok := idxBucket.UserIndex[username.Value]; !ok {
			idxBucket.UserIndex[username.Value] = nil
		}
		idxBucket.Mu.Unlock()
	}
}

func taskNotify(task fndapi.Task) {
	username := task.CreatedBy
	b := taskBucketByUser(username)

	var subscriptions []*taskSubscription

	b.Mu.RLock()
	subs, ok := b.UserSubscriptions[username]
	if ok {
		for _, sub := range subs {
			subscriptions = append(subscriptions, sub)
		}
	}
	b.Mu.RUnlock()

	for _, sub := range subscriptions {
		select {
		case <-sub.Ctx.Done():
		case sub.Channel <- task:
		case <-time.After(30 * time.Millisecond):
		}
	}
}

// Public API
// =====================================================================================================================

func TaskCreate(actor rpc.Actor, task fndapi.TasksCreateRequest) (fndapi.Task, *util.HttpError) {
	providerId, ok := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	if actor.Role != rpc.RoleProvider || !ok || providerId == "" {
		return fndapi.Task{}, util.HttpErr(http.StatusForbidden, "forbidden")
	}

	var err *util.HttpError
	util.ValidateStringIfPresent(&task.Title, "title", util.StringValidationAllowEmpty, &err)
	util.ValidateStringIfPresent(&task.Body, "body", util.StringValidationAllowEmpty, &err)
	util.ValidateStringIfPresent(&task.Progress, "progress", util.StringValidationAllowEmpty, &err)
	util.ValidateStringIfPresent(&task.Icon, "icon", util.StringValidationAllowEmpty, &err)
	util.ValidateString(&task.User, "user", 0, &err)
	if err != nil {
		return fndapi.Task{}, err
	}

	if _, ok := rpc.LookupActor(task.User); !ok {
		return fndapi.Task{}, util.HttpErr(http.StatusNotFound, "unknown user specified")
	}

	now := time.Now()
	newId := taskId(taskGlobals.IdAcc.Add(1))
	b := taskBucketById(newId)
	b.Mu.Lock()
	iTask := &internalTask{
		Id: newId,
		Task: &fndapi.Task{
			Id:         int(newId),
			CreatedAt:  fndapi.Timestamp(now),
			ModifiedAt: fndapi.Timestamp(now),
			CreatedBy:  task.User,
			Provider:   providerId,
			Status: fndapi.TaskStatus{
				State:    fndapi.TaskStateInQueue,
				Title:    task.Title,
				Body:     task.Body,
				Progress: task.Progress,
			},
			Specification: fndapi.TaskSpecification{
				CanPause:  task.CanPause,
				CanCancel: task.CanCancel,
			},
			Icon: task.Icon,
		},
		Dirty: false, // Task is written directly to DB immediately after this
	}
	result := *iTask.Task
	b.Tasks[newId] = iTask
	b.Mu.Unlock()

	idxBucket := taskBucketByUser(task.User)
	idxBucket.Mu.Lock()
	idxBucket.UserIndex[task.User] = append(idxBucket.UserIndex[task.User], newId)
	idxBucket.Mu.Unlock()

	taskStatusUpdates.WithLabelValues(providerId, string(fndapi.TaskStateInQueue)).Inc()
	taskActiveByState.WithLabelValues(providerId, string(fndapi.TaskStateInQueue)).Inc()

	// NOTE(Dan): Commit new tasks directly to the DB and do not wait for the periodic update. This is required such
	// that the orchestration layer can always read the origin provider from the database (see coreutil). The
	// requirement is that the task is written to the database before this function returns.
	if !taskGlobals.TestingEnabled {
		db.NewTx0(func(tx *db.Transaction) {
			b := db.BatchNew(tx)
			iTask.Mu.Lock()
			lTaskPersist(b, iTask)
			iTask.Mu.Unlock()
			db.BatchSend(b)
		})
	}

	taskNotify(result)

	return result, nil
}

func TaskPostStatus(actor rpc.Actor, update fndapi.TasksPostStatusRequestUpdate) *util.HttpError {
	// NOTE(Dan): Failures are logged in this function in an attempt to troubleshoot why the providers would want to
	// do this.
	providerId, ok1 := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	itask, ok2 := taskRetrieve(taskId(update.Id), true)
	if !ok1 || !ok2 {
		log.Info("%s wants to update an unknown task: %v", providerId, update.Id)
		return util.HttpErr(http.StatusNotFound, "unknown task requested")
	}

	oldState := fndapi.TaskStateInQueue
	newState := update.NewStatus.State

	itask.Mu.RLock()
	ok := itask.Task.Provider == providerId
	itask.Mu.RUnlock()

	if !ok {
		log.Info("%s wants to update a task that does not belongs to it. %v belongs to %v", providerId,
			update.Id, itask.Task.Provider)
		return util.HttpErr(http.StatusNotFound, "unknown task requested")
	}

	itask.Mu.Lock()
	oldState = itask.Task.Status.State
	itask.Task.ModifiedAt = fndapi.Timestamp(time.Now())
	itask.Task.Status = update.NewStatus
	itask.Dirty = true

	resultTask := *itask.Task
	itask.Mu.Unlock()

	taskStatusUpdates.WithLabelValues(providerId, string(newState)).Inc()
	if oldState != newState {
		taskActiveByState.WithLabelValues(providerId, string(oldState)).Dec()
		if newState != fndapi.TaskStateSuccess && newState != fndapi.TaskStateFailure {
			taskActiveByState.WithLabelValues(providerId, string(newState)).Inc()
		}
	}

	taskNotify(resultTask)

	return nil
}

func TaskMarkAsComplete(actor rpc.Actor, id int) *util.HttpError {
	providerId, ok1 := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
	itask, ok2 := taskRetrieve(taskId(id), true)
	if !ok1 || !ok2 {
		return util.HttpErr(http.StatusNotFound, "unknown task requested")
	}

	oldState := fndapi.TaskStateInQueue
	newState := fndapi.TaskStateSuccess

	itask.Mu.RLock()
	ok := itask.Task.Provider == providerId
	itask.Mu.RUnlock()

	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown task requested")
	}

	itask.Mu.Lock()
	oldState = itask.Task.Status.State
	itask.Task.ModifiedAt = fndapi.Timestamp(time.Now())
	itask.Task.Status.State = fndapi.TaskStateSuccess
	itask.Dirty = true
	resultTask := *itask.Task
	itask.Mu.Unlock()

	taskStatusUpdates.WithLabelValues(providerId, string(newState)).Inc()
	if oldState != newState {
		taskActiveByState.WithLabelValues(providerId, string(oldState)).Dec()
	}

	taskNotify(resultTask)

	return nil
}

func TaskRetrieve(actor rpc.Actor, id int) (fndapi.Task, bool) {
	itask, ok := taskRetrieve(taskId(id), true)
	if !ok {
		return fndapi.Task{}, false
	}

	// NOTE(Dan): Retrieving a task by ID causes the isActive check to be bypassed.
	itask.Mu.RLock()
	result := *itask.Task
	itask.Mu.RUnlock()

	if result.CreatedBy == actor.Username {
		return result, true
	} else {
		return fndapi.Task{}, false
	}
}

func TaskBrowse(actor rpc.Actor, itemsPerPage int, next util.Option[string]) fndapi.PageV2[fndapi.Task] {
	itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)

	b := taskBucketByUser(actor.Username)

	b.Mu.RLock()
	var resultIdx []taskId
	{
		idx, ok := b.UserIndex[actor.Username]
		if !ok {
			b.Mu.RUnlock()
			taskLoad(util.OptNone[taskId](), util.OptValue(actor.Username))

			b.Mu.RLock()
			idx, ok = b.UserIndex[actor.Username]
		}
		resultIdx = make([]taskId, len(idx))
		copy(resultIdx, idx)
	}
	b.Mu.RUnlock()

	if next.Present {
		nextIdx, err := strconv.ParseInt(next.Value, 10, 64)
		if err == nil {
			slot, _ := slices.BinarySearch(resultIdx, taskId(nextIdx))
			resultIdx = resultIdx[:slot]
		}
	}

	slices.Reverse(resultIdx)

	var result fndapi.PageV2[fndapi.Task]
	result.ItemsPerPage = itemsPerPage

	for i, id := range resultIdx {
		itask, ok := taskRetrieve(id, false)
		if ok {
			itask.Mu.RLock()
			if lTaskIsActive(itask) {
				result.Items = append(result.Items, *itask.Task)
			}
			itask.Mu.RUnlock()

			if len(result.Items) >= itemsPerPage && len(resultIdx)-1 > i {
				result.Next.Set(fmt.Sprint(id))
				break
			}
		}
	}

	return result
}

func TaskSubscribe(actor rpc.Actor, ctx context.Context) <-chan fndapi.Task {
	sub := &taskSubscription{
		SessionId: util.RandomTokenNoTs(32),
		Ctx:       ctx,
		Channel:   make(chan fndapi.Task, 128),
	}

	{
		b := taskBucketByUser(actor.Username)
		b.Mu.Lock()
		subs, ok := b.UserSubscriptions[actor.Username]
		if !ok {
			b.UserSubscriptions[actor.Username] = map[string]*taskSubscription{}
			subs, ok = b.UserSubscriptions[actor.Username]
		}
		subs[sub.SessionId] = sub
		b.Mu.Unlock()
	}

	go func() {
		<-ctx.Done()

		b := taskBucketByUser(actor.Username)
		b.Mu.Lock()
		subs, ok := b.UserSubscriptions[actor.Username]
		if ok {
			delete(subs, sub.SessionId)
		}
		b.Mu.Unlock()
	}()

	return sub.Channel
}

// Metrics
// =====================================================================================================================

var (
	taskStatusUpdates = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "tasks",
		Name:      "status_updates_total",
		Help:      "Counts total task status updates by provider and new state.",
	}, []string{"provider", "state"})

	taskActiveByState = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud",
		Subsystem: "tasks",
		Name:      "state_count",
		Help:      "Tracks the number of tasks by provider and state.",
	}, []string{"provider", "state"})

	taskPersistCycleDuration = promauto.NewSummary(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "tasks",
		Name:      "persist_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to complete a persistence cycle",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	})

	taskPersistDirtyTasks = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "tasks",
		Name:      "persist_dirty_tasks_total",
		Help:      "Tracks the number of tasks persisted in total.",
	})
)
