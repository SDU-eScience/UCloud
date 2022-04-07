mkdir -p /etc/ucloud

export UCLOUD_INTEGRATION_CFG=/etc/ucloud
export UCLOUD_INTEGRATION_PSQL=false
export UCLOUD_INTEGRATION_REDIS=false

cat > /etc/ucloud/config.yaml << EOF
database:
  hostname: localhost
  database: postgres
  port: 5432
  credentials:
    username: postgres
    password: $POSTGRES_PASSWORD

app:
  kubernetes:
    kubernetesConfig: /output/kubeconfig.yaml
EOF

# TODO Configure the Kubernetes cluster here

cd ./backend/
gradle integrationTest
