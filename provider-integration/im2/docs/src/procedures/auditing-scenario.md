# Auditing scenario

The following UCloud usage scenarios are used to manually verify that the complete auditing stack works. This is
currently not machine tested.

### Setup

To complete these scenarios three different users are required. 
These have been created on the production system with following usernames

- `audit1`
- `audit2`
- `audit3`

The person responsible for performing the scenarios has the 2FA and other credentials to these users. 
The person should also have admin rights to a grant giver so that they are able to grant resources.

It is advised to make sure that each user has active storage resource allocations 
available before starting the scenarios.

In the following scenarios `$DATE` refers to today's date in the format `DD-MM-YYYY`.

Read access to the `audit_logs` table in the `ucloud_core` postgres DB is needed to access the audit logs.

## Internal Auditing: #1 File Activity

To complete this scenario the following users is needed: 

- `audit1`
- `audit2`

All steps are done in a personal workspace context

**Steps:**

1. Create a directory called `Audit-$DATE`
2. Upload a file called `file.txt` to the new directory
3. Check if other user (`audit2`) can see the file (copy URL to of file location to other users browser)
4. Copy this file to the same directory
5. Move the new copy to the trash
6. Rename `file` to `renamed`
7. Mark `renamed` as a favorite file
8. Unmark `renamed` as a favorite file

### Validation of audit trail

This section covers how to validate each step of the above scenario

1. Directory was created

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.createFolder'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"id": "/CollectionID/Audit-$DATE", "conflictPolicy": "RENAME"}]}
username: audit1
```

2. File uploaded

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.createUpload'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"id": "/CollectionID/Audit-$DATE/file.txt", "type": "FILE",...]}
username: audit1
```

3. Attempted Access by other user (`audit2`)

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.retrieve'
    AND username = 'audit2';
```

Should return an entry with the following info
```
requesty_body: {"id": "/CollectionID/Audit-$DATE", ... }
username: audit2
response_code: 404
```

4. Copy of file

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.copy'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{
    "newId": "/CollectionID/Audit-$DATE/file.txt", 
    "oldId": "/CollectionID/Audit-$DATE/file.txt", 
    "conflictPolicy": "RENAME"}]}
username: audit1
```

Please note that the name is the same. The request we send is old path -> new path. It is
only after the backend has received the request that we register a conflict.
We then use the conflictPolicy to decide what to do. In this case we rename it automatically
by providing a (1), (2) etc. to the file name

5. Moving file to trash

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.trash'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"id": "/CollectionID/Audit-$DATE/file(1).txt"}]}
username: audit1
```

6. Renaming of file

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.move'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{
    "newId": "/CollectionID/Audit-$DATE/renamed.txt", 
    "oldId": "/CollectionID/Audit-$DATE/file.txt",
     "conflictPolicy": "REJECT"}
 ]}
username: audit1
```

7. Favorite the file

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.metadata.create'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{
    "fileId": "/CollectionID/Audit-$DATE/renamed.txt", 
    "metadata": {"version": "1.0.0", "document": {"favorite": true}, 
    "changeLog": "New favorite status", "templateId": "1"}}
]}
username: audit1
```
8. Unfavorite the file

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.metadata.delete'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"id": "favorite \n /CollectionID/Audit-$DATE/renamed.txt"}]}
username: audit1
```


## Internal Auditing: #2 Project Activity

This Audit requires 3 different users. To complete this scenario the following users is needed:

- `audit1`
- `audit2`
- `audit3`

**Steps:**

1. `audit1` applies for a project called AUDITTEST-$DATE 
2. Provider accepts the application for resources
3. `audit1` invites `audit2`
4. `audit2` accepts the invite
5. `audit1` upgrades `audit2` to admin
6. `audit2` invites `audit3` to the project
7. `audit3` accepts the invite
8. `audit3` uploads a file to his personal workspace called file.txt
9. `audit3` classifies the file as Sensitive
10. `audit2` creates a group called `auditGroup`
11. `audit2` adds `audit3` to the new group
11. `audit2` creates a drive called `auditDrive` in the project with read permissions to the new group
12. `audit3` attempts to move file to read only folder and fails
13. `audit2` changes permissions to write
14. `audit3` moves the file to new drive in the project

### Validation of audit trail

This section covers how to validate each step of the above scenario

1. `audit1` applies for a project called AUDITTEST-$DATE

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'grants.v2.submitRevision'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: 
    {"comment": "Submitted the application", 
        "revision": {
            "form": {
                "text": ... 
                "recipient": {
                    "id": null, 
                    "type": "newProject", 
                    "title": "AUDITTEST-$DATE", 
                    "username": null
                }, ...
            }
        }
    }    
