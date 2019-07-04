It is possible to use normal CLI tools to make REST calls (like curl and httpie). However, this can sometimes be annoying since the access tokens only last for a few minutes. The following command line tool will automatically create a new access token when the old one has expired. The only dependency for the tool is `python3` and `curl`. You should create a file in `~/.sducloud/config.json` containing your refresh token in the following format: `{ "refreshToken": "<token>" }`. You can retrieve your refresh token by logging into the website and looking in storage -> cookies of the developer console. You will have to `decodeURI` the token.

Save the following tool as `sducloud` and put it in your path.

```python
#!/usr/bin/env python3
import json
import base64
import os
import time
import sys
import uuid
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import subprocess

if len(sys.argv) < 3:
    print("usage: sducloud <method> <path> [<curl args>]")
    print("example: sducloud GET /api/files?path=/home/user3@test.dk")
    exit(1)

sducloud = os.path.expanduser("~/.sducloud")
if not os.path.exists(sducloud):
    os.makedirs(sducloud)

host = "https://cloud.sdu.dk"
config_file = os.path.expanduser("~/.sducloud/config.json")
refresh_token = None
try:
    with open(config_file) as config_file_data:
        config_json = json.load(config_file_data)
        refresh_token = config_json["refreshToken"]
        if config_json["host"] != None:
            host = config_json["host"]
        config_file_data.close()
except:
    pass

if refresh_token == None:
    print("Found no refresh token in ~/.sducloud/refresh")
    print('It should contain: {"refreshToken": "<TOKEN>"}')
    exit(1)

cached_jwt_file = os.path.expanduser("~/.sducloud/jwt")
cached_jwt = None
try:
    with open(cached_jwt_file) as jwt_data:
        cached_jwt = json.load(jwt_data)["jwt"]
        jwt_data.close()
except:
    pass

try:
    if cached_jwt != None:
        # Check if jwt is valid
        payload = cached_jwt.split(".")[1]
        exp = json.loads(base64.b64decode(payload + '=' * (-len(payload) % 4)))["exp"]
        now = int(time.time()) + 30
        if now >= exp:
            cached_jwt = None
except:
    cached_jwt = None

def send_request(method, url, bearer_token, data = None):
    req = Request(url, method = method)
    req.add_header("Authorization", "Bearer {}".format(bearer_token))
    if data != None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
        jsondata = json.dumps(data)
        body_as_bytes = jsondata.encode("utf-8")
        req.add_header("Content-Length", len(body_as_bytes))
    return json.loads(urlopen(req).read().decode())

if cached_jwt == None:
    resp = send_request("POST", "{}/auth/refresh".format(host), refresh_token)
    cached_jwt = resp["accessToken"]
    with open(cached_jwt_file, "w") as outfile:
        json.dump({"jwt": cached_jwt}, outfile)

url = "{}{}".format(host, sys.argv[2])
if sys.argv[2].startswith("http"):
    url = sys.argv[2]

if sys.argv[2].startswith(":"):
    url = "http://localhost{}".format(sys.argv[2])


command = [
    "http",
    sys.argv[1],
    url,
    "Authorization: Bearer {}".format(cached_jwt),
    "Job-Id: {}".format(str(uuid.uuid4()))
] + sys.argv[3:]

p = subprocess.Popen(command)

p.communicate()
p.wait()
```