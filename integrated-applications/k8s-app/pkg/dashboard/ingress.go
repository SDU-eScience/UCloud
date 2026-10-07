package dashboard

import (
	"net/url"
	"strings"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
)

func dashboardIngressServiceUrl(obj *unstructured.Unstructured) string {
	rules, _, _ := unstructured.NestedSlice(obj.Object, "spec", "rules")
	for _, item := range rules {
		rule, ok := item.(map[string]any)
		if !ok {
			continue
		}
		host, _, _ := unstructured.NestedString(rule, "host")
		if host == "" || strings.Contains(host, "*") {
			continue
		}
		paths, _, _ := unstructured.NestedSlice(rule, "http", "paths")
		for _, item := range paths {
			path, ok := item.(map[string]any)
			if !ok {
				continue
			}
			prefix, _, _ := unstructured.NestedString(path, "path")
			if prefix == "" {
				prefix = "/"
			}
			target := url.URL{Scheme: "https", Host: host, Path: prefix}
			return target.String()
		}
	}
	return ""
}
