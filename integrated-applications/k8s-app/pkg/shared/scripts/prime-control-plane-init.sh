emit() { /opt/ucloud/ucviz stage "$@"; }
exec >> /work/stdout-0.log 2>&1

emit "Configuring machine (1 / 2)" 5
echo 'ucloud:ucloud' | sudo chpasswd
echo "net.ipv4.ip_nonlocal_bind=1" > /etc/sysctl.d/99-custom.conf
sysctl -f /etc/sysctl.d/99-custom.conf

emit "Configuring machine (2 / 2)" 10
sudo apt-get update && sudo apt-get install jq

sudo mkdir -p /var/lib/ucloud || true
sudo mkdir -p /etc/ucloud-stack/k3s/storage || true

cat /etc/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt
export K3S_TOKEN="$(cat /var/lib/ucloud/join-token.txt)"

# Extract VM's service IP
export MAIN_URL=$(ucloud introspect private-network ls -json | jq -r '.networks[0].members[] | select(.name=="control-plane-1").labels["ucloud.dk/service-ip-address"]' 2>/dev/null)

emit "Installing Kubernetes" 30
# Setup k3s
curl -sfL https://get.k3s.io | \
	INSTALL_K3S_EXEC="server --cluster-cidr=10.200.0.0/16 --service-cidr=10.201.0.0/16 --flannel-backend=wireguard-native --node-external-ip=$MAIN_URL --advertise-address=$MAIN_URL \
	--default-local-storage-path /etc/ucloud-stack/k3s/storage \
	#--cluster-init \
	#--node-ip=$MAIN_URL \
	#--etcd-arg=--initial-advertise-peer-urls='https://$MAIN_URL:2380' --etcd-arg=--listen-peer-urls='https://127.0.0.1:2380,https://$MAIN_URL:2380' \
	#--etcd-arg=--advertise-client-urls='https://$MAIN_URL:2379' --etcd-arg=--listen-client-urls='https://127.0.0.1:2379,https://$MAIN_URL:2379' \
	#--etcd-arg=--initial-cluster=control-plane-1='https://$MAIN_URL:2380' --etcd-arg=--name=control-plane-1 \
	" \
	sh -

echo "https://$MAIN_URL:6443" > /etc/ucloud-stack/k3s-url.txt

emit "Configuring Kubernetes" 60
# Setup kubectl for ucloud user
sudo mkdir -p /home/ucloud/.kube
sudo cp /etc/rancher/k3s/k3s.yaml /home/ucloud/.kube/config
sudo chown -R ucloud:ucloud /home/ucloud/.kube
echo "export KUBECONFIG=/home/ucloud/.kube/config" >> /home/ucloud/.bashrc
source /home/ucloud/.bashrc

sudo snap install helm --classic

cat /etc/ucloud-stack/admin-token.yml | tee /var/lib/ucloud/admin-token.yml

emit "Installing dashboard" 75
# Install Headlamp
su ucloud -c "helm repo add headlamp https://kubernetes-sigs.github.io/headlamp/"
su ucloud -c "helm install headlamp headlamp/headlamp --namespace headlamp --create-namespace --set service.type=NodePort --set service.nodePort=30500"

kubectl -n headlamp create token headlamp | tee /etc/ucloud-stack/headlamp-token

# Generate Kubernetes authentication token and wait for it to be ready
kubectl apply -f /var/lib/ucloud/admin-token.yml
TRIES=0
COMMAND_STATUS=1
until [[ $COMMAND_STATUS -eq 0 || $TRIES -eq 10 ]]; do
	kubectl describe secret admin | grep -E '^token' >/dev/null
	let COMMAND_STATUS=$?
	sleep 1
	let TRIES=TRIES+1
done
kubectl get secret admin -n default -o jsonpath='{.data.token}' | base64 -d | tee /etc/ucloud-stack/kube-api-token

export K8S_API_TOKEN=$(cat /etc/ucloud-stack/kube-api-token)
sed -i 's/MYTOKEN/'"$K8S_API_TOKEN"'/' /etc/ucloud-stack/kubeconfig

emit "Initialization complete" 100
