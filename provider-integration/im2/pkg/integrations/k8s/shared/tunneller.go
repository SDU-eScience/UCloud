package shared

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"sync"
	"sync/atomic"

	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/portforward"
	"k8s.io/client-go/transport/spdy"
	"ucloud.dk/shared/pkg/log"
)

const tunnelPortOffset = 30_000

var tunnelPortAllocator = atomic.Int32{}
var tunnels = map[string]int{}
var tunnelMutex = sync.Mutex{}

// EstablishTunnel will port-forward to the target identified by name on a given port. A new local port is returned
// which can be used instead. The function is goroutine safe. The port is not guaranteed to be ready after the function
// returns. The target can be either a Pod or a KubeVirt VirtualMachineInstance.
//
// NOTE(Dan): This is not supposed to be used in production. It will leak memory from old tunnels. It will also
// eventually run out of ports.
func EstablishTunnel(name string, port int) int {
	key := fmt.Sprintf("%v:%v", name, port)
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
		request, targetType, err := resolvePortForwardRequest(name)
		if err != nil {
			log.Warn("Failed to establish tunnel to %v:%v %s", name, port, err)
			return myPort
		}

		if targetType == "virtualmachine" {
			err = establishVirtualMachineTunnel(name, port, myPort)
			if err != nil {
				log.Warn("Failed to establish tunnel to %v(%v):%v %s", targetType, name, port, err)
			}
			return myPort
		}

		transport, upgrader, err := spdy.RoundTripperFor(K8sConfig)
		if err != nil {
			log.Warn("Failed to establish tunnel to %v(%v):%v %s", targetType, name, port, err)
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
			log.Warn("Failed to establish tunnel to %v(%v):%v %s", targetType, name, port, err)
			return myPort
		}

		errChan := make(chan error, 1)

		go func() {
			errChan <- fw.ForwardPorts()
		}()

		select {
		case <-readyChan:
		case err := <-errChan:
			if err != nil {
				log.Warn("Failed to establish tunnel to %v(%v):%v %s", targetType, name, port, err)
			}
		}

		return myPort
	}
}

func establishVirtualMachineTunnel(name string, port int, localPort int) error {
	listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", localPort))
	if err != nil {
		return err
	}

	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				log.Warn("Failed accepting connection for VM tunnel %v:%v %s", name, port, err)
				return
			}

			go func(localConn net.Conn) {
				stream, err := KubevirtClient.VirtualMachine(ServiceConfig.Compute.Namespace).PortForward(name, port, "")
				if err != nil {
					_ = localConn.Close()
					log.Warn("Failed to open VM portforward stream %v:%v %s", name, port, err)
					return
				}

				remoteConn := stream.AsConn()
				errChan := make(chan error, 2)

				go func() {
					_, copyErr := io.Copy(remoteConn, localConn)
					errChan <- copyErr
				}()

				go func() {
					_, copyErr := io.Copy(localConn, remoteConn)
					errChan <- copyErr
				}()

				<-errChan
				_ = localConn.Close()
				_ = remoteConn.Close()
			}(conn)
		}
	}()

	return nil
}

func resolvePortForwardRequest(name string) (request *rest.Request, targetType string, err error) {
	ns := ServiceConfig.Compute.Namespace

	_, err = K8sClient.CoreV1().Pods(ns).Get(context.Background(), name, metav1.GetOptions{})
	if err == nil {
		return K8sClient.CoreV1().RESTClient().
			Post().
			Resource("pods").
			Namespace(ns).
			Name(name).
			SubResource("portforward"), "pod", nil
	}

	if !k8serrors.IsNotFound(err) {
		return nil, "", err
	}

	if KubevirtClient == nil {
		return nil, "", fmt.Errorf("pod %q was not found and kubevirt client is unavailable", name)
	}

	_, err = KubevirtClient.VirtualMachine(ns).Get(context.Background(), name, metav1.GetOptions{})
	if err == nil {
		return nil, "virtualmachine", nil
	}

	if !k8serrors.IsNotFound(err) {
		return nil, "", err
	}

	return nil, "", fmt.Errorf("neither pod nor virtualmachine %q was found", name)
}
