# Using Stolon

## Using `stolonctl`

Connect to one of the proxy pods and use the following alias to correctly connect to the current cluster:

```
alias s='stolonctl --cluster-name stolon --kube-resource-kind configmap --store-backend kubernetes'
```

## Recovering from a broken keeper

First confirm the broken keeper with `s status`. Next remove it from the cluster:

```
s removekeeper <uid>
```

You should now be able to reintroduce the pod in the statefulset. 