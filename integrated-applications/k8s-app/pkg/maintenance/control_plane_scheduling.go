package maintenance

import (
	"context"
	"fmt"
	"time"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/klog/v2"
	"sigs.k8s.io/yaml"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

func controlPlaneSchedulingRun(ctx context.Context, kubeconfigPath string) {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()

	for {
		sweepCtx, cancel := context.WithTimeout(ctx, 45*time.Second)
		err := controlPlaneSchedulingSweep(sweepCtx, kubeconfigPath)
		cancel()
		if err != nil && !apierrors.IsConflict(err) && ctx.Err() == nil {
			log.Warn("k8s-app control plane scheduling: could not apply the scheduling policy: %s", err)
		}

		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func controlPlaneSchedulingSweep(ctx context.Context, kubeconfigPath string) error {
	record, found, err := readClusterRecord()
	if err != nil {
		return err
	}
	if !found || record.Phase != shared.ClusterRecordPhaseCreated {
		return nil
	}
	recovery, err := recoveryIntentActive()
	if err != nil || recovery {
		return err
	}

	clientset, err := newClient(kubeconfigPath)
	if err != nil {
		return err
	}

	taint := corev1.Taint{Key: controlPlaneLabel, Effect: corev1.TaintEffectNoSchedule}
	for _, addon := range []struct {
		namespace string
		name      string
	}{
		{namespace: "ucloud-k8s-controller", name: "ucloud-k8s-controller"},
		{namespace: "local-path-storage", name: "local-path-provisioner"},
		{namespace: "kube-system", name: "coredns"},
		{namespace: "kube-system", name: "metrics-server"},
		{namespace: "kube-system", name: "traefik"},
	} {
		deployment, err := clientset.AppsV1().Deployments(addon.namespace).Get(ctx, addon.name, metav1.GetOptions{})
		if apierrors.IsNotFound(err) {
			continue
		}
		if err != nil {
			return err
		}
		if !controlPlaneSchedulingTolerate(&deployment.Spec.Template.Spec, taint) {
			continue
		}
		if _, err := clientset.AppsV1().Deployments(addon.namespace).Update(ctx, deployment, metav1.UpdateOptions{}); err != nil {
			return fmt.Errorf("could not update the tolerations of %s/%s: %w", addon.namespace, addon.name, err)
		}
	}
	if err := controlPlaneSchedulingStorageHelper(ctx, clientset, taint); err != nil {
		return err
	}

	nodes, err := clientset.CoreV1().Nodes().List(ctx, metav1.ListOptions{})
	if err != nil {
		return err
	}
	controlPlanes := controlPlaneNames(record)
	for i := range nodes.Items {
		node := &nodes.Items[i]
		if !controlPlanes[node.Name] {
			continue
		}
		present := false
		for _, existing := range node.Spec.Taints {
			if existing.Key == taint.Key && existing.Effect == taint.Effect {
				present = true
				break
			}
		}
		if present {
			continue
		}
		node.Spec.Taints = append(node.Spec.Taints, taint)
		if _, err := clientset.CoreV1().Nodes().Update(ctx, node, metav1.UpdateOptions{}); err != nil {
			return fmt.Errorf("could not taint control-plane node %s: %w", node.Name, err)
		}
		log.Info("k8s-app control plane scheduling: applied the NoSchedule taint to %s", node.Name)
	}

	return nil
}

func controlPlaneSchedulingTolerate(spec *corev1.PodSpec, taint corev1.Taint) bool {
	for _, toleration := range spec.Tolerations {
		if toleration.ToleratesTaint(klog.Background(), &taint, false) {
			return false
		}
	}
	spec.Tolerations = append(spec.Tolerations, corev1.Toleration{
		Key:      taint.Key,
		Operator: corev1.TolerationOpExists,
		Effect:   taint.Effect,
	})
	return true
}

func controlPlaneSchedulingStorageHelper(ctx context.Context, clientset *kubernetes.Clientset, taint corev1.Taint) error {
	config, err := clientset.CoreV1().ConfigMaps("local-path-storage").Get(ctx, "local-path-config", metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return nil
	}
	if err != nil {
		return err
	}
	template := config.Data["helperPod.yaml"]
	if template == "" {
		return nil
	}

	var helper corev1.Pod
	if err := yaml.Unmarshal([]byte(template), &helper); err != nil {
		return fmt.Errorf("could not read the storage helper pod template: %w", err)
	}
	if !controlPlaneSchedulingTolerate(&helper.Spec, taint) {
		return nil
	}
	encoded, err := yaml.Marshal(helper)
	if err != nil {
		return err
	}
	config.Data["helperPod.yaml"] = string(encoded)
	_, err = clientset.CoreV1().ConfigMaps(config.Namespace).Update(ctx, config, metav1.UpdateOptions{})
	return err
}
