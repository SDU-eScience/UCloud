package shared

import (
	"context"
	"fmt"
	"time"

	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/client-go/dynamic"
	"k8s.io/client-go/dynamic/dynamicinformer"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/tools/cache"
	"ucloud.dk/shared/pkg/log"
)

const k8sResourceIndex = "primary"

type K8sResourceTracker[R runtime.Object] struct {
	informer cache.SharedIndexInformer
}

func NewResourceTracker[R runtime.Object](
	namespace string,
	informer func(factory informers.SharedInformerFactory) cache.SharedIndexInformer,
	keyer func(resource R) string,
	options ...informers.SharedInformerOption,
) *K8sResourceTracker[R] {
	opts := []informers.SharedInformerOption{}
	if namespace != "" {
		opts = append(opts, informers.WithNamespace(namespace))
	}
	opts = append(opts, options...)

	factory := informers.NewSharedInformerFactoryWithOptions(K8sClient, 0, opts...)

	return newResourceTrackerFromInformer(informer(factory), keyer)
}

func NewDynamicResourceTracker(
	client dynamic.Interface,
	gvr schema.GroupVersionResource,
	namespace string,
) *K8sResourceTracker[*unstructured.Unstructured] {
	if namespace != "" {
		_, err := client.Resource(gvr).Namespace(namespace).List(
			context.Background(),
			k8smeta.ListOptions{Limit: 1},
		)
		if err == nil {
			factory := dynamicinformer.NewFilteredDynamicSharedInformerFactory(client, 0, namespace, nil)
			return newResourceTrackerFromInformer(
				factory.ForResource(gvr).Informer(),
				func(resource *unstructured.Unstructured) string {
					return resource.GetName()
				},
			)
		}
	}

	_, err := client.Resource(gvr).List(
		context.Background(),
		k8smeta.ListOptions{Limit: 1},
	)
	if err != nil {
		log.Fatal("Could not watch %s: %s", gvr.String(), err)
	}

	factory := dynamicinformer.NewFilteredDynamicSharedInformerFactory(client, 0, "", nil)
	return newResourceTrackerFromInformer(
		factory.ForResource(gvr).Informer(),
		func(resource *unstructured.Unstructured) string {
			return resource.GetName()
		},
	)
}

func newResourceTrackerFromInformer[R runtime.Object](
	informer cache.SharedIndexInformer,
	keyer func(resource R) string,
) *K8sResourceTracker[R] {
	r := &K8sResourceTracker[R]{
		informer: informer,
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

	synced := make(chan bool, 1)
	go func() {
		cache.WaitForCacheSync(stopCh, r.informer.HasSynced)
		select {
		case synced <- true:
		default:
		}
	}()

	select {
	case <-synced:
	case <-time.After(2 * time.Minute):
		log.Fatal("Resource tracker: cache sync timed out")
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
