adduser dan --gecos "Dan,,," --disabled-password
adduser ucloud --system
addgroup ucloud
addgroup dan ucloud
apt update
apt install -y sudo
ln -s /opt/ucloud/integration-module/build/bin/native/debugExecutable/ucloud-integration-module.kexe /usr/bin/ucloud
apt install -y libjansson-dev libcurl4
echo "ucloud  ALL=(%ucloud) NOPASSWD: /usr/bin/ucloud, /opt/ucloud/integration-module/build/bin/native/debugExecutable/ucloud-integration-module.kexe" > /etc/sudoers.d/ucloud.conf
mkdir -p /var/run/ucloud/envoy
chown -R ucloud: /var/run/ucloud