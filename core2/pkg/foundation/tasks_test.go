package foundation

import (
	"context"
	"fmt"
	"runtime"
	"testing"
	"time"

	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initTaskTest(t *testing.T) {
	t.Helper()

	taskGlobals.TestingEnabled = true
	taskGlobals.Buckets = nil
	taskGlobals.IdAcc.Store(0)

	for i := 0; i < runtime.NumCPU(); i++ {
		taskGlobals.Buckets = append(taskGlobals.Buckets, &taskBucket{
			UserIndex:         map[string][]taskId{},
			UserSubscriptions: map[string]map[string]*taskSubscription{},
			Tasks:             map[taskId]*internalTask{},
		})
	}

	taskTestUsers = map[string]rpc.Actor{}
	rpc.LookupActor = func(username string) (rpc.Actor, bool) {
		result, ok := taskTestUsers[username]
		return result, ok
	}
}

var taskTestUsers map[string]rpc.Actor

func taskTestProvider(providerId string) rpc.Actor {
	result := rpc.Actor{
		Username: fndapi.ProviderSubjectPrefix + providerId,
		Role:     rpc.RoleProvider,
	}

	taskTestUsers[result.Username] = result
	return result
}

func taskTestUser(username string) rpc.Actor {
	result := rpc.Actor{
		Username: username,
		Role:     rpc.RoleUser,
	}

	taskTestUsers[result.Username] = result
	return result
}

func mustCreateTask(t *testing.T, providerId, username string, opts ...func(*fndapi.TasksCreateRequest)) fndapi.Task {
	t.Helper()
	prov := taskTestProvider(providerId)
	taskTestUser(username)
	req := fndapi.TasksCreateRequest{
		User:      username,
		CanPause:  true,
		CanCancel: true,
		Title:     util.OptValue("T"),
		Body:      util.OptValue("B"),
		Progress:  util.OptValue("starting"),
		Icon:      util.OptNone[string](),
	}
	for _, opt := range opts {
		opt(&req)
	}
	task, err := TaskCreate(prov, req)
	if err != nil {
		t.Fatalf("TaskCreate error: %+v", err)
	}
	return task
}

func recvOrTimeout[T any](t *testing.T, ch <-chan T, d time.Duration) (T, bool) {
	t.Helper()
	select {
	case v := <-ch:
		return v, true
	case <-time.After(d):
		var zero T
		return zero, false
	}
}

func TestTaskCreateAndRetrieveAuthz(t *testing.T) {
	initTaskTest(t)

	task := mustCreateTask(t, "p1", "alice")

	got, ok := TaskRetrieve(taskTestUser("alice"), task.Id)
	if !ok {
		t.Fatalf("alice should retrieve her task")
	}
	if got.Id != task.Id || got.CreatedBy != "alice" || got.Provider != "p1" {
		t.Fatalf("unexpected task fields: %+v", got)
	}

	_, ok = TaskRetrieve(taskTestUser("bob"), task.Id)
	if ok {
		t.Fatalf("bob must NOT retrieve alice's task")
	}
}

func TestTaskCreateForbiddenForNonProvider(t *testing.T) {
	initTaskTest(t)

	_, err := TaskCreate(taskTestUser("alice"), fndapi.TasksCreateRequest{
		User: "alice",
	})
	if err == nil {
		t.Fatalf("expected forbidden error for non-provider")
	}
}

func TestTaskPostStatusAndNotify(t *testing.T) {
	initTaskTest(t)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ch := TaskSubscribe(taskTestUser("alice"), ctx)

	task := mustCreateTask(t, "p1", "alice")

	if _, ok := recvOrTimeout(t, ch, 200*time.Millisecond); !ok {
		t.Fatalf("expected creation notification")
	}

	newStatus := fndapi.TaskStatus{
		State:              fndapi.TaskStateRunning,
		Title:              util.OptValue("Working"),
		Body:               util.OptValue("Halfway"),
		Progress:           util.OptValue("50%"),
		ProgressPercentage: util.OptValue(50.0),
	}
	if err := TaskPostStatus(taskTestProvider("p1"), fndapi.TasksPostStatusRequestUpdate{
		Id:        task.Id,
		NewStatus: newStatus,
	}); err != nil {
		t.Fatalf("TaskPostStatus error: %+v", err)
	}

	got, ok := TaskRetrieve(taskTestUser("alice"), task.Id)
	if !ok {
		t.Fatalf("alice should retrieve updated task")
	}
	if got.Status.State != fndapi.TaskStateRunning ||
		got.Status.Title.GetOrDefault("") != "Working" ||
		got.Status.ProgressPercentage.GetOrDefault(0) != 50.0 {
		t.Fatalf("unexpected status after update: %+v", got.Status)
	}

	msg, ok2 := recvOrTimeout(t, ch, 200*time.Millisecond)
	if !ok2 {
		t.Fatalf("expected status update notification")
	}
	if msg.Id != task.Id || msg.Status.State != fndapi.TaskStateRunning {
		t.Fatalf("unexpected notification payload: %+v", msg)
	}

	err := TaskPostStatus(taskTestProvider("p2"), fndapi.TasksPostStatusRequestUpdate{
		Id:        task.Id,
		NewStatus: newStatus,
	})
	if err == nil {
		t.Fatalf("expected not-found/forbidden when other provider posts status")
	}
}

func TestTaskMarkAsComplete(t *testing.T) {
	initTaskTest(t)

	task := mustCreateTask(t, "p1", "alice")

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ch := TaskSubscribe(taskTestUser("alice"), ctx)
	recvOrTimeout(t, ch, 200*time.Millisecond)

	if err := TaskMarkAsComplete(taskTestProvider("p1"), task.Id); err != nil {
		t.Fatalf("TaskMarkAsComplete error: %+v", err)
	}

	got, ok := TaskRetrieve(taskTestUser("alice"), task.Id)
	if !ok {
		t.Fatalf("alice should retrieve completed task")
	}
	if got.Status.State != fndapi.TaskStateSuccess {
		t.Fatalf("expected state=success, got: %v", got.Status.State)
	}

	msg, ok2 := recvOrTimeout(t, ch, 200*time.Millisecond)
	if !ok2 {
		t.Fatalf("expected completion notification")
	}
	if msg.Status.State != fndapi.TaskStateSuccess {
		t.Fatalf("unexpected notify state: %v", msg.Status.State)
	}

	if err := TaskMarkAsComplete(taskTestProvider("p2"), task.Id); err == nil {
		t.Fatalf("expected not-found/forbidden for different provider")
	}
}

func TestTaskBrowseOrderAndPagination(t *testing.T) {
	initTaskTest(t)

	var ids []int
	for i := 0; i < 5; i++ {
		tk := mustCreateTask(t, "p1", "alice", func(r *fndapi.TasksCreateRequest) {
			r.Title = util.OptValue(fmt.Sprintf("T%d", i))
		})
		ids = append(ids, tk.Id)
	}

	page1 := TaskBrowse(taskTestUser("alice"), 2, util.OptNone[string]())
	if len(page1.Items) != 2 {
		t.Fatalf("expected 2 items on page1, got %d", len(page1.Items))
	}
	if page1.Items[0].Id != ids[4] || page1.Items[1].Id != ids[3] {
		t.Fatalf("unexpected order page1: got [%d,%d], want [%d,%d]",
			page1.Items[0].Id, page1.Items[1].Id, ids[4], ids[3])
	}
	if !page1.Next.Present {
		t.Fatalf("expected Next cursor on page1")
	}

	page2 := TaskBrowse(taskTestUser("alice"), 2, util.OptValue(page1.Next.Value))
	if len(page2.Items) != 2 {
		t.Fatalf("expected 2 items on page2, got %d", len(page2.Items))
	}
	if page2.Items[0].Id != ids[2] || page2.Items[1].Id != ids[1] {
		t.Fatalf("unexpected order page2: got [%d,%d], want [%d,%d]",
			page2.Items[0].Id, page2.Items[1].Id, ids[2], ids[1])
	}

	var next util.Option[string]
	if page2.Next.Present {
		next = util.OptValue(page2.Next.Value)
	} else {
		next = util.OptNone[string]()
	}
	page3 := TaskBrowse(taskTestUser("alice"), 2, next)
	if len(page3.Items) != 1 || page3.Items[0].Id != ids[0] {
		t.Fatalf("unexpected page3: %+v", page3.Items)
	}
	if page3.Next.Present {
		t.Fatalf("did not expect a Next cursor on the last page")
	}
}

func TestTaskBrowseActivityFilter(t *testing.T) {
	initTaskTest(t)

	active := mustCreateTask(t, "p1", "alice")
	inactive := mustCreateTask(t, "p1", "alice")

	{
		b := taskBucketById(taskId(inactive.Id))
		b.Mu.Lock()
		it := b.Tasks[taskId(inactive.Id)]
		it.Mu.Lock()
		it.Task.ModifiedAt = fndapi.Timestamp(time.Now().Add(-6 * time.Minute))
		it.Mu.Unlock()
		b.Mu.Unlock()
	}

	page := TaskBrowse(taskTestUser("alice"), 10, util.OptNone[string]())
	if len(page.Items) != 1 {
		t.Fatalf("expected 1 active task, got %d", len(page.Items))
	}
	if page.Items[0].Id != active.Id {
		t.Fatalf("expected only active task id=%d, got id=%d", active.Id, page.Items[0].Id)
	}
}
