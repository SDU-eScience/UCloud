<p align='center'>
<a href='/docs/developer-guide/core/monitoring/auditing.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/monitoring/dependencies.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring, Alerting and Procedures](/docs/developer-guide/core/monitoring/README.md) / Auditing Scenario
# Auditing Scenario

The following UCloud usage scenarios are used to manually verify that the complete auditing stack works. This is
currently not machine tested.

## Internal Auditing: #1 File Activity

There has been created a users on the production system to help with this test:
- `audit1`

The person responsible for our logging architecture has the 2FA and other credentials to these users.


Placeholders:

For easy use pof the following curl commands create the following variables in the terminal.
E.g on MacOS use 'export [variable_name]=[variable_value]'.

- `$DATE` should be replaced with the current date (format YYYY.MM.DD)
- `$USERNAME` should be replaced with your username. If using the user created for this purpose 
this should be audit1.
- `$ELASTIC_USER` an admin user of the elastic cluster
- `$ELASTIC_PASSWORD` matching password of the admin user

Steps:

1. Create a directory called `Audit-$DATE`
2. Upload a file called `file.txt` to the new directory
3. Copy this file to the same directory using the rename strategy (default)
4. Move the new copy to the trash
5. Rename `file` to `renamed`
6. Mark `renamed` as a favorite file
7. Unmark `renamed` as a favorite file

Verification:

---
Request #1:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.createupload-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
    "id" : "/RANDOM_ID/Audit-$DATE/file.txt",
    "supportedProtocols" : [
      "CHUNKED"
    ],
    "conflictPolicy" : "RENAME"
}
```

---
Request #2:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.createfolder-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
  "items" : [
    {
      "id" : "/RANDOM_ID/Audit-$DATE",
      "conflictPolicy" : "RENAME"
    }
  ]
}
```

---

Request #3:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.copy-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
  "items" : [
    {
      "oldId" : "/RANDOM_ID/Audit-$DATE/file.txt",
      "newId" : "/RANDOM_ID/Audit-$DATE/file.txt",
      "conflictPolicy" : "RENAME"
    }
  ]
}
```

---

Request #4:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.trash-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
  "items" : [
    {
      "id" : "/RANDOM_ID/Audit-$DATE/file(1).txt"
    }
  ]
}
```

---

Request #5:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.move-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
  "items" : [
    {
      "oldId" : "/RANDOM_ID/Audit-$DATE/file.txt",
      "newId" : "/RANDOM_ID/Audit-$DATE/renamed.txt",
      "conflictPolicy" : "REJECT"
    }
  ]
}
```

---

Request #6:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.metadata.create-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
  "items" : [
    {
      "fileId" : "/RANDOM_ID/Audit-$DATE/renamed.txt",
      "metadata" : {
        "templateId" : "4",
        "version" : "1.0.0",
        "document" : {
          "favorite" : true
        },
        "changeLog" : "New favorite status"
      }
    }
  ]
}
```
---

Request #7:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.metadata.delete-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME\"
    }
  }
}"
```

Should contain:

```
"requestJson" : {
  "items" : [
    {
      "id" : "ID",
      "changeLog" : "Remove favorite"
    }
  ]
}
```
---

The metadata related to favorite can also be seen by the users through the properties page of the file.

## Internal Auditing: #2 Project Activity

This Audit requires 3 different users. 
There has been created additional 2 users on the production system to help with this test:
- `audit2`
- `audit3`

The person responsible for our logging architecture has the 2FA and other credentials to these users.

Placeholders:

- `$DATE` should be replaced with the current date (format YYYY.MM.DD)
- `$USERNAME1` should be replaced with your username. If using the users created for this purpose this
should be audit1.
- `$USERNAME2` should be replaced with a second user. If using the users created for this purpose this
    should be audit2.
- `$USERNAME3` should be replaced with a third user. If using the users created for this purpose this
    should be audit3.
- `$ELASTIC_USER` an admin user of the elastic cluster
- `$ELASTIC_PASSWORD` matching password of the admin user

Steps:

1. `audit1` applies for a project called AUDITTEST-$DATE which is approved
2. `audit1` invites `audit2`
3. `audit2` accepts the invite
4. `audit1` upgrades `audit2` to admin
5. `audit2` invites `audit3` to the project
6. `audit3` accepts the invite
7. `audit3` uploads a file to his personal workspace called file.txt
8. `audit3` classifies the file as Sensitive
9. `audit3` moves the file to the project

Verification:

Be aware that the responses contain project IDs that changes for each test. These are noted as PROJECTID in the response

---

Request #1:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_project.create-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"requestJson.principalInvestigator:$USERNAME1\"
    }
  }
}"

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

