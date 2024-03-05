# Instructions

```
git clone https://github.com/giovtorres/slurm-docker-cluster.git
cd slurm-docker-cluster/
docker buildx build \
    --tag dreg.cloud.sdu.dk/ucloud-dev/slurm-docker-cluster:23.02.7 \
    --platform linux/arm64/v8,linux/amd64 \
    --build-arg SLURM_TAG="slurm-23-02-7-1" \
    --push \
    .
```

