#!/bin/bash 

refreshToken=$(cat $(cat $HOME/sducloud/start-dependencies.yml | yq -r ".config.additionalDirectories[0]")/refresh.yml | yq -r ".refreshToken")
csrfToken=$(cat $(cat $HOME/sducloud/start-dependencies.yml | yq -r ".config.additionalDirectories[0]")/refresh.yml | yq -r ".devCsrfToken")

echo "document.cookie = \"refreshToken=$refreshToken;expires=\" + new Date(new Date().getTime() + 1000 * 60 * 60 * 24 * 365).toUTCString() + \";path=/;\""
echo "localStorage.setItem(\"csrfToken\", \"$csrfToken\")"
echo "localStorage.setItem(\"accessToken\", \"$(echo $ADMINTOK)\")"
echo "location.reload(true)"
