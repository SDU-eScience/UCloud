# file-favorite-service

Manages the favorite status of user files. This is accomplished by using a the metadata feature of the 
[storage-service](../storage-service).

Favoriting information is attached to many file related calls. This information is merged via the 
[file gateway](../file-gateway-service). The file gateway will query this service in bulk by file path.
