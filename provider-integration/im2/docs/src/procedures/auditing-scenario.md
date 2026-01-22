# Auditing scenario

The following UCloud usage scenarios are used to manually verify that the complete auditing stack works. This is
currently not machine tested.

### Setup

To complete these scenarios 3 different users are required. 
These have been created on the production system with following usernames

- `audit1`
- `audit2`
- `audit3`

The person responsible for performing the scenarios has the 2FA and other credentials to these users.

Before starting any of the scenarios it is advised to create the following alias in your terminal:

- `ELASTIC_USER`: Username for a privileged elastic user
- `ELASTIC_PASSWORD`: Password for a privileged elastic user
- `

These makes it possible to simply copy-paste the code snippets in the scenarios when pulling audit data from elasticsearch.
## Internal Auditing: #1 File Activity

To complete this scenarios use the following user is needed: 

- `audit1`

**Steps:**

1. Create a directory called `Audit-$DATE`
2. Upload a file called `file.txt` to the new directory
3. Check if other user can see the file (copy URL to of file location to other users browser)
4. Copy this file to the same directory (default)
5. Move the new copy to the trash
6. Rename `file` to `renamed`
7. Mark `renamed` as a favorite file
8. Unmark `renamed` as a favorite file

### Validation of audit trail


## Internal Auditing: #2 Project Activity

This Audit requires 3 different users. To complete this following three users is recommended:

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
