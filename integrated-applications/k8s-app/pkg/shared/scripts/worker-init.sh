emit() { /opt/ucloud/ucviz stage "$@"; }
exec >> /work/stdout-0.log 2>&1

emit "Configuring machine" 10
echo 'ucloud:ucloud' | sudo chpasswd

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
	INSTALL_K3S_EXEC="agent --node-external-ip=$NODE_NAME"\
	sh -

export NODE_GROUP="$(basename "$(hostname)" | sed 's/-[0-9]*$//')"
until k3s kubectl label node "$(hostname)" "ucloud.dk/k8s-node-group=${NODE_GROUP}" --overwrite 2>/dev/null; do sleep 2; done

emit "Initialization complete" 100
