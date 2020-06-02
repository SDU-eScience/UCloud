# File Favorite Service

Manages the favorite status of user files. This is accomplished by using a the metadata feature of the 
[storage-service](../../file-storage-service.html).

Favoriting information is attached to many file related calls. This information is merged via the 
[file gateway](../file-gateway-service/README.html). The file gateway will query this service in bulk by file path.
