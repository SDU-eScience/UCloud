package shared

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/portforward"
	"k8s.io/client-go/transport/spdy"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const tunnelPortOffset = 30_000

var tunnelPortAllocator = atomic.Int32{}
var tunnels = map[string]int{}
var tunnelMutex = sync.Mutex{}

func EstablishTunnel(name string, port int) int {
	return EstablishTunnelEx(name, ServiceConfig.Compute.Namespace, port)
}

// EstablishTunnel will port-forward to the target identified by name on a given port. A new local port is returned
// which can be used instead. The function is goroutine safe. The port is not guaranteed to be ready after the function
// returns. The target can be either a Pod or a KubeVirt VirtualMachineInstance.
//
// NOTE(Dan): This is not supposed to be used in production. It will leak memory from old tunnels. It will also
// eventually run out of ports.
func EstablishTunnelEx(name string, namespace string, port int) int {
	key := fmt.Sprintf("%v:%v:%v", namespace, name, port)
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
		request, targetType, err := resolvePortForwardRequest(name, namespace)
		if err != nil {
			releaseTunnel(key, myPort)
			log.Warn("Failed to establish tunnel to %v:%v %s", name, port, err)
			return myPort
		}

		if targetType == "virtualmachine" {
			err = establishVirtualMachineTunnel(name, namespace, port, myPort)
			if err != nil {
				releaseTunnel(key, myPort)
				log.Warn("Failed to establish tunnel to %v(%v):%v %s", targetType, name, port, err)
			}
			return myPort
		}

		transport, upgrader, err := spdy.RoundTripperFor(K8sConfig)
		if err != nil {
			releaseTunnel(key, myPort)
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
			releaseTunnel(key, myPort)
			log.Warn("Failed to establish tunnel to %v(%v):%v %s", targetType, name, port, err)
			return myPort
		}

		errChan := make(chan error, 1)

		go func() {
			forwardErr := fw.ForwardPorts()
			releaseTunnel(key, myPort)
			errChan <- forwardErr
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

func releaseTunnel(key string, localPort int) {
	tunnelMutex.Lock()
	if tunnels[key] == localPort {
		delete(tunnels, key)
	}
	tunnelMutex.Unlock()
}

const vmTunnelVerifyInterval = 2 * time.Second
const vmTunnelReadDeadline = 2 * time.Second

type vmTunnelSupervisor struct {
	name      string
	namespace string
	port      int
	localPort int

	stateMutex sync.Mutex
	listener   net.Listener
	running    bool
	reverify   chan struct{}
}

func establishVirtualMachineTunnel(name string, namespace string, port int, localPort int) error {
	s := &vmTunnelSupervisor{
		name:      name,
		namespace: namespace,
		port:      port,
		localPort: localPort,
		reverify:  make(chan struct{}, 1),
	}

	go s.run()
	return nil
}

func (s *vmTunnelSupervisor) run() {
	s.verify()

	ticker := time.NewTicker(vmTunnelVerifyInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
		case <-s.reverify:
		}
		s.verify()
	}
}

func (s *vmTunnelSupervisor) verify() {
	healthy := s.probe()

	s.stateMutex.Lock()
	defer s.stateMutex.Unlock()

	if healthy == s.running {
		return
	}

	if healthy {
		listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", s.localPort))
		if err != nil {
			log.Warn("Failed to listen for VM tunnel %v:%v %s", s.name, s.port, err)
			return
		}

		s.listener = listener
		s.running = true
		go s.acceptLoop(listener)
	} else {
		s.running = false
		if s.listener != nil {
			_ = s.listener.Close()
			s.listener = nil
		}
	}
}

func (s *vmTunnelSupervisor) probe() bool {
	stream, err := KubevirtClient.VirtualMachine(s.namespace).PortForward(s.name, s.port, "")
	if err != nil {
		return false
	}

	conn := stream.AsConn()
	defer util.SilentClose(conn)

	_ = conn.SetReadDeadline(time.Now().Add(vmTunnelReadDeadline))
	buffer := make([]byte, 1)
	_, readErr := conn.Read(buffer)
	if readErr == nil {
		return true
	}
	if netErr, ok := readErr.(net.Error); ok && netErr.Timeout() {
		return true
	}
	return false
}

func (s *vmTunnelSupervisor) triggerReverify() {
	select {
	case s.reverify <- struct{}{}:
	default:
	}
}

func (s *vmTunnelSupervisor) acceptLoop(listener net.Listener) {
	for {
		conn, err := listener.Accept()
		if err != nil {
			return
		}

		go s.handle(conn)
	}
}

func (s *vmTunnelSupervisor) handle(localConn net.Conn) {
	stream, err := KubevirtClient.VirtualMachine(s.namespace).PortForward(s.name, s.port, "")
	if err != nil {
		_ = localConn.Close()
		log.Warn("Failed to open VM portforward stream %v:%v %s", s.name, s.port, err)
		s.triggerReverify()
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
}

func resolvePortForwardRequest(name string, ns string) (request *rest.Request, targetType string, err error) {
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
