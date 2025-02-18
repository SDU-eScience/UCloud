package kubevirt

import (
	"context"
	"encoding/json"
	"fmt"
	ws "github.com/gorilla/websocket"
	"io"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	kvcore "kubevirt.io/api/core/v1"
	kvclient "kubevirt.io/client-go/kubecli"
	kvapi "kubevirt.io/client-go/kubevirt/typed/core/v1"
	kvcdi "kubevirt.io/containerized-data-importer-api/pkg/apis/core/v1beta1"
	"strconv"
	"strings"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes
var KubevirtClient kvclient.KubevirtClient
var Namespace string

func Init() ctrl.JobsService {
	// Create a number of aliases for use in this package. These are all static by the time this function is called.
	_ = shared.K8sClient
	ServiceConfig = shared.ServiceConfig
	KubevirtClient = shared.KubevirtClient

	Namespace = ServiceConfig.Compute.Namespace

	return ctrl.JobsService{
		Terminate:                terminate,
		Extend:                   nil,
		RetrieveProducts:         nil, // handled by main instance
		Follow:                   follow,
		HandleShell:              handleShell,
		ServerFindIngress:        nil,
		OpenWebSession:           openWebSession,
		RequestDynamicParameters: requestDynamicParameters,
		Suspend:                  suspend,
		Unsuspend:                unsuspend,
		HandleBuiltInVnc:         handleVnc,
	}
}

func vmName(jobId string, rank int) string {
	return fmt.Sprintf("vm-%v-%v", jobId, rank)
}

func vmNameToJobIdAndRank(name string) (jobId string, rank int, ok bool) {
	if !strings.HasPrefix(name, "vm-") {
		return "", 0, false
	}

	toks := strings.Split(name, "-")
	if len(toks) != 3 {
		return "", 0, false
	}

	parsedRank, err := strconv.ParseInt(toks[2], 10, 64)
	if err != nil {
		return "", 0, false
	}

	return toks[1], int(parsedRank), true
}

type cloudInit struct {
	User     string   `json:"user"`
	Password string   `json:"password"`
	Bootcmd  []string `json:"bootcmd"`
	Chpasswd struct {
		Expire bool `json:"expire"`
	} `json:"chpasswd"`
}

func follow(session *ctrl.FollowJobSession) {
	// Keep alive to make the Core happy.
	for *session.Alive {
		time.Sleep(100 * time.Millisecond)
	}
}

func handleShell(session *ctrl.ShellSession, cols int, rows int) {
	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()

	startChannel := make(chan error)

	streamStop := make(chan error)
	writeStop := make(chan error)
	readStop := make(chan error)

	go func() {
		stream, err := KubevirtClient.
			VirtualMachineInstance(Namespace).
			SerialConsole(
				vmName(session.Job.Id, session.Rank),
				&kvapi.SerialConsoleOptions{},
			)

		startChannel <- err

		if err != nil {
			return
		}

		streamStop <- stream.Stream(kvapi.StreamOptions{
			In:  stdinReader,
			Out: stdoutWriter,
		})
	}()

	err := <-startChannel
	if err != nil {
		log.Info("Failed to open serial console to VM '%v': %v", session.Job.Id, err)
		return
	}

	go func() {
		defer close(readStop)
		buf := make([]byte, 1024*4)

		for session.Alive {
			n, err := stdoutReader.Read(buf)
			if err != nil {
				break
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		defer close(writeStop)

		for util.IsAlive && session.Alive {
			select {
			case event := <-session.InputEvents:
				switch event.Type {
				case ctrl.ShellEventTypeInput:
					_, err = stdinWriter.Write([]byte(event.Data))
					if err != nil {
						session.Alive = false
						log.Info("Error while writing to master: %v", err)
						break
					}

				case ctrl.ShellEventTypeResize:
					// Do nothing
				}

			case _ = <-time.After(1 * time.Second):
				continue
			}
		}
	}()

	select {
	case <-streamStop:
	case <-writeStop:
	case <-readStop:
	}
}

func terminate(request ctrl.JobTerminateRequest) error {
	name := vmName(request.Job.Id, 0)
	err := KubevirtClient.VirtualMachine(Namespace).Delete(context.TODO(), name, metav1.DeleteOptions{})
	if err != nil {
		log.Info("Failed to delete VM: %v", err)
		return util.ServerHttpError("Failed to delete VM")
	}
	return nil
}

func unsuspend(request ctrl.JobUnsuspendRequest) error {
	shared.RequestSchedule(request.Job)
	return nil
}

func suspend(request ctrl.JobSuspendRequest) error {
	name := vmName(request.Job.Id, 0)
	err := KubevirtClient.VirtualMachine(Namespace).Stop(context.TODO(), name, &kvcore.StopOptions{})
	if err != nil {
		log.Info("Failed to shutdown VM: %v", err)
		return util.ServerHttpError("Failed to shutdown VM")
	}
	return nil
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	return nil
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (ctrl.ConfiguredWebSession, error) {
	return ctrl.ConfiguredWebSession{
		Flags: ctrl.RegisteredIngressFlagsVnc | ctrl.RegisteredIngressFlagsNoGatewayConfig,
	}, nil
}

func handleVnc(job *orc.Job, rank int, conn *ws.Conn) {
	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()

	startChannel := make(chan error)

	streamStop := make(chan error)
	writeStop := make(chan error)
	readStop := make(chan error)

	go func() {
		stream, err := KubevirtClient.
			VirtualMachineInstance(Namespace).
			VNC(vmName(job.Id, rank))

		startChannel <- err

		if err != nil {
			return
		}

		streamStop <- stream.Stream(kvapi.StreamOptions{
			In:  stdinReader,
			Out: stdoutWriter,
		})
	}()

	startErr := <-startChannel
	if startErr != nil {
		log.Info("Failed to open VNC connection to VM '%v': %v", job.Id, startErr)
		return
	}

	go func() {
		defer close(readStop)
		buf := make([]byte, 1024*4)

		for util.IsAlive {
			n, err := stdoutReader.Read(buf)
			if err != nil {
				break
			}

			err = conn.WriteMessage(ws.BinaryMessage, buf[:n])
			if err != nil {
				log.Info("Failed to read: %v", err)
				break
			}
		}
	}()

	go func() {
		defer close(writeStop)

		for util.IsAlive {
			mType, data, err := conn.ReadMessage()
			if err != nil {
				break
			}

			if mType == ws.BinaryMessage {
				_, err := stdinWriter.Write(data)
				if err != nil {
					log.Info("Failed to write: %v", err)
					break
				}
			}
		}
	}()

	select {
	case <-streamStop:
	case <-writeStop:
	case <-readStop:
	}
}

func StartScheduledJob(job *orc.Job, rank int, node string) {
	cinit := cloudInit{}
	cinit.User = "ucloud"
	cinit.Password = "ucloud"
	cinit.Chpasswd.Expire = false
	cinit.Bootcmd = []string{}

	cinitRawData, _ := json.Marshal(cinit)
	cinitData := "#cloud-config\n" + string(cinitRawData)
	_ = cinitData

	machine := &job.Status.ResolvedProduct

	nameOfVm := vmName(job.Id, rank)
	existingVm, err := KubevirtClient.VirtualMachine(Namespace).Get(context.TODO(), nameOfVm, metav1.GetOptions{})

	vm := &kvcore.VirtualMachine{}
	if err != nil {
		vm.Name = vmName(job.Id, rank)
		vm.Namespace = Namespace
	} else {
		vm = existingVm
	}

	strategy := kvcore.RunStrategyAlways
	vm.Spec.RunStrategy = &strategy

	image := job.Status.ResolvedApplication.Invocation.Tool.Tool.Description.Image
	baseImageSource := &kvcdi.DataVolumeSource{}
	if strings.HasPrefix(image, "http://") || strings.HasPrefix(image, "https://") {
		baseImageSource.HTTP = &kvcdi.DataVolumeSourceHTTP{
			URL: image,
		}
	} else {
		// TODO
		baseImageSource.PVC = &kvcdi.DataVolumeSourcePVC{
			Namespace: Namespace,
			Name:      image,
		}
	}

	vm.Spec.DataVolumeTemplates = []kvcore.DataVolumeTemplateSpec{
		{
			ObjectMeta: metav1.ObjectMeta{
				Name: vm.Name,
			},
			Spec: kvcdi.DataVolumeSpec{
				Source: baseImageSource,
				Storage: &kvcdi.StorageSpec{
					AccessModes: []corev1.PersistentVolumeAccessMode{corev1.ReadWriteOnce},
					Resources: corev1.ResourceRequirements{
						Requests: corev1.ResourceList{
							corev1.ResourceStorage: *resource.NewScaledQuantity(5, resource.Giga),
						},
					},
					StorageClassName: shared.ServiceConfig.Compute.VirtualMachineStorageClass.GetPtrOrNil(),
				},
			},
		},
	}

	vm.Spec.Template = &kvcore.VirtualMachineInstanceTemplateSpec{
		Spec: kvcore.VirtualMachineInstanceSpec{
			DNSPolicy: corev1.DNSNone,
			DNSConfig: &corev1.PodDNSConfig{
				Nameservers: []string{
					"1.1.1.1",
				},
			},
			NodeSelector: map[string]string{
				"kubernetes.io/hostname": node,
			},
			Domain: kvcore.DomainSpec{
				CPU: &kvcore.CPU{
					Cores:   max(1, uint32(machine.Cpu/2)),
					Threads: max(1, uint32(machine.Cpu)),
				},
				Memory: &kvcore.Memory{
					Guest: resource.NewScaledQuantity(int64(machine.MemoryInGigs), resource.Giga),
				},
				Devices: kvcore.Devices{
					Disks: []kvcore.Disk{
						{
							Name: "base",
							DiskDevice: kvcore.DiskDevice{
								Disk: &kvcore.DiskTarget{
									Bus: kvcore.DiskBusVirtio,
								},
							},
							BootOrder: util.UintPointer(1),
						},
						{
							Name: "cloudinitdisk",
							DiskDevice: kvcore.DiskDevice{
								Disk: &kvcore.DiskTarget{
									Bus: kvcore.DiskBusVirtio,
								},
							},
							BootOrder: util.UintPointer(3),
						},
					},
					Interfaces: []kvcore.Interface{
						{
							Name: "default",
							InterfaceBindingMethod: kvcore.InterfaceBindingMethod{
								Masquerade: &kvcore.InterfaceMasquerade{},
							},
						},
					},
				},
			},
			Networks: []kvcore.Network{
				{
					Name: "default",
					NetworkSource: kvcore.NetworkSource{
						Pod: &kvcore.PodNetwork{},
					},
				},
			},
			Volumes: []kvcore.Volume{
				{
					Name: "base",
					VolumeSource: kvcore.VolumeSource{
						DataVolume: &kvcore.DataVolumeSource{
							Name: vm.Name,
						},
					},
				},
				{
					Name: "cloudinitdisk",
					VolumeSource: kvcore.VolumeSource{
						CloudInitNoCloud: &kvcore.CloudInitNoCloudSource{
							UserData: cinitData,
						},
					},
				},
			},
		},
	}

	_, err = KubevirtClient.VirtualMachine(Namespace).Create(context.TODO(), vm, metav1.CreateOptions{})
	if err != nil {
		// TODO
		// TODO
		// TODO
		log.Info("Failed to create VM: %v", err)
	}
}
