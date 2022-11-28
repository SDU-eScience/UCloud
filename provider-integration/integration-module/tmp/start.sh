cd $WORK_DIR
[ $DO_CLONE = "true" ] && git clone https://github.com/sdu-escience/ucloud
[ $START_FRONTEND = "true" ] && git clone https://github.com/sdu-escience/ucloud
[ $START_BACKEND = "true" ] && git clone https://github.com/sdu-escience/ucloud
[ $START_SSH_SERVER = "true" ] && service ssh start

caddy start --config /root/Caddyfile
sleep inf