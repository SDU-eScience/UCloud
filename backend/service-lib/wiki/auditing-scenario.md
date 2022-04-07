The following UCloud usage scenarios are used to manually verify that the complete auditing stack works. This is
currently not machine tested.

## Internal Auditing: #1 File Activity

Placeholders:

- `$DATE` should be replaced with the current date (format YYYY.MM.DD)
- `$USERNAME` should be replaced with your username
- `$ELASTIC_USER` an admin user of the elastic cluster
- `$ELASTIC_PASSWORD` matching password of the admin user

Steps:

1. Create a directory called `Audit-$DATE`
2. Upload a file called `file` to the new directory
3. Copy this file to the same directory using the rename strategy
4. Move the new copy to the trash
5. Rename `file` to `renamed`
6. Mark `renamed` as a favorite file
7. Unmark `renamed` as a favorite file

Verification:

---
Request #1:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.upload.simpleupload-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:#USERNAME"
    }
  }
}'
```

Should contain:

```
"requestJson" : {
    "path" : "/home/$USERNAME/Audit-$DATE",
    "owner" : null,
    "sensitivity" : null
}
```

---
Request #2:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.createdirectory-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```

Should contain:

```
"requestJson" : {
    "request" : {
      "path" : "/home/$USERNAME/Audit-$DATE/file",
      "sensitivityLevel" : null,
      "owner" : "$USERNAME"
    }
}
```

---

Request #3:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.copy-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```

Should contain:

```
"requestJson" : {
    "request" : {
        "path" : "/home/$USERNAME/Audit-$DATE/file",
        "newPath" : "/home/$USERNAME/Audit-$DATE/file",
        "policy" : "RENAME"
    }
}
```

---

Request #4:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.trash.trash-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```

Should contain:

```
"requestJson" : {
    "files" : [
        "/home/$USERNAME/Audit-$DATE/file(1)"
    ]
}
```

---

Request #5:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.move-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```

Should contain:

```
"requestJson" : {
    "request" : {
        "path" : "/home/$USERNAME/Audit-$DATE/file",
        "newPath" : "/home/$USERNAME/Audit-$DATE/renamed",
        "policy" : null
    }
}
```

AND 

```
"requestJson" : {
    "request" : {
      "path" : "/home/$USERNAME/AUDIT-$DATE/file(1)",
      "newPath" : "/home/$USERNAME/Trash/file(1)",
      "policy" : "RENAME"
    }
  }
```

---

Request #6 (finds both the favorite and unfavorite):

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.favorite.togglefavorite-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```

Should contain __(TWICE)__:

```
"requestJson" : {
    "files" : [
        {
        "path" : "/home/$USERNAME/Audit-$DATE/renamed",
        "newStatus" : null
        }
    ]
}
```
---

## Internal Auditing: #2 Project Activity

This Audit requires 3 different users. 
There has been created 2 users on the production system to help with this test:
- `audit1`
- `audit2`

The person responsible for our logging architecture has the 2FA and other credentials to these users.

Placeholders:

- `$DATE` should be replaced with the current date (format YYYY.MM.DD)
- `$USERNAME` should be replaced with your username
- `$ELASTIC_USER` an admin user of the elastic cluster
- `$ELASTIC_PASSWORD` matching password of the admin user

Steps:

1. `$USERNAME` applies for a project called AUDITTEST-$DATE which is approved
2. `$USERNAME` invites `audit1`
3. `audit1` accepts the invite
4. `$USERNAME` upgrades `audit1` to admin
5. `audit1` invites `audit2` to the project
6. `audit2` accepts the invite
7. `audit2` uploads a file to his personal workspace called file.txt
8. `audit2` classifies the file as Sensitive
9. `audit2` moves the file to the project

Verification:

Be aware that the responses contain project IDs that changes for each test. These are noted as PROJECTID in the response

---

Request #1:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.create-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "requestJson.principalInvestigator:$USERNAME"
    }
  }
}'

```
Should contain:
```
"requestJson" : {
    "title" : "AUDITTEST-$DATE",
    "parent" : "PARENT_PROJECT_ID",
    "principalInvestigator" : "$USERNAME"
}
```
---
Request #2:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.invite-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```
Should contain:
```
"requestJson" : {
    "projectId" : PROJECTID,
    "usernames" : [
      "audit1"
    ]
},
```
---
Request #3:
```
 curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.acceptinvite-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:audit1"
    }
  }
}'
```

Should contain:
```
"requestJson" : {
    "projectId" : PROJECTID
},
```
---
Request #4:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.changeuserrole-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}'
```

Should contain:
```
"requestJson" : {
    "projectId" : PROJECTID,
    "member" : "audit1",
    "newRole" : "ADMIN"
}
```
---
Request #5: 
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.invite-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:audit1"
    }
  }
}
```

Should contain:
```
 "requestJson" : {
    "projectId" : PROJECTID,
    "usernames" : [
      "audit2"
    ]
}
```
---
Request #6:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.acceptinvite-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:audit2"
    }
  }
}
```

Should contain:
```
"requestJson" : {
    "projectId" : "f933c498-1f11-47d1-b701-760dfef0d548"
}
```
---
Request #7
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.upload.simpleupload-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:audit2"
    }
  }
}
```

Should contain:
```
"requestJson" : {
    "request" : {
      "path" : "/home/audit2/file.txt",
      "sensitivityLevel" : null,
      "owner" : "audit2"
    }
}
```
---
Request #8:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.reclassify-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
        "query": "token.principal.username:audit2"
    }
  }
}
```

Should contain:
```
"requestJson" : {
    "request" : {
        "path" : "/home/audit2/file.txt",
        "sensitivity" : "SENSITIVE"
    }
}
```
---
Request #9:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.move-$DATE/_search?pretty -d '
{
  "query": {
    "query_string": {
      "query": "token.principal.username:audit2"
    }
  }
}
```

Should contain:
```
"requestJson" : {
    "request" : {
      "path" : "/home/audit2/file.txt",
      "newPath" : "/projects/PROJECTID/Members' Files/audit2/file.txt",
      "policy" : "REJECT"
    }
}
```
---
