#Contact book Service

This service allows for services to save contacts between users when using specific services.
The contacts are service specific so if a service is created using e.g share-service it will
not show up when queried by other services. 

##Shares
When a share is created, a contact is created from sender to recipient. First when the recipient 
accepts the share a contact is created the other way around.

When typing the username of the upcoming recipient of the share, a search-while-typing is 
performed to suggest potential recipients based on previous shares.
