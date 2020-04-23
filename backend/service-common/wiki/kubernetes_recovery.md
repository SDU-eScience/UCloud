# Kubernetes Recovery

`kuberecov`

```
#!/usr/bin/env bash
kubectl -s https://localhost:6443 --insecure-skip-tls-verify=True --client-certificate /etc/kubernetes/ssl/kube-admin.pem --client-key /etc/kubernetes/ssl/kube-admin-key.pem $@
```

`kubedump`

```
#!/usr/bin/env bash

set -e
set -x

CONTEXT="dev-backup"

if [[ -z ${CONTEXT} ]]; then
  echo "Usage: $0 KUBE-CONTEXT"
  exit 1
fi

NAMESPACES=$(./kuberecov get -o json namespaces|jq '.items[].metadata.name'|sed "s/\"//g")
#RESOURCES="configmap secret daemonset deployment service hpa"
RESOURCES=`./kuberecov api-resources -o name`

for ns in ${NAMESPACES};do
  for resource in ${RESOURCES};do
    rsrcs=$(./kuberecov -n ${ns} get -o json ${resource}|jq '.items[].metadata.name'|sed "s/\"//g")
    for r in ${rsrcs};do
      dir="${CONTEXT}/${ns}/${resource}"
      mkdir -p "${dir}"
      ./kuberecov -n ${ns} get -o yaml ${resource} ${r} > "${dir}/${r}.yaml"
    done
  done
done
```