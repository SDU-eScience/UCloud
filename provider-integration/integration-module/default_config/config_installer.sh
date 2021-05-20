if [ ! -f "/etc/ucloud/core.json" ]; then
  cp /opt/ucloud-default-config/* /etc/ucloud
  chmod 644 /etc/ucloud/*
  chown -R ucloud: /etc/ucloud/
  chmod 600 /etc/ucloud/server.json
  rm /etc/ucloud/config_installer.sh
fi
