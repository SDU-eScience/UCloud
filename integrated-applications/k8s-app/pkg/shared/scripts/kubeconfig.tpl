apiVersion: v1
kind: Config
clusters:
- name: default
  cluster:
    server: https://%s
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
