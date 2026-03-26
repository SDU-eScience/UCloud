package shared

import (
	"fmt"
	"strings"

	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	"ucloud.dk/pkg/controller"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

type PublicIpPrepared struct {
	Service              *core.Service
	EnvironmentVariables map[string]string
}

func PublicIpPrepare(job *orc.Job, firewall *networking.NetworkPolicy) PublicIpPrepared {
	ips, privateIps, err := controller.PublicIpBindToJob(job)
	if err != nil {
		_ = controller.JobTrackMessage([]controller.JobMessage{
			{JobId: job.Id,
				Message: fmt.Sprintf("Failed to bind IP address. %s", err),
			},
		})
		return PublicIpPrepared{}
	} else if len(ips) > 0 {
		envVars := map[string]string{}
		serviceLabel := JobIdLabel(job.Id)
		rankLabel := JobRankLabel(0)

		service := &core.Service{
			ObjectMeta: meta.ObjectMeta{
				Name: fmt.Sprintf("%s-ip", ServiceName(job.Id)),
				Labels: map[string]string{
					serviceLabel.First: serviceLabel.Second,
				},
			},
			Spec: core.ServiceSpec{
				Type:      core.ServiceTypeClusterIP,
				ClusterIP: core.ClusterIPNone,
				Selector: map[string]string{
					serviceLabel.First: serviceLabel.Second,
					rankLabel.First:    rankLabel.Second,
				},
			},
		}

		// NOTE(Dan): This is needed to make sure that the IP becomes routable through the external IP
		service.Spec.ClusterIP = ""

		message := strings.Builder{}
		message.WriteString("Successfully attached the following IP addresses: ")

		for ipIdx, ip := range ips {
			if ip.Status.IpAddress.Present {
				privateIp := privateIps[ipIdx]
				if ipIdx != 0 {
					message.WriteString(", ")
				}
				message.WriteString(ip.Status.IpAddress.Value)

				// Tell K8s to use the IP
				service.Spec.ExternalIPs = append(service.Spec.ExternalIPs, privateIp.String())

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

					AllowNetworkFromWorld(firewall, fw.Value.OpenPorts)
				}

				envVarName := "UCLOUD_PUBLIC_IP"
				if ipIdx != 0 {
					envVarName += fmt.Sprintf("_%v", ipIdx+1)
				}
				envVars[envVarName] = ip.Status.IpAddress.Value
			}
		}

		_ = controller.JobTrackMessage([]controller.JobMessage{
			{
				JobId:   job.Id,
				Message: message.String(),
			},
		})

		return PublicIpPrepared{
			Service:              service,
			EnvironmentVariables: envVars,
		}
	} else {
		return PublicIpPrepared{}
	}
}
