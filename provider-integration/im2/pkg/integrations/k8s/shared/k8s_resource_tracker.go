package shared

import (
	"fmt"

	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/tools/cache"
)

const k8sResourceIndex = "primary"

type K8sResourceTracker[R runtime.Object] struct {
	informer cache.SharedIndexInformer
}

func NewResourceTracker[R runtime.Object](
	namespace string,
	informer func(factory informers.SharedInformerFactory) cache.SharedIndexInformer,
	keyer func(resource R) string,
) *K8sResourceTracker[R] {
	var factory informers.SharedInformerFactory
	if namespace == "" {
		factory = informers.NewSharedInformerFactoryWithOptions(K8sClient, 0)
	} else {
		factory = informers.NewSharedInformerFactoryWithOptions(K8sClient, 0, informers.WithNamespace(namespace))
	}

	r := &K8sResourceTracker[R]{
		informer: informer(factory),
	}

	_ = r.informer.AddIndexers(cache.Indexers{
		k8sResourceIndex: func(obj interface{}) ([]string, error) {
			resc, ok := obj.(R)
			if ok {
				return []string{keyer(resc)}, nil
			} else {
				return nil, nil
			}
		},
	})

	r.start()
	return r
}

func (r *K8sResourceTracker[R]) start() {
	stopCh := make(chan struct{})
	go r.informer.Run(stopCh)

	if ok := cache.WaitForCacheSync(stopCh, r.informer.HasSynced); !ok {
		panic("resource tracker: cache sync failed")
	}
}

// List returns a snapshot of the current objects.
func (r *K8sResourceTracker[R]) List() []R {
	var result []R
	snapshot := r.informer.GetStore().List()
	for _, obj := range snapshot {
		res, ok := obj.(R)
		if !ok {
			panic(fmt.Sprintf("invalid resource type in seed: %#v", obj))
		}
		result = append(result, res)
	}

	return result
}

func (r *K8sResourceTracker[R]) Retrieve(key string) (R, bool) {
	result, err := r.informer.GetIndexer().ByIndex(k8sResourceIndex, key)
	if err != nil || len(result) == 0 {
		var zero R
		return zero, false
	} else {
		res, ok := result[0].(R)
		if ok {
			return res, true
		} else {
			var zero R
			return zero, false
		}
	}
}
