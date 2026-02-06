package shared

import (
	"fmt"
	"math/rand"
	"strconv"
	"strings"
	"sync"

	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

var sshPortsInUse = map[int]bool{}
var sshPortsMutex = sync.Mutex{}

const sshPrefix = "SSH: Connected! Available at: ssh ucloud@"

var sshConfig cfg.KubernetesSshConfiguration

func initSsh() {
	sshConfig = ServiceConfig.Compute.Ssh
	if sshConfig.Enabled {
		jobs := ctrl.GetJobs()
		for _, job := range jobs {
			port := GetAssignedSshPort(job)
			if port.Present {
				sshPortsInUse[port.Value] = true
			}
		}
	}
}

func assignSshPort(job *orc.Job) (util.Option[int], *util.HttpError) {
	if IsSensitiveProject(job.Owner.Project.Value) {
		return util.OptNone[int](), util.UserHttpError("This project does not allow for SSH")
	}

	sshMode := job.Status.ResolvedApplication.Value.Invocation.Ssh.Value.Mode
	sshEnabled := false
	switch sshMode {
	case orc.SshModeDisabled:
		sshEnabled = false
	case orc.SshModeOptional:
		sshEnabled = job.Specification.SshEnabled
	case orc.SshModeMandatory:
		sshEnabled = true
	}

	if !sshEnabled {
		return util.OptNone[int](), nil
	}

	if !sshConfig.Enabled {
		return util.OptNone[int](), util.UserHttpError("SSH not supported")
	} else {
		sshPortsMutex.Lock()
		defer sshPortsMutex.Unlock()

		count := sshConfig.PortMax - sshConfig.PortMin
		if count <= 0 {
			return util.OptNone[int](), util.ServerHttpError("No more SSH ports available")
		}

		attempt := rand.Intn(count)
		remaining := count
		for remaining > 0 {
			port := sshConfig.PortMin + (attempt % count)
			_, exists := sshPortsInUse[port]
			if !exists {
				_ = ctrl.TrackJobMessages([]ctrl.JobMessage{
					{
						JobId: job.Id,
						Message: fmt.Sprintf(
							"%s%s -p %d",
							sshPrefix,
							sshConfig.Hostname.GetOrDefault(sshConfig.IpAddress),
							port,
						),
					},
				})
				sshPortsInUse[port] = true
				return util.OptValue(port), nil
			}

			attempt++
			remaining--
		}

		return util.OptNone[int](), util.ServerHttpError("No more SSH ports available")
	}
}

func ClearAssignedSshPort(job *orc.Job) {
	port := GetAssignedSshPort(job)
	if port.Present {
		sshPortsMutex.Lock()
		delete(sshPortsInUse, port.Value)
		defer sshPortsMutex.Unlock()
	}
}

func GetAssignedSshPort(job *orc.Job) util.Option[int] {
	var result util.Option[int]

	for _, update := range job.Updates {
		if update.Status.Present && strings.HasPrefix(update.Status.Value, sshPrefix) {
			idx := strings.Index(update.Status.Value, " -p ")
			if idx == -1 {
				continue
			}

			portString := update.Status.Value[idx+4:]
			port, err := strconv.ParseInt(portString, 10, 64)
			if err == nil {
				result.Set(int(port))
			}
		}
	}

	return result
}

func AssignAndPrepareSshService(job *orc.Job) util.Option[*core.Service] {
	if IsSensitiveProject(job.Owner.Project.Value) {
		return util.OptNone[*core.Service]()
	}

	port, _ := assignSshPort(job)
	if !port.Present {
		return util.OptNone[*core.Service]()
	} else {
		serviceLabel := JobIdLabel(job.Id)
		rankLabel := JobRankLabel(0)
		service := &core.Service{
			ObjectMeta: meta.ObjectMeta{
				Name: fmt.Sprintf("j-%v-ssh", job.Id),
				Labels: map[string]string{
					serviceLabel.First: serviceLabel.Second,
				},
			},
			Spec: core.ServiceSpec{
				Type:      core.ServiceTypeClusterIP,
				ClusterIP: "",
				Selector: map[string]string{
					serviceLabel.First: serviceLabel.Second,
					rankLabel.First:    rankLabel.Second,
				},
				ExternalIPs: []string{sshConfig.IpAddress},
				Ports: []core.ServicePort{
					{
						Name:     "ssh",
						Protocol: core.ProtocolTCP,
						Port:     int32(port.Value),
						TargetPort: intstr.IntOrString{
							IntVal: 22,
						},
					},
				},
			},
		}

		return util.OptValue(service)
	}
}
