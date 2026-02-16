set -e

export VERSION=$(curl -s https://storage.googleapis.com/kubevirt-prow/release/kubevirt/kubevirt/stable.txt)
export ARCH=$(uname -s | tr A-Z a-z)-$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/')

sed -i 's|server: https://127\.0\.0\.1:6443|server: https://im2k3:6443|' "/mnt/k3s/kubeconfig.yaml" 2> /dev/null || true
export KUBECONFIG=/mnt/k3s/kubeconfig.yaml

if ! kubectl get kubevirt -n kubevirt kubevirt >/dev/null 2>&1; then
  kubectl create -f "https://github.com/kubevirt/kubevirt/releases/download/${VERSION}/kubevirt-operator.yaml"
  kubectl create -f "https://github.com/kubevirt/kubevirt/releases/download/${VERSION}/kubevirt-cr.yaml"
  kubectl create -f "https://github.com/kubevirt/containerized-data-importer/releases/download/v1.64.0/cdi-operator.yaml"
  kubectl create -f "https://github.com/kubevirt/containerized-data-importer/releases/download/v1.64.0/cdi-cr.yaml"

  kubectl patch kubevirt -n kubevirt kubevirt --type=merge \
    -p '{"spec":{"configuration":{"developerConfiguration":{"featureGates":["EnableVirtioFsStorageVolumes"]}}}}'
fi

curl -L -o virtctl https://github.com/kubevirt/kubevirt/releases/download/${VERSION}/virtctl-${VERSION}-${ARCH} &> /dev/null
install -m 0755 virtctl /usr/local/bin
rm -f virtctl

if [[ ! -f "/etc/ucloud/webhook.key" ]]; then
  CERT_DIR="/etc/ucloud"
  CA_KEY="${CERT_DIR}/webhook-ca.key"
  CA_CRT="${CERT_DIR}/webhook-ca.crt"

  SRV_KEY="${CERT_DIR}/webhook.key"
  SRV_CRT="${CERT_DIR}/webhook.crt"
  SRV_CSR="${CERT_DIR}/webhook.csr"

  OPENSSL_CNF="${CERT_DIR}/webhook-openssl.cnf"
  CA_BUNDLE_B64="${CERT_DIR}/caBundle.b64"

  mkdir -p "${CERT_DIR}"
  umask 077

  SERVICE_NAME="vmi-mutator"
  SERVICE_NAMESPACE="default"

  # Commonly used DNS SANs
  DNS_1="${SERVICE_NAME}"
  DNS_2="${SERVICE_NAME}.${SERVICE_NAMESPACE}"
  DNS_3="${SERVICE_NAME}.${SERVICE_NAMESPACE}.svc"
  DNS_4="${SERVICE_NAME}.${SERVICE_NAMESPACE}.svc.cluster.local"

  cat > "${OPENSSL_CNF}" <<EOF
[ req ]
default_bits       = 2048
prompt             = no
default_md         = sha256
distinguished_name = dn
req_extensions     = req_ext

[ dn ]
CN = ${DNS_3}

[ req_ext ]
subjectAltName = @alt_names

[ alt_names ]
DNS.1 = ${DNS_1}
DNS.2 = ${DNS_2}
DNS.3 = ${DNS_3}
DNS.4 = ${DNS_4}

[ v3_ca ]
basicConstraints = critical,CA:TRUE
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer

[ v3_server ]
basicConstraints = CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
EOF

  echo "Generating CA key and self-signed CA certificate..."
  openssl genrsa -out "${CA_KEY}" 2048
  openssl req -x509 -new -nodes -sha256 -days 3650 \
    -key "${CA_KEY}" \
    -out "${CA_CRT}" \
    -subj "/CN=ucloud-webhook-ca" \
    -extensions v3_ca \
    -config "${OPENSSL_CNF}"

  echo "Generating server key..."
  openssl genrsa -out "${SRV_KEY}" 2048

  echo "Generating server CSR..."
  openssl req -new -sha256 \
    -key "${SRV_KEY}" \
    -out "${SRV_CSR}" \
    -config "${OPENSSL_CNF}"

  echo "Signing server certificate with CA..."
  openssl x509 -req -sha256 -days 825 \
    -in "${SRV_CSR}" \
    -CA "${CA_CRT}" \
    -CAkey "${CA_KEY}" \
    -CAcreateserial \
    -out "${SRV_CRT}" \
    -extensions v3_server \
    -extfile "${OPENSSL_CNF}"

  echo "Writing base64 CA bundle for MutatingWebhookConfiguration..."
  base64 -w 0 "${CA_CRT}" > "${CA_BUNDLE_B64}"

  chmod 600 "${CA_KEY}" "${SRV_KEY}"
  chmod 644 "${CA_CRT}" "${SRV_CRT}" "${CA_BUNDLE_B64}"
  rm -f "${SRV_CSR}"

  echo "Done."
  echo "Server cert: ${SRV_CRT}"
  echo "Server key : ${SRV_KEY}"
  echo "CA cert    : ${CA_CRT}"
  echo "CA bundle b64 (for caBundle field): ${CA_BUNDLE_B64}"

  echo "Applying MutatingWebhookConfiguration..."

  CA_BUNDLE="$(base64 -w 0 /etc/ucloud/webhook-ca.crt)"

  cat > /tmp/hook.yml << EOF
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: vmi-fs-mutator
webhooks:
- admissionReviewVersions:
  - v1
  clientConfig:
    caBundle: ${CA_BUNDLE}
    service:
      name: vmi-mutator
      namespace: default
      path: /
      port: 59231
  failurePolicy: Fail
  matchPolicy: Equivalent
  name: vmi-mutator.default.svc
  namespaceSelector:
    matchLabels:
      kubernetes.io/metadata.name: ucloud-apps
  objectSelector:
    matchLabels:
      kubevirt.io: virt-launcher
  reinvocationPolicy: Never
  rules:
  - apiGroups:
    - ""
    apiVersions:
    - v1
    operations:
    - CREATE
    resources:
    - pods
    scope: '*'
  sideEffects: None
  timeoutSeconds: 5
EOF

  kubectl create -f /tmp/hook.yml

  cat > /tmp/pvc.yml << EOF
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: ucloud-config
  namespace: default
spec:
  capacity:
    storage: 1000Gi
  volumeMode: Filesystem
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  storageClassName: ""
  hostPath:
    path: "/etc/ucloud"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ucloud-config
  namespace: default
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: ""
  volumeName: ucloud-config
  resources:
    requests:
      storage: 1000Gi
EOF

  kubectl --kubeconfig /mnt/k3s/kubeconfig.yaml create -f /tmp/pvc.yml

  cat > /tmp/rbac.yml << EOF
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: dev-provider
  namespace: default

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: dev-provider-role
  namespace: ucloud-apps
rules:
- apiGroups:
  - ""
  resources:
  - pods
  - pods/log
  - pods/exec
  - services
  - events
  verbs:
  - '*'
- apiGroups:
  - networking.k8s.io
  resources:
  - networkpolicies
  verbs:
  - '*'
- apiGroups:
  - kubevirt.io
  - cdi.kubevirt.io
  resources:
  - '*'
  verbs:
  - '*'

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: dev-provider-role-binding
  namespace: ucloud-apps
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: dev-provider-role
subjects:
- kind: ServiceAccount
  name: dev-provider
  namespace: default

EOF

  kubectl --kubeconfig /mnt/k3s/kubeconfig.yaml create -f /tmp/rbac.yml

  cat > /tmp/deployment.yml << EOF
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vmi-mutator
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/instance: vmi-mutator
      app.kubernetes.io/name: vmi-mutator
  template:
    metadata:
      labels:
        app.kubernetes.io/instance: vmi-mutator
        app.kubernetes.io/name: vmi-mutator
      name: vmi-mutator
    spec:
      containers:
      - command:
        - /usr/bin/ucloud
        - vmi-mutator
        - --this-flag-is-ignored
        image: dreg.cloud.sdu.dk/ucloud/im2:2026.1.43-kubevirt1
        imagePullPolicy: IfNotPresent
        name: ucloud
        ports:
        - containerPort: 59231
          name: webhook
          protocol: TCP
        volumeMounts:
        - mountPath: /etc/ucloud
          name: config
      restartPolicy: Always
      serviceAccount: dev-provider
      serviceAccountName: dev-provider
      volumes:
      - name: config
        persistentVolumeClaim:
          claimName: ucloud-config

---
apiVersion: v1
kind: Service
metadata:
  name: vmi-mutator
  namespace: default
spec:
  internalTrafficPolicy: Cluster
  ports:
  - name: webhook
    port: 59231
    protocol: TCP
    targetPort: webhook
  selector:
    app.kubernetes.io/instance: vmi-mutator
    app.kubernetes.io/name: vmi-mutator
  sessionAffinity: None
  type: ClusterIP

EOF

  kubectl --kubeconfig /mnt/k3s/kubeconfig.yaml create -f /tmp/deployment.yml

  echo "MutatingWebhookConfiguration vmi-fs-mutator applied."
fi
