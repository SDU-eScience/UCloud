<p align='center'>
<a href='/docs/developer-guide/core/monitoring/grafana.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/monitoring/stolon-recovery.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring, Alerting and Procedures](/docs/developer-guide/core/monitoring/README.md) / Kubernetes Recovery
# Kubernetes Recovery

---

__⚠️WARNING:__ The following are a collection of scripts which can be useful when debugging Kubernetes. The scripts 
have not been updated in a while and might no longer be useful. Please verify before using them.

---

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

