package main

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"

	accapi "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	fmt.Println("Hello 2!")
	port := util.OptNone[int]()
	if len(os.Args) >= 2 {
		converted, err := strconv.Atoi(os.Args[1])
		if err == nil {
			port.Set(converted)
		}
	}

	if os.Getenv("UCX_PORT") != "" {
		converted, err := strconv.Atoi(os.Getenv("UCX_PORT"))
		if err == nil {
			port.Set(converted)
		}
	}

	ucx.AppServe(K8sApp, port)
}

func K8sApp() ucx.Application {
	if _, err := os.Stat("/etc/ucloud-stack"); err == nil {
		if os.Getenv("UCX_PORT") != "" {
			return StackUi()
		}
	}

	return &k8sApp{
		WorkerNodes:       1,
		ControlPlaneNodes: 1,
		Ports:             "8080",
	}
}

type k8sApp struct {
	mu      sync.Mutex   `ucx:"-"`
	session *ucx.Session `ucx:"-"`

	Machine           accapi.ProductReference
	WorkerNodes       int
	ControlPlaneNodes int
	Ports             string
}

func PrimeControlPlaneInitScript() string {
	return `
	echo 'ucloud:ucloud' | sudo chpasswd
	echo "net.ipv4.ip_nonlocal_bind=1" > /etc/sysctl.d/99-custom.conf
	sysctl -f /etc/sysctl.d/99-custom.conf

	sudo apt-get update && sudo apt-get install jq

	sudo mkdir -p /var/lib/ucloud || true    
	sudo mkdir -p /etc/ucloud-stack/k3s/storage || true 

	cat /etc/ucloud-stack/join-token.txt > /var/lib/ucloud/join-token.txt
	export K3S_TOKEN="$(cat /var/lib/ucloud/join-token.txt)"

	# Extract VM's service IP
	export MAIN_URL=$(ucloud introspect private-network ls -json | jq '.networks[0].members[] | select(.name=="control-plane-1").labels["ucloud.dk/service-ip-address"]' 2>/dev/null)
	export MAIN_URL=${MAIN_URL#'"'}
	export MAIN_URL=${MAIN_URL%'"'}
	
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

	# Setup kubectl for ucloud user
	sudo mkdir -p /home/ucloud/.kube
	sudo cp /etc/rancher/k3s/k3s.yaml /home/ucloud/.kube/config
	sudo chown -R ucloud:ucloud /home/ucloud/.kube
	echo "export KUBECONFIG=/home/ucloud/.kube/config" >> /home/ucloud/.bashrc
	source /home/ucloud/.bashrc

	sudo snap install helm --classic

	cat /etc/ucloud-stack/admin-token.yml | tee /var/lib/ucloud/admin-token.yml

	
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
	`
}

// NOTE: multiple control planes currently don't work
func ControlPlaneInitScript() string {
	return `
	echo 'ucloud:ucloud' | sudo chpasswd
	echo "net.ipv4.ip_nonlocal_bind=1" > /etc/sysctl.d/99-custom.conf
	sysctl -f /etc/sysctl.d/99-custom.conf

	sudo mkdir -p /var/lib/ucloud || true     
	cat /etc/ucloud-stack/join-token.txt | tee /var/lib/ucloud/join-token.txt
	export K3S_TOKEN="$(cat /var/lib/ucloud/join-token.txt)"

	export CURR_HOST=$(hostname)
	export NODE_NAME=$(ucloud introspect private-network ls -json | jq '.networks[0].members[] | select(.name==$ENV.CURR_HOST).labels["ucloud.dk/service-ip-address"]' 2>/dev/null)
	export NODE_NAME=${NODE_NAME#'"'}
	export NODE_NAME=${NODE_NAME%'"'}

	while [ ! -f /etc/ucloud-stack/k3s-url.txt ]; do sleep 1; done
	cat /etc/ucloud-stack/k3s-url.txt | tee /var/lib/ucloud/k3s-url.txt
	export K3S_URL="$(cat /var/lib/ucloud/k3s-url.txt)"
	curl -sfL https://get.k3s.io | \
		  INSTALL_K3S_EXEC="server --cluster-cidr=10.200.0.0/16 --service-cidr=10.201.0.0/16 --flannel-backend=wireguard-native --node-external-ip=$NODE_NAME --node-ip=$NODE_NAME --disable-network-policy  \
		  	--etcd-arg=--initial-advertise-peer-urls='http://$NODE_NAME:2380' --etcd-arg=--listen-peer-urls='http://0.0.0.0:2380,http://$NODE_NAME:2380' \
			--etcd-arg=--advertise-client-urls='http://$NODE_NAME:2379' --etcd-arg=--listen-client-urls='http://0.0.0.0:2379,http://$NODE_NAME:2379' \
		" \
		  sh - 
	`
}

