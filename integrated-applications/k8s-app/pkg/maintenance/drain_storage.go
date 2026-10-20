package maintenance

import (
	"context"
	"fmt"
	"strings"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
)

func drainBlock(worker *nodeWorker, blockers []string) {
	message := fmt.Sprintf(
		"The node cannot be drained. These pods are not approved for eviction: %s. Review these pods, or skip this node and update it later",
		strings.Join(blockers, ", "),
	)
	operationLogLines(worker, message)
	finish(worker, PhaseBlocked, message)
}

func drainEmptyDirBlocker(pod *corev1.Pod) string {
	volumes := []string{}
	for _, volume := range pod.Spec.Volumes {
		if volume.EmptyDir != nil {
			volumes = append(volumes, volume.Name)
		}
	}
	message := "unapproved local emptyDir storage (" + strings.Join(volumes, ", ") + ")"
	if pod.Namespace == "kube-system" && pod.Spec.ServiceAccountName == "traefik" && !drainTraefikDataDisposable(pod) {
		message += ". This Traefik pod stores local data. If you customized Traefik, move that data to a volume first"
	}
	return message
}

func drainEmptyDirDisposable(
	ctx context.Context,
	clientset *kubernetes.Clientset,
	pod *corev1.Pod,
	owner *metav1.OwnerReference,
) (bool, error) {
	if pod.Namespace != "kube-system" || owner == nil || owner.APIVersion != "apps/v1" || owner.Kind != "ReplicaSet" {
		return false, nil
	}
	var deploymentName string
	var paths map[string]string
	switch pod.Spec.ServiceAccountName {
	case "metrics-server":
		deploymentName = "metrics-server"
		paths = map[string]string{"tmp-dir": "/tmp"}
	case "traefik":
		deploymentName = "traefik"
		paths = map[string]string{"tmp": "/tmp"}
		if drainTraefikDataDisposable(pod) {
			paths["data"] = "/data"
		}
	default:
		return false, nil
	}
	if !drainEmptyDirPathsApproved(pod, paths) {
		return false, nil
	}

	replicaSet, err := clientset.AppsV1().ReplicaSets(pod.Namespace).Get(ctx, owner.Name, metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	if replicaSet.UID != owner.UID || replicaSet.DeletionTimestamp != nil {
		return false, nil
	}
	deploymentOwner := metav1.GetControllerOf(replicaSet)
	if deploymentOwner == nil || deploymentOwner.APIVersion != "apps/v1" ||
		deploymentOwner.Kind != "Deployment" || deploymentOwner.Name != deploymentName {
		return false, nil
	}
	deployment, err := clientset.AppsV1().Deployments(pod.Namespace).Get(ctx, deploymentName, metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return deployment.UID == deploymentOwner.UID && deployment.DeletionTimestamp == nil, nil
}

func drainEmptyDirPathsApproved(pod *corev1.Pod, paths map[string]string) bool {
	if len(pod.Spec.Containers) != 1 || len(pod.Spec.InitContainers) != 0 {
		return false
	}
	for _, volume := range pod.Spec.Volumes {
		if volume.EmptyDir == nil {
			continue
		}
		path, approved := paths[volume.Name]
		if !approved {
			return false
		}
		mounted := false
		for _, mount := range pod.Spec.Containers[0].VolumeMounts {
			if mount.Name != volume.Name {
				continue
			}
			if mount.MountPath != path || mount.SubPath != "" || mount.SubPathExpr != "" {
				return false
			}
			mounted = true
		}
		if !mounted {
			return false
		}
	}
	return true
}

func drainTraefikDataDisposable(pod *corev1.Pod) bool {
	if len(pod.Spec.Containers) != 1 || len(pod.Spec.InitContainers) != 0 {
		return false
	}
	container := pod.Spec.Containers[0]
	image := strings.TrimPrefix(container.Image, "docker.io/")
	if container.Name != "traefik" || (!strings.HasPrefix(image, "rancher/mirrored-library-traefik:") &&
		!strings.HasPrefix(image, "rancher/mirrored-library-traefik@")) {
		return false
	}
	if len(container.Command) != 0 || len(container.EnvFrom) != 0 {
		return false
	}
	for _, env := range container.Env {
		if strings.HasPrefix(strings.ToUpper(env.Name), "TRAEFIK_") || env.Name == "HOME" || env.Name == "XDG_CONFIG_HOME" {
			return false
		}
	}
	for _, arg := range container.Args {
		arg = strings.ToLower(arg)
		if !strings.HasPrefix(arg, "--") || strings.Contains(arg, "certificatesresolvers") ||
			strings.Contains(arg, "configfile") || strings.Contains(arg, "providers.file") {
			return false
		}
	}
	for _, mount := range container.VolumeMounts {
		if mount.MountPath != "/tmp" && mount.MountPath != "/data" &&
			mount.MountPath != "/var/run/secrets/kubernetes.io/serviceaccount" {
			return false
		}
	}
	return true
}
