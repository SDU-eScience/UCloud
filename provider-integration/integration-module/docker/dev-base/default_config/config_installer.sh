if [ ! -f "/etc/ucloud/core.yaml" ]; then
  mkdir /etc/ucloud
  chmod 644 /etc/ucloud/*
  chown -R ucloud: /etc/ucloud/
  mkdir /var/log/ucloud
  chmod 777 /var/log/ucloud
  chown -R ucloud: /etc/ucloud/

  chown -R munge:munge /etc/munge
  service munge start 
  chown ucloud:ucloud ${DATA_MOUNT} 

  rm /etc/ucloud/config_installer.sh
fi

chmod +x /opt/ucloud/compose-init.sh
/opt/ucloud/compose-init.sh
service munge start 