func WorkerInitScript() string {
	return `
	echo 'ucloud:ucloud' | sudo chpasswd

	sudo mkdir -p /var/lib/ucloud || true     
	cat /etc/ucloud-stack/join-token.txt | tee /var/lib/ucloud/join-token.txt
	export K3S_TOKEN="$(cat /var/lib/ucloud/join-token.txt)"

	export CURR_HOST=$(hostname)
	export NODE_NAME=$(ucloud introspect private-network ls -json | jq '.networks[0].members[] | select(.name==$ENV.CURR_HOST).labels["ucloud.dk/service-ip-address"]' 2>/dev/null)
	export NODE_NAME=${NODE_NAME#'"'}
	export NODE_NAME=${NODE_NAME%'"'}

	while [ ! -f /etc/ucloud-stack/k3s-url.txt ]; do sleep 1; done
	cat /etc/ucloud-stack/k3s-url.txt | tee /var/lib/ucloud/k3s-url.txt
	export K3S_URL="$(cat /var/lib/ucloud/k3s-url.txt)"
	curl -sfL https://get.k3s.io | \
		INSTALL_K3S_EXEC="agent --node-external-ip=$NODE_NAME"\
		sh -
	`
}

func (app *k8sApp) Mutex() *sync.Mutex {
	return &app.mu
}

func (app *k8sApp) Session() **ucx.Session {
	return &app.session
}

func (app *k8sApp) OnInit() {
	// Nothing to do
}

