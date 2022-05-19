echo 'ucloud  ALL= NOPASSWD: /opt/ucloud/example-extensions/oidc-extension, /opt/ucloud/example-extensions/project-extension, /opt/ucloud/example-extensions/ucloud-connection' > /etc/sudoers.d/ucloud
/opt/ucloud/user-sync.py push /mnt/passwd &
chmod 755 /home
