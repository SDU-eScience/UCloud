:orphan:

# Share Service

This service implements the share functionality for the UCloud file system. It
gives the user the ability to share files and directories with other users of
the file system. 

.. figure:: /backend/share-service/wiki/CreateAndAcceptShare.png
   :align: center
   :width: 80%

A share can be in four different states:
- `REQUEST_SENT`:
  The share request has been sent and is awaiting acceptance or denial.
- `ACCEPTED`:
  The share has been accepted and is an active share.
- `UPDATING`:
  This is a transition state. While the actions needed for a share to be 
  completely accepted or removed are in progress, the share is in the updating 
  state. 
