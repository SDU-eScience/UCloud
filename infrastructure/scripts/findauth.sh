#!/bin/bash 

if [[ -z "${ADMINTOK}" ]]
then
  ATOKEN="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbkBkZXYiLCJ1aWQiOjAsImxhc3ROYW1lIjoiRGV2IiwiYXVkIjoiYWxsOndyaXRlIiwicm9sZSI6IkFETUlOIiwiaXNzIjoiY2xvdWQuc2R1LmRrIiwiZmlyc3ROYW1lcyI6IkFkbWluIiwiZXhwIjoxNTg5ODY5NDM3LCJleHRlbmRlZEJ5Q2hhaW4iOltdLCJpYXQiOjE1NTgzMzM0MzcsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsInB1YmxpY1Nlc3Npb25SZWZlcmVuY2UiOiJiZjE5NzIwNy02MDNmLTRjMjUtYmE2Mi1lMWI4MjUwYWZiMWQifQ.27xbjXVIvXMFc22kWxXF1SYqIWBkC4j4BubqlZdHnp4rTvasLDobD8ClJFXJjGVY1QaoPVxyhkHaEuT0tk7Gow"
else
  ATOKEN="${ADMINTOK}"
fi

refreshToken=$(cat $(cat $HOME/sducloud/start-dependencies.yml | yq -r ".config.additionalDirectories[0]")/refresh.yml | yq -r ".refreshToken")
csrfToken=$(cat $(cat $HOME/sducloud/start-dependencies.yml | yq -r ".config.additionalDirectories[0]")/refresh.yml | yq -r ".devCsrfToken")

echo "document.cookie = \"refreshToken=$refreshToken;expires=\" + new Date(new Date().getTime() + 1000 * 60 * 60 * 24 * 365).toUTCString() + \";path=/;\""
echo "localStorage.setItem(\"csrfToken\", \"$csrfToken\")"
echo "localStorage.setItem(\"accessToken\", \"$(echo $ATOKEN)\")"
echo "location.reload(true)"
