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
- `$USERNAME1` should be replaced with your username. If using the user created for this purpose 
this should be audit1.
- `$USERNAME2` should be replaced with a second username. If using the user created for this purpose 
this should be audit2
- `$ELASTIC_USER` an admin user of the elastic cluster
- `$ELASTIC_PASSWORD` matching password of the admin user

Steps:

1. Create a directory called `Audit-$DATE`
2. Upload a file called `file.txt` to the new directory
3. Check if other user can see the file (copy URL to of file location to other users browser)
4. Copy this file to the same directory using the rename strategy (default)
5. Move the new copy to the trash
6. Rename `file` to `renamed`
7. Mark `renamed` as a favorite file
8. Unmark `renamed` as a favorite file

Verification:

---

Request #1:

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
Request #2:

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

Request #3:

Collection level
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.collections.retrieve-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:

```
        "requestJson" : {
            "flags" : {
              .
              .
              .
            },
            "id" : "43430"
          },
          "responseCode" : 404,

```
---
Folder level:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.retrieve-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:

```
   "requestJson" : {
            "flags" : {
              .
              .
              .
            },
            "id" : "/43430/Mojn"
          },
          "responseCode" : 400,


```

---

Request #4:

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

Please note that the name is the same. The request we send is old path -> new path. It is 
only after the backend has received the request that we find out there is a conflict. 
We then use the conflictPolicy to decide what to do. In this case we rename it automatically
by providing a (1), (2) etc. to the file name

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
9. `audit2` creates a group with audit3 in it
10. `audit2` creates a drive in the project with read permissions to the new group
11. `audit3` attempts to move file to read only folder and fails
12. `audit2` changes permissions to write
13. `audit3` moves the file to new drive in the project

Verification:

Be aware that the responses contain project IDs that changes for each test. These are noted as PROJECTID in the response

---

Request #1:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_notifications.create-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"requestJson.user:$USERNAME1\"
    }
  }
}"

```
Should contain:
```
"requestJson" : {
    "user" : "audit1",
    "notification" : {
      "type" : "GRANT_APPLICATION_RESPONSE",
      "message" : "Grant application updated (Approved)",
      "id" : null,
      "meta" : {
        "grantRecipient" : {
          "type" : "newProject",
          "title" : "AUDITTEST-$DATE"
        },
        "appId" : APPLICATION_ID
      },
      "ts" : 1672876712302,
      "read" : false
    }
}
```
---
Request #2:

```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.createinvite-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME1\"
    }
  }
}"
```
Should contain:
```
"requestJson" : {
  "items" : [
    {
      "recipient" : "$USERNAME2"
    }
  ]
}
```
---
Request #3:
```
 curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.acceptinvite-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "project" : "PROJECTID"
    }
  ]
},
```
---
Request #4:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.changerole-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME1\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "username" : "$USERNAME2",
      "role" : "ADMIN"
    }
  ]
}
```
---
Request #5: 
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.createinvite-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "recipient" : "$USERNAME3"
    }
  ]
}
```
---
Request #6:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.acceptinvite-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME3\"
    }
  }
}
"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
     "project" : "PROJECTID"
    } 
  ]
}
```
---
Request #7
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.createupload-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME3\"
    }
  }
}
"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "id" : "/RANDOMID/file.txt",
      "supportedProtocols" : [
        "CHUNKED"
      ],
      "conflictPolicy" : "RENAME"
    }
  ]
}
```
---
Request #8:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.metadata.create-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
        \"query\": \"token.principal.username:$USERNAME3\"
    }
  }
}
"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "fileId" : "/RANDOMID/file.txt",
      "metadata" : {
        "templateId" : "5",
        "version" : "1.0.0",
        "document" : {
          "sensitivity" : "SENSITIVE"
        },
        "changeLog" : "WHAT EVER WE WROTE IN COMMENT"
      }
    }
  ]
}
```
---
Request #9:

Group Creation:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.creategroup-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
    "items" : [
      {
        "project" : "PROJECTID",
        "title" : GROUPNAME"
      }
    ]
  }
```

Adding Member:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_projects.v2.creategroupmember-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
    "items" : [
      {
        "username" : "audit3",
        "group" : GROUPID
      }
    ]
  },
```
---

Request #9:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.move-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME3\"
    }
  }
}
"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "oldId" : "/RANDOMID/file.txt",
      "newId" : "/OTHER_RANDOMID/file.txt",
      "conflictPolicy" : "RENAME"
    }
  ]
}
```
---

Request #10:
Drive creation:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.collections.create-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
    "items" : [
      {
        "title" : "Newtest",
        "product" : {
          "id" : "u1-cephfs",
          "category" : "u1-cephfs",
          "provider" : "ucloud"
        }
      }
    ]
  }
```

Permission setting: 
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.collections.updateacl-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
    "items" : [
      {
        "id" : "RANDOMID",
        "added" : [
          {
            "entity" : {
              "type" : "project_group",
              "projectId" : PROJECTID,
              "group" : GROUPID
            },
            "permissions" : [
              "READ"
            ]
          }
        ],
        "deleted" : [
          {
            "type" : "project_group",
            "projectId" : PROJECTID",
            "group" : GROUPID
          }
        ]
      }
    ]
  },

```
---

Request #11:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.move-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME3\"
    }
  }
}
"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "oldId" : "/RANDOMID/file.txt",
      "newId" : "/OTHER_RANDOMID/file.txt",
      "conflictPolicy" : "RENAME"
    }
  ]
}
"responseCode" : 400,

```
---

Request #12:
Permission setting:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.collections.updateacl-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME2\"
    }
  }
}"
```

Should contain:
```
"requestJson" : {
    "items" : [
      {
        "id" : "RANDOMID",
        "added" : [
          {
            "entity" : {
              "type" : "project_group",
              "projectId" : PROJECTID",
              "group" : GROUPID
            },
            "permissions" : [
              "READ",
              "EDIT"
            ]
          }
        ],
        "deleted" : [
          {
            "type" : "project_group",
            "projectId" : PROJECTID",
            "group" : GROUPID
          }
        ]
      }
    ]
  },

```
---

Request #13:
```
curl -u $ELASTIC_USER:$ELASTIC_PASSWORD -H "Content-type:application/json" localhost:9200/http_logs_files.move-$DATE/_search?pretty -d "
{
  \"query\": {
    \"query_string\": {
      \"query\": \"token.principal.username:$USERNAME3\"
    }
  }
}
"
```

Should contain:
```
"requestJson" : {
  "items" : [
    {
      "oldId" : "/RANDOMID/file.txt",
      "newId" : "/OTHER_RANDOMID/file.txt",
      "conflictPolicy" : "RENAME"
    }
  ]
}
"responseCode" : 200,

```
---
