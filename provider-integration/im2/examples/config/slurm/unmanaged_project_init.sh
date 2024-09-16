#!/usr/bin/env bash

echo "------------------------------------------------------------------------------------"
echo 'TODO Create user in IPA. This can be done manually from https://ipa.localhost.direct'
echo "------------------------------------------------------------------------------------"
sleep 2

username=$1
qos=standard
partition=normal
accountName="$username-acc"

mkdir /home/$username
chown $username:$username /home/$username
chmod 700 /home/$username

sacctmgr -i create account $accountName qos=$qos defaultqos=$qos
sacctmgr -i modify account $accountName set \
    maxjobs=-1 \
    grptresmins=billing=$resourceMinutes \
    fairshare=600000

sacctmgr -i add user $username account=$accountName
