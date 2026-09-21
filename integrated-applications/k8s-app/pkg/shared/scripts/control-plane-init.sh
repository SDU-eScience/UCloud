emit() { /opt/ucloud/ucviz stage "$@"; }
exec >> /work/stdout-0.log 2>&1

emit "Configuring machine" 10
echo 'ucloud:ucloud' | sudo chpasswd
echo "net.ipv4.ip_nonlocal_bind=1" > /etc/sysctl.d/99-custom.conf
sysctl -f /etc/sysctl.d/99-custom.conf

sudo mkdir -p /var/lib/ucloud || true
cat /etc/ucloud-stack/join-token.txt | tee /var/lib/ucloud/join-token.txt
export K3S_TOKEN="$(cat /var/lib/ucloud/join-token.txt)"

export CURR_HOST=$(hostname)
export NODE_NAME=$(ucloud introspect private-network ls -json | jq -r '.networks[0].members[] | select(.name==$ENV.CURR_HOST).labels["ucloud.dk/service-ip-address"]' 2>/dev/null)

emit "Waiting for control plane" 30
while [ ! -f /etc/ucloud-stack/k3s-url.txt ]; do sleep 1; done
cat /etc/ucloud-stack/k3s-url.txt | tee /var/lib/ucloud/k3s-url.txt
export K3S_URL="$(cat /var/lib/ucloud/k3s-url.txt)"

emit "Joining cluster" 60
curl -sfL https://get.k3s.io | \
	INSTALL_K3S_EXEC="server --cluster-cidr=10.200.0.0/16 --service-cidr=10.201.0.0/16 --flannel-backend=wireguard-native --node-external-ip=$NODE_NAME --node-ip=$NODE_NAME --disable-network-policy  \
	--etcd-arg=--initial-advertise-peer-urls='http://$NODE_NAME:2380' --etcd-arg=--listen-peer-urls='http://0.0.0.0:2380,http://$NODE_NAME:2380' \
	--etcd-arg=--advertise-client-urls='http://$NODE_NAME:2379' --etcd-arg=--listen-client-urls='http://0.0.0.0:2379,http://$NODE_NAME:2379' \
" \
	sh -

emit "Initialization complete" 100
