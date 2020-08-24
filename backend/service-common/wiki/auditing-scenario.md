# Auditing Scenarios

The following UCloud usage scenarios are used to manually verify that the complete auditing stack works. This is
currently not machine tested.

## Internal Auditing: #1 File Activity

Placeholders:

- `$DATE` should be replaced with the current date (format YYYY.MM.DD)
- `$USERNAME` should be replaced with your username

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
GET /http_logs_files.upload.simpleupload-$DATE/_search
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}
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

Request #2:

```
GET /http_logs_files.createdirectory-$DATE/_search
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}
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

Request #3:

```
GET /http_logs_files.copy-$DATE/_search
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}
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
GET /http_logs_files.trash.trash-$DATE/_search
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}
```

Should contain:

```
"requestJson" : {
    "files" : [
        "/home/$USERNAME/Audit-$DATE/file"
    ]
}
```

---

Request #5:

```
GET /http_logs_files.move-$DATE/_search
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}
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

---

Request #5:

```
GET /http_logs_files.move-$DATE/_search
{
  "query": {
    "query_string": {
      "query": "token.principal.username:$USERNAME"
    }
  }
}
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


## Activity Feed: #1 File Activity

Follow the steps from "Internal Auditing: #1". It should produce the following entries when viewing the 'Activity'
feed visible on UCloud.

![](activity-feed.png)
