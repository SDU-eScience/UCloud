package containers

import (
	"fmt"
	core "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	"strings"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func injectSshKeys(jobId string, pod *core.Pod, userContainer *core.Container) {
	job, ok := ctrl.RetrieveJob(jobId)
	if !ok {
		return
	}

	port := shared.GetAssignedSshPort(job)
	if port.Present {
		keys, err := orc.BrowseSshKeys(job.Id) // TODO This is called once for every rank which is not needed
		if err == nil && len(keys) > 0 {
			pod.Spec.InitContainers = append(pod.Spec.InitContainers, core.Container{})
			sshContainer := &pod.Spec.InitContainers[len(pod.Spec.InitContainers)-1]

			{
				// Establish a shared volume for storing the SSH keys
				volName := "ssh-keys"
				pod.Spec.Volumes = append(pod.Spec.Volumes, core.Volume{
					Name: volName,
					VolumeSource: core.VolumeSource{
						EmptyDir: &core.EmptyDirVolumeSource{},
					},
				})
				volMount := core.VolumeMount{
					Name:      volName,
					ReadOnly:  false,
					MountPath: "/etc/ucloud/ssh",
				}

				userContainer.VolumeMounts = append(userContainer.VolumeMounts, volMount)
				sshContainer.VolumeMounts = append(sshContainer.VolumeMounts, volMount)
			}

			{
				// Prepare a container which creates the authorized_keys file and sets permissions on it
				sshContainer.Name = "ssh-keys"
				sshContainer.Image = "alpine:latest"
				sshContainer.Resources.Limits = map[core.ResourceName]resource.Quantity{}
				sshContainer.Resources.Requests = map[core.ResourceName]resource.Quantity{}

				addResource := func(name core.ResourceName, value int64, scale resource.Scale) {
					sshContainer.Resources.Limits.Name(name, resource.DecimalSI).SetScaled(value, scale)
					sshContainer.Resources.Requests.Name(name, resource.DecimalSI).SetScaled(value, scale)
				}

				addResource(core.ResourceCPU, 100, resource.Milli)
				addResource(core.ResourceMemory, 64, resource.Mega)

				bashScript := strings.Builder{}
				bashScript.WriteString("chmod 700 /etc/ucloud/ssh\n")
				bashScript.WriteString("touch /etc/ucloud/ssh/authorized_keys.ucloud\n")
				bashScript.WriteString("chmod 600 /etc/ucloud/ssh/authorized_keys.ucloud\n")
				bashScript.WriteString(fmt.Sprintf("chown %d:%d -R /etc/ucloud/ssh\n", filesystem.DefaultUid, filesystem.DefaultUid))
				bashScript.WriteString("cat >> /etc/ucloud/ssh/authorized_keys.ucloud << EOF\n")
				for _, key := range keys {
					bashScript.WriteString(key.Specification.Key)
					bashScript.WriteString("\n")
				}
				bashScript.WriteString("EOF\n")

				sshContainer.Command = []string{"/bin/sh", "-c", bashScript.String()}
			}
		}
	}
}
