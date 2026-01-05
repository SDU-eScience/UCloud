set -ex

while ! test -e "/mnt/k3s/kubeconfig.yaml"; do
  sleep 1
  echo "Waiting for Kubernetes to be ready..."
done

sleep 2

mkdir -p /mnt/storage/{home,projects,trash,collections}

sed -i s/127.0.0.1/im2k3/g /mnt/k3s/kubeconfig.yaml

kubectl --kubeconfig /mnt/k3s/kubeconfig.yaml create namespace ucloud-apps

cat > /tmp/pvc.yml << EOF
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: cephfs
  namespace: ucloud-apps
spec:
  capacity:
    storage: 1000Gi
  volumeMode: Filesystem
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  storageClassName: ""
  hostPath:
    path: "/mnt/storage"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: cephfs
  namespace: ucloud-apps
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: ""
  volumeName: cephfs
  resources:
    requests:
      storage: 1000Gi
EOF

kubectl --kubeconfig /mnt/k3s/kubeconfig.yaml create -f /tmp/pvc.yml
rm /tmp/pvc.yml
rm /etc/ucloud/init.sh
