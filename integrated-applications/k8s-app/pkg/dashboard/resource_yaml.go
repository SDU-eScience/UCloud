package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	k8syaml "k8s.io/apimachinery/pkg/util/yaml"
	"k8s.io/client-go/dynamic"
	"sigs.k8s.io/yaml"
)

var dashboardResourceManagedMetadata = []string{
	"creationTimestamp",
	"deletionTimestamp",
	"deletionGracePeriodSeconds",
	"generation",
	"managedFields",
	"resourceVersion",
	"selfLink",
	"uid",
}

func dashboardResourceInterface(client *K8sClient, def ResourceTypeDef, namespace string) dynamic.ResourceInterface {
	if def.Namespaced {
		return client.Dynamic.Resource(def.Gvr).Namespace(namespace)
	}
	return client.Dynamic.Resource(def.Gvr)
}

func dashboardResourceGet(ctx context.Context, client *K8sClient, def ResourceTypeDef, namespace string, name string) (*unstructured.Unstructured, error) {
	resource := dashboardResourceInterface(client, def, namespace)
	return resource.Get(ctx, name, metav1.GetOptions{})
}

func dashboardResourceYaml(object *unstructured.Unstructured) (string, error) {
	data, err := yaml.Marshal(object.Object)
	return string(data), err
}

func dashboardResourceYamlTexts(object *unstructured.Unstructured) (string, string, error) {
	viewObject := object.DeepCopy()
	unstructured.RemoveNestedField(viewObject.Object, "metadata", "managedFields")
	unstructured.RemoveNestedField(viewObject.Object, "metadata", "selfLink")
	unstructured.RemoveNestedField(viewObject.Object, "metadata", "resourceVersion")
	annotations := viewObject.GetAnnotations()
	delete(annotations, "kubectl.kubernetes.io/last-applied-configuration")
	if len(annotations) == 0 {
		unstructured.RemoveNestedField(viewObject.Object, "metadata", "annotations")
	} else {
		viewObject.SetAnnotations(annotations)
	}
	view, err := dashboardResourceYaml(viewObject)
	if err != nil {
		return "", "", err
	}
	editable := object.DeepCopy()
	delete(editable.Object, "status")
	metadata, _, err := unstructured.NestedMap(editable.Object, "metadata")
	if err != nil {
		return "", "", err
	}
	for _, field := range dashboardResourceManagedMetadata {
		delete(metadata, field)
	}
	editable.Object["metadata"] = metadata
	draft, err := dashboardResourceYaml(editable)
	return view, draft, err
}

func dashboardResourceUpdateYaml(ctx context.Context, client *K8sClient, def ResourceTypeDef, original *unstructured.Unstructured, source string) (*unstructured.Unstructured, error) {
	if !def.CanUpdate {
		return nil, fmt.Errorf("this resource type does not support updates")
	}
	decoder := k8syaml.NewYAMLOrJSONDecoder(strings.NewReader(source), 4096)
	var object unstructured.Unstructured
	err := decoder.Decode(&object)
	if err != nil {
		return nil, fmt.Errorf("invalid YAML: %w", err)
	}
	var extra json.RawMessage
	err = decoder.Decode(&extra)
	if err != io.EOF {
		return nil, fmt.Errorf("provide exactly one Kubernetes resource")
	}
	if len(object.Object) == 0 {
		return nil, fmt.Errorf("provide a Kubernetes resource object")
	}
	sameType := object.GetAPIVersion() == original.GetAPIVersion() && object.GetKind() == original.GetKind()
	sameName := object.GetName() == original.GetName() && object.GetNamespace() == original.GetNamespace()
	if !sameType || !sameName {
		return nil, fmt.Errorf("apiVersion, kind, metadata.name, and metadata.namespace cannot be changed")
	}
	if _, present := object.Object["status"]; present {
		return nil, fmt.Errorf("status cannot be edited here")
	}
	metadata, _, err := unstructured.NestedMap(object.Object, "metadata")
	if err != nil {
		return nil, fmt.Errorf("invalid metadata: %w", err)
	}
	protected := original.DeepCopy()
	originalMetadata, _, err := unstructured.NestedMap(protected.Object, "metadata")
	if err != nil {
		return nil, err
	}
	for _, field := range dashboardResourceManagedMetadata {
		delete(metadata, field)
		if value, present := originalMetadata[field]; present {
			metadata[field] = value
		}
	}
	object.Object["metadata"] = metadata
	if status, present := protected.Object["status"]; present {
		object.Object["status"] = status
	}
	resource := dashboardResourceInterface(client, def, original.GetNamespace())
	return resource.Update(ctx, &object, metav1.UpdateOptions{
		FieldValidation: metav1.FieldValidationStrict,
	})
}
