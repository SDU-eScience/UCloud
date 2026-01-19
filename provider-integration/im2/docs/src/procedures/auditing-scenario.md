# Auditing scenario

The following UCloud usage scenarios are used to manually verify that the complete auditing stack works. This is
currently not machine tested.

## Internal Auditing: #1 File Activity

There has been created a users on the production system to help with this test:

- `audit1`

The person responsible for our logging architecture has the 2FA and other credentials to these users.

**Steps:**

1. Create a directory called `Audit-$DATE`
2. Upload a file called `file.txt` to the new directory
3. Check if other user can see the file (copy URL to of file location to other users browser)
4. Copy this file to the same directory using the rename strategy (default)
5. Move the new copy to the trash
6. Rename `file` to `renamed`
7. Mark `renamed` as a favorite file
8. Unmark `renamed` as a favorite file

## Internal Auditing: #2 Project Activity

This Audit requires 3 different users. There has been created additional 2 users on the production system to help with
this test:

- `audit2`
- `audit3`

The person responsible for our logging architecture has the 2FA and other credentials to these users.

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
