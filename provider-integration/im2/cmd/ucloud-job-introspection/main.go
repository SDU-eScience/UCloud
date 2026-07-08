package main

import _ "ucloud.dk/shared/pkg/silentlog"
import job_introspection "ucloud.dk/pkg/integrations/k8s/job-introspection"

func main() {
	job_introspection.Launch()
}
