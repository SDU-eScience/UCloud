package containers

import (
	"fmt"
	"k8s.io/client-go/tools/portforward"
	"k8s.io/client-go/transport/spdy"
	"net/http"
	"sync"
	"sync/atomic"
	"ucloud.dk/pkg/log"
)

const tunnelPortOffset = 30_000

var tunnelPortAllocator = atomic.Int32{}
var tunnels = map[string]int{}
var tunnelMutex = sync.Mutex{}

// establishTunnel will port-forward to the pod identified by podName on a given port. A new local port is returned
// which can be used instead. The function is goroutine safe. The port is not guaranteed to be ready after the function
// returns.
//
// NOTE(Dan): This is not supposed to be used in production. It will leak memory from old tunnels. It will also
// eventually run out of ports.
func establishTunnel(podName string, port int) int {
	key := fmt.Sprintf("%v:%v", podName, port)
	tunnelMutex.Lock()
	myPort, ok := tunnels[key]
	if !ok {
		myPort = int(tunnelPortOffset + tunnelPortAllocator.Add(1))
		tunnels[key] = myPort
	}
	tunnelMutex.Unlock()

	if ok {
		return myPort
	} else {
		request := K8sClient.CoreV1().RESTClient().
			Post().
			Resource("pods").
			Namespace(Namespace).
			Name(podName).
			SubResource("portforward")

		transport, upgrader, err := spdy.RoundTripperFor(K8sConfig)
		if err != nil {
			log.Warn("Failed to establish tunnel to %v:%v %s", podName, port, err)
			return myPort
		}

		stopChan := make(chan struct{}, 1)
		readyChan := make(chan struct{})

		fw, err := portforward.New(
			spdy.NewDialer(upgrader, &http.Client{Transport: transport}, "POST", request.URL()),
			[]string{fmt.Sprintf("%d:%d", myPort, port)},
			stopChan,
			readyChan,
			nil,
			nil,
		)

		if err != nil {
			log.Warn("Failed to establish tunnel to %v:%v %s", podName, port, err)
			return myPort
		}

		go func() {
			err := fw.ForwardPorts()
			if err != nil {
				log.Warn("Failed to establish tunnel to %v:%v %s", podName, port, err)
			}
		}()

		<-readyChan
		return myPort
	}
}
