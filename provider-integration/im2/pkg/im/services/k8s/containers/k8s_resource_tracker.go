package containers

import (
	"context"
	"fmt"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/watch"
	"sync"
	"time"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type K8sResourceTracker[R runtime.Object] struct {
	watcher   func(ctx context.Context, options metav1.ListOptions) (watch.Interface, error)
	keyer     func(resource R) string
	mu        sync.RWMutex
	resources map[string]R
}

func (r *K8sResourceTracker[R]) Start() {
	go func() {
		for util.IsAlive {
			r.loop()
			time.Sleep(300 * time.Millisecond)
		}
	}()
}

func (r *K8sResourceTracker[R]) loop() {
	didUnlock := false           // track if bookmark has been read and the mutex has been unlocked
	r.mu.Lock()                  // lock until bookmark has been read
	r.resources = map[string]R{} // reset map after connection lost

	watcher, err := r.watcher(context.Background(), metav1.ListOptions{
		Watch:             true,
		SendInitialEvents: util.Pointer(true),
	})

	doLock := func() {
		if didUnlock {
			r.mu.Lock()
		}
	}
	doUnlock := func() {
		if didUnlock {
			r.mu.Unlock()
		}
	}

	if err != nil {
		log.Warn("Resource tracker failed unexpectedly: %s", err.Error())
		return
	}

	c := watcher.ResultChan()
	defer func() {
		watcher.Stop()
		if !didUnlock {
			r.mu.Unlock()
		}
	}()

	for {
		event, ok := <-c
		if !ok {
			log.Warn("Resource tracked has ended unexpectedly!")
			return
		}

		switch event.Type {
		case watch.Added, watch.Modified:
			resource, ok := event.Object.(R)
			if !ok {
				panic(fmt.Sprintf("Invaild resource type: %#v is not the expected resource type", event.Object))
			}

			key := r.keyer(resource)
			doLock()
			r.resources[key] = resource
			doUnlock()

		case watch.Deleted:
			resource, ok := event.Object.(R)
			if !ok {
				panic(fmt.Sprintf("Invaild resource type: %#v is not the expected resource type", event.Object))
			}

			key := r.keyer(resource)
			doLock()
			r.resources[key] = resource
			doUnlock()

		case watch.Bookmark:
			r.mu.Unlock()
			didUnlock = true

		case watch.Error:
			log.Warn("Resource tracker has ended unexpectedly!")
			return
		}
	}
}
