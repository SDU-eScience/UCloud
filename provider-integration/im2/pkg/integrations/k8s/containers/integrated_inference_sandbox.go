package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"reflect"
	"strings"
	"time"

	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const inferenceSandboxInactivityDuration = 15 * time.Minute

const integratedInferenceSandboxAppName = shared.InferenceSandboxAppName
const inferenceSandboxImage = "dreg.cloud.sdu.dk/ucloud/ucloud-inf-tools:2026.3.118"

func initIntegratedInferenceSandbox() {
	if ServiceConfig.Compute.IntegratedTerminal.Enabled {
		shared.InferenceSandboxRegisterInternetApply(inferenceSandboxApplyInternetAccess)
		IApps[integratedInferenceSandboxAppName] = ContainerIAppHandler{
			Flags:                           controller.IntegratedAppInternal | controller.IntegratedAppProjectScoped,
			RetrieveDefaultConfiguration:    integratedSandboxRetrieveDefaultConfiguration,
			ShouldRun:                       inferenceSandboxShouldRun,
			MutateJobNonPersistent:          inferenceSandboxMutateJobNonPersistent,
			MutateJobSpecBeforeRegistration: inferenceSandboxMutateJobBeforeRegistration,
			MutatePod:                       inferenceSandboxMutatePod,
			MutateNetworkPolicy:             inferenceSandboxMutateNetworkPolicy,
			ValidateConfiguration:           integratedSandboxValidateConfiguration,
		}
	}
}

func inferenceSandboxMutateJobBeforeRegistration(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError {
	readableUsername, _, _ := strings.Cut(owner.CreatedBy, "#")
	if err := integratedSandboxMutateJobBeforeRegistration(fmt.Sprintf("Inference sandbox (%s)", readableUsername), spec); err != nil {
		return err
	}
	spec.Product.Id = shared.IntegratedTerminalAppName
	spec.Product.Category = shared.IntegratedTerminalAppName
	return nil
}

func inferenceSandboxMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
	if err := integratedSandboxMutatePod(integratedTerminalDimensions, inferenceSandboxImage, pod); err != nil {
		return err
	}

	for i := range pod.Spec.Containers {
		container := &pod.Spec.Containers[i]
		if container.Name == ContainerUserJob {
			container.VolumeMounts = append(container.VolumeMounts, core.VolumeMount{
				Name:      "ucloud-filesystem",
				ReadOnly:  true,
				MountPath: "/mnt/exe",
				SubPath:   shared.ExecutablesDir,
			})
		}
	}

	return nil
}

func inferenceSandboxMutateNetworkPolicy(job *orc.Job, configuration json.RawMessage, firewall *networking.NetworkPolicy, pod *core.Pod) *util.HttpError {
	shared.AllowNetworkToClusterDNS(firewall)
	if shared.InferenceSandboxInternetEnabledFor(integratedSandboxLeaseOwner(job)) {
		shared.AllowNetworkToPublicInternet(firewall, []int32{80, 443})
	}
	return nil
}

func inferenceSandboxApplyInternetAccess(jobId string, enabled bool) *util.HttpError {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	policies := K8sClient.NetworkingV1().NetworkPolicies(ServiceConfig.Compute.Namespace)
	desired := shared.PublicInternetEgressRule([]int32{80, 443})
	for {
		if ctx.Err() != nil {
			return util.UserHttpError("sandbox did not become ready before the command timed out")
		}

		policy, err := policies.Get(ctx, shared.FirewallName(jobId), meta.GetOptions{})
		if err == nil {
			egress := policy.Spec.Egress[:0]
			for _, rule := range policy.Spec.Egress {
				if reflect.DeepEqual(rule, desired) {
					continue
				}
				egress = append(egress, rule)
			}
			if enabled {
				egress = append(egress, desired)
			}
			policy.Spec.Egress = egress
			_, err = policies.Update(ctx, policy, meta.UpdateOptions{})
		}
		if err == nil {
			return nil
		}
		if !apierrors.IsNotFound(err) && !apierrors.IsConflict(err) {
			return util.HttpErrorFromErr(err)
		}
		select {
		case <-ctx.Done():
			return util.UserHttpError("sandbox did not become ready before the command timed out")
		case <-time.After(250 * time.Millisecond):
		}
	}
}

func inferenceSandboxMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	integratedSandboxMutateJobNonPersistent(job, configuration, true)
}

func inferenceSandboxShouldRun(job *orc.Job, configuration json.RawMessage) bool {
	return integratedSandboxShouldRun(job, configuration, true, shared.InferenceSandboxLeaseUntil, func(owner orc.ResourceOwner) {
		_ = shared.InferenceSandboxLease(owner, inferenceSandboxInactivityDuration)
	})
}
