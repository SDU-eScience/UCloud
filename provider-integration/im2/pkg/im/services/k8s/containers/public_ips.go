package containers

import (
	"fmt"
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	"strings"
	ctrl "ucloud.dk/pkg/im/controller"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func preparePublicIp(job *orc.Job, service *core.Service, firewall *networking.NetworkPolicy) {
	if service == nil {
		// Nothing to do if we are not supposed to touch the resources
		return
	}

	ips, err := ctrl.BindIpsToJob(job)
	if err != nil {
		_ = ctrl.TrackJobMessages([]ctrl.JobMessage{
			{
				JobId:   job.Id,
				Message: fmt.Sprintf("Failed to bind IP address. %s", err),
			},
		})
		return
	} else if len(ips) > 0 {
		// NOTE(Dan): This is needed to make sure that the IP becomes routable through the external IP
		service.Spec.ClusterIP = ""

		message := strings.Builder{}
		message.WriteString("Successfully attached the following IP addresses: ")

		for ipIdx, ip := range ips {
			if ip.Status.IpAddress.Present {
				if ipIdx != 0 {
					message.WriteString(", ")
				}
				message.WriteString(ip.Status.IpAddress.Value)

				// Tell K8s to use the IP
				service.Spec.ExternalIPs = append(service.Spec.ExternalIPs, ip.Status.IpAddress.Value)

				// Forward the correct ports
				fw := &ip.Specification.Firewall
				if fw.Present {
					for portIdx, portRange := range fw.Value.OpenPorts {
						for port := portRange.Start; port <= portRange.End; port++ {
							service.Spec.Ports = append(service.Spec.Ports, core.ServicePort{
								Name:     fmt.Sprintf("p-%d-%d-%d", ipIdx, portIdx, port),
								Protocol: core.Protocol(portRange.Protocol),
								Port:     int32(port),
								TargetPort: intstr.IntOrString{
									Type:   intstr.Int,
									IntVal: int32(port),
								},
							})
						}
					}

					allowNetworkFromWorld(firewall, fw.Value.OpenPorts)
				}
			}
		}

		_ = ctrl.TrackJobMessages([]ctrl.JobMessage{
			{
				JobId:   job.Id,
				Message: message.String(),
			},
		})
	}
}
