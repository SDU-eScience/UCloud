#!/usr/bin/env bash
unix_user_is_cloud() {
    local result=`id -Gn "${1}" | grep -c sftponly$`
    return `(( $result > 0 ))`
}