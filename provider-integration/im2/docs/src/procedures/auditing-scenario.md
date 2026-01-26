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
requesty_body: {"items": [{"id": "/CollectionID/Audit-$DATE/file.txt", "type": "FILE", "conflictPolicy": "RENAME", "supportedProtocols": ["CHUNKED", "WEBSOCKET"]}]}
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
requesty_body: {"id": "/CollectionID/Audit-$DATE", "path": null, "sortBy": null, "filterIds": null, ... }
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
requesty_body: {"items": [{"newId": "/CollectionID/Audit-$DATE/file.txt", "oldId": "/CollectionID/Audit-$DATE/file.txt", "conflictPolicy": "RENAME"}]}
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
requesty_body: {"items": [{"newId": "/CollectionID/Audit-$DATE/renamed.txt", "oldId": "/CollectionID/Audit-$DATE/file.txt", "conflictPolicy": "REJECT"}]}
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
requesty_body: {"items": [{"fileId": "/CollectionID/Audit-$DATE/renamed.txt", "metadata": {"version": "1.0.0", "document": {"favorite": true}, "changeLog": "New favorite status", "templateId": "1"}}]}
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