func (app *k8sApp) UserInterface() ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{
		Direction: "column",
		Gap:       8,
	}).Sx(ucx.SxP(4)).Children(
		ucx.MachineTypeSelector("machine", "Machine type", "machine", ucx.MachineCapabilityVm),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 32}).Sx(ucx.SxP(4)).Children(
			// NOTE: multiple control planes currently don't work
			// ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(ucx.InputNumber("controlPlaneNodes", "Number of control planes", "controlPlaneNodes", 1, 32)),
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(ucx.InputNumber("workerNodes", "Number of workers", "workerNodes", 1, 32)),
		),
		ucx.InputText("ports", "Comma-separated list of exposed ports", "8080", "ports"),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 32}).Sx(ucx.SxP(4)).Children(
			ucx.Text("Reserved API port: 6443"),
			ucx.Text("Reserved Headlamp port: 30500"),
		),
		ucx.Button("stack", "Create a stack", ucx.ColorPrimaryMain).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			stackId := fmt.Sprintf("K8s-%v", util.RandomTokenNoTs(4))
			stack, ok := ucxsvc.StackCreate(app, stackId, "Kubernetes")

			if !ok {
				return
			}

			ucxsvc.StackWriteFile(stack, "join-token.txt", util.SecureToken())
			ucxsvc.StackWriteFile(stack, "admin-token.yml", `
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin
  namespace: default
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin
  namespace: default
---
apiVersion: v1
kind: Secret
metadata:
  name: admin
  namespace: default
  annotations:
    kubernetes.io/service-account.name: admin
type: kubernetes.io/service-account-token			
`)

			ucxsvc.StackWriteFile(stack, "kubeconfig", fmt.Sprintf(`apiVersion: v1
kind: Config
clusters:
- name: default
  cluster:
    server: https://app-%s-k8s.dev.cloud.sdu.dk
users:
- name: admin
  user:
    token: MYTOKEN
contexts:
- name: default
  context:
    cluster: default
    user: admin
current-context: default
`, strings.ToLower(stackId)))

			customUi := ucxsvc.UcxInitCustomUiService(stack, 43102, "")
			initPrimeControlPlaneLabels := ucxsvc.StackWriteInitScript(stack, PrimeControlPlaneInitScript()+"\n"+customUi.InitScript)
			initControlPlaneLabels := ucxsvc.StackWriteInitScript(stack, ControlPlaneInitScript())
			initWorkerLabels := ucxsvc.StackWriteInitScript(stack, WorkerInitScript())

			selectedProduct := app.Machine
			if selectedProduct.Id == "" {
				ucxsvc.UiSendFailure(app, "Could not decide on a product!")
				return
			}

			ports := strings.Split(app.Ports, ",")
			ucxsvc.UiSendSuccess(app, fmt.Sprintf("Forwarding ports %s!", app.Ports))
			apiLink := ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-k8s", stackId), ucxsvc.PublicLinkCreateOptions{
				Port: util.OptValue(6443),
				TLS:  true,
			})
			linkAttachments := []orcapi.AppParameterValue{}
			linkAttachments = append(linkAttachments, apiLink)
			linkAttachments = append(linkAttachments, ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-dashboard", stackId), ucxsvc.PublicLinkCreateOptions{
				Port: util.OptValue(30500),
			}))
			for _, p := range ports {
				if p != "" {
					port, err := strconv.Atoi(p)
					if err != nil {
						ucxsvc.UiSendFailure(app, fmt.Sprintf("Could not decode ports! Found list: %v", ports))
						return
					}

					if p == "6443" || p == "30500" {
						ucxsvc.UiSendFailure(app, "Can't use reserved ports 6443, 30500")
						return
					}
					linkAttachments = append(linkAttachments, ucxsvc.PublicLinkCreate(stack, fmt.Sprintf("%s-%d", stackId, port), ucxsvc.PublicLinkCreateOptions{
						Port: util.OptValue(port),
					}))
					ucxsvc.UiSendSuccess(app, fmt.Sprintf("Forwarding port %d!", port))
				}
			}
			networkAttachment := ucxsvc.PrivateNetworkCreate(stack, stackId)

			// initialize control planes
			for i := 1; i <= app.ControlPlaneNodes; i++ {
				nodeGroup := "control-plane"

				attachments := []orcapi.AppParameterValue{
					networkAttachment,
				}
				labels := initControlPlaneLabels
				serviceForwardsTcp := "[2379,2380,2381,6443,6444,10248,10249,10250,10256,10257,10258,10259]"

				if i == 1 {
					labels = util.MapMerge(initPrimeControlPlaneLabels, customUi.Labels)
					attachments = append(attachments, linkAttachments...)
					// serviceForwardsTcp = fmt.Sprintf("[%s]", app.Ports)
				}

				vmId := ucxsvc.VirtualMachineCreate(stack, ucxsvc.VirtualMachineSpec{
					Labels: util.MapMerge(labels, map[string]string{
						"ucloud.dk/service-forward-tcp": serviceForwardsTcp,
						"ucloud.dk/service-forward-udp": "[51820,51821]",
						stackGroupingLabel:              nodeGroup,
					}),
					Product:     selectedProduct,
					Image:       ucxsvc.VmImageUbuntu24_04,
					Hostname:    fmt.Sprintf("control-plane-%v", i),
					Attachments: attachments,
				})
				ucxsvc.UiSendSuccess(app, "Created control plane VM "+vmId+"!")

			}

			// initialize workers
			for i := 1; i <= app.WorkerNodes; i++ {
				nodeGroup := "worker"

				attachments := []orcapi.AppParameterValue{
					networkAttachment,
				}

				serviceForwardsTcp := "[6443,10250,10256,30500"
				/*for i := 30000; i <= 32767; i++ {
					serviceForwardsTcp += fmt.Sprintf(",%d", i)
				}*/
				serviceForwardsTcp += "]"

				vmId := ucxsvc.VirtualMachineCreate(stack, ucxsvc.VirtualMachineSpec{
					Labels: util.MapMerge(initWorkerLabels, map[string]string{
						"ucloud.dk/service-forward-tcp": serviceForwardsTcp,
						"ucloud.dk/service-forward-udp": "[51820,51821]",
						stackGroupingLabel:              nodeGroup,
					}),
					Product:     selectedProduct,
					Image:       ucxsvc.VmImageUbuntu24_04,
					Hostname:    fmt.Sprintf("worker-%v", i),
					Attachments: attachments,
				})
				ucxsvc.UiSendSuccess(app, "Created worker VM "+vmId+"!")
			}

			ucxsvc.StackConfirmAndOpen(stack)
		}),
	)
}

func (app *k8sApp) OnMessage(msg ucx.Frame) {}
