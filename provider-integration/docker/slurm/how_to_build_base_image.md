# Instructions

```
git clone https://github.com/giovtorres/slurm-docker-cluster.git
cd slurm-docker-cluster/
git checkout b20f12db67dae088d1920f50d0df57a64172f5f2
docker buildx build \
    --tag dreg.cloud.sdu.dk/ucloud-dev/slurm-docker-cluster:23.02.7 \
    --platform linux/arm64/v8,linux/amd64/v2,linux/amd64 \
    --build-arg SLURM_TAG="slurm-23-02-7-1" \
    --push \
    .
```