username: audit1
```

2. Provider accepts the application for resources

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'grants.v2.updateState'
    AND username = :grant_giver_username;
```

Should return an entry with the following info
```
requesty_body: {"newState": "APPROVED", "applicationId": $GrantID}  
```

3. `audit1` invites `audit2`

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.createInvites'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"recipient": "audit2"}]}
username: audit1
project_id: $PROJECT_ID  
```

4. `audit2` accepts the invite

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.acceptInvite'
    AND username = 'audit2';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"project": "$PROJECTID"}]}
username: audit2
```

5. `audit1` upgrades `audit2` to admin

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.changeRole'
    AND username = 'audit1';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"role": "ADMIN", "username": "audit2"}]}
username: audit1
project_id: $PROJECT_ID  
```

6. `audit2` invites `audit3` to the project

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.createInvites'
    AND username = 'audit2';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"recipient": "audit3"}]}
username: audit2
project_id: $PROJECT_ID  
```

7. `audit3` accepts the invite

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.acceptInvite'
    AND username = 'audit3';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"project": "$PROJECTID"}]}
username: audit3
```

8. `audit3` uploads a file to his personal workspace called file.txt

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.createUpload'
    AND username = 'audit3';
```

Should return an entry with the following info
```
requesty_body: {"items": [{"id": "/CollectionID/file.txt", "type": "FILE", ... ]}
username: audit3
```

9. `audit3` classifies the file as Sensitive

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.metadata.create'
    AND username = 'audit3';
```

Should return an entry with the following info
```
requesty_body: {"items": [{
    "fileId": "/CollectionID/file.txt", 
    "metadata": {
        "version": "1.0.0", "document": {"sensitivity": "SENSITIVE"}, 
        "changeLog": "new", "templateId": "2"
    }
}]}
username: audit3
```

10. `audit2` creates a group called `auditGroup`

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.createGroups'
    AND username = 'audit2';
```

Should return an entry with the following info
```
request_body: {"items": [{
    "title": "auditGroup", 
    "project": $PROJECT_ID"
}]}
username: audit2
project_id: $PROJECT_ID
```

11. `audit2` adds `audit3` to the new group

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'projects.v2.createGroups'
    AND username = 'audit2';
```

Should return an entry with the following info
```
request_body: {"items": [{
    "group": $GROUP_ID,
     "username": "audit3"
 }]}
username: audit2
project_id: $PROJECT_ID
```

11. `audit2` creates a drive called `auditDrive` in the project with read permissions to the new group

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.collections.create'
    AND username = 'audit2';
```

Should return an entry with the following info
```
request_body: {"items": [{
    "title": "auditDrive", 
    "product": {"id": "storage", "category": "storage", "provider": "k8s"}
}]}
username: audit2
project_id: $PROJECT_ID
```

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.collections.updateAcl'
    AND username = 'audit2';
```

Should return an entry with the following info
```
request_body: {"items": [{
    "id": "11", "added": [{
        "entity": {
            "type": "project_group", 
            "group": $GROUP_ID, 
            "projectId": $PROJECT_ID
        }, "permissions": ["READ"]}],
    ...
]}
username: audit2
project_id: $PROJECT_ID
```


12. `audit3` attempts to move file to read only folder and fails

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.move'
    AND username = 'audit3';
```

Should return an entry with the following info
```
requesty_body: {"items": [{
    "newId": "/CollectionID/file.txt", 
    "oldId": "/CollectionID/file.txt", 
    "conflictPolicy": "RENAME"}
]}
username: audit3
response_code: 404
```

13. `audit2` changes permissions to write

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.collections.updateAcl'
    AND username = 'audit2';
```

Should return an entry with the following info
```
request_body: {"items": [{
    "id": "11", "added": [{
        "entity": {
            "type": "project_group", 
            "group": $GROUP_ID, 
            "projectId": $PROJECT_ID
        }, "permissions": ["READ", "EDIT"]}],
    ...
]}
username: audit2
project_id: $PROJECT_ID
```

14. `audit3` moves the file to new drive in the project

Query:
```
SELECT *
FROM audit_logs.logs
WHERE request_name = 'files.move'
    AND username = 'audit3';
```

Should return an entry with the following info
```
requesty_body: {"items": [{
    "newId": "/CollectionID/file.txt", 
    "oldId": "/CollectionID/file.txt", 
    "conflictPolicy": "RENAME"}
]}
username: audit3
response_code: 200
```