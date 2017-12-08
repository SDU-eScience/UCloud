# service-common

Utility library designed to make it easier to create services that fit into SDUCloud.

__Included features:__

  - Service discovery
  - Kafka utilities
    + Stream and table descriptions
    + Extension API for Kotlin
    + (De-)serialization helpers
  - Support library for creating `RESTCallDescription`s
  - Support library for registering Kafka services at Gateway
  
## Quick Start

First, add the internal Maven archiva to your `build.gradle`:

```groovy
maven {
    url("https://cloud.sdu.dk/archiva/repository/internal")
    credentials {
        username(eScienceCloudUser)
        password(eScienceCloudPassword)
    }
}
```

This will also require you to set `eScienceCloudUser` and `eScienceCloudPassword` in `~/.gradle/gradle.properties`. 
__DO NOT SET THESE DIRECTLY IN THE SOURCE CODE__

Then add a dependency on this library:

```groovy
compile "org.esciencecloud.service-common:0.4.0-SNAPSHOT"
```
    
## Exporting and Implementing REST Calls

All calls and associated types should be put into their own package. It is _very_ important that the amount of
dependencies inside of this artifact is heavily minimized. Remember, any dependencies you add in here will eventually
be loaded at the clients!

In this example we will show how to a simple CRUD (no updates) interface for the following data type:

```kotlin
package org.esciencecloud.animals.api // note the package

data class Animal(val name: String, val sound: String, val weightInKg: Int) {
    init {
        // Input validation is supported. Simply throw an IllegalArgumentException.
        // The input validation will automatically take place both at client and server
        if (weightInKg < 0) throw IllegalArgumentException("Weight cannot be negative!")
    }
}
```

Call descriptions go inside of a container, and are described using the `callDescription` method.

```kotlin
package org.esciencecloud.animals.api

data class FindAnimalByName(val name: String)
data class SimpleError(val why: String)

object AnimalDescriptions : RESTDescriptions() {
    val baseContext = "/api/animals"
    
    val findByName = callDescription<FindAnimalByName, Animal, SimpleError> {
        path {
            using(baseContext)
            +boundTo(FindAnimalByName::name)
        }
    }
}
```

TODO Describe how this works

```kotlin
val listAll = callDescription<Unit, List<Animal>, SimpleError> {
    path {
        using(baseContext)
    }
}
```

```kotlin
val deleteById = callDescription<FindAnimalByName, Animal, SimpleError> {
    method = HttpMethod.DELETE
    
    path {
        using(baseContext)
    }
}
```

```kotlin
val createAnimal = callDescription<Animal, Animal, SimpleError> {
    method = HttpMethod.POST
    
    path {
        using(baseContext)
    }
    
    body {
        bindEntireRequestFromBody()
    }
}
```

We support type safety and automatic input validation when using the `implement` function in ktor.

```kotlin
route("/api/animals") {
    implement(AnimalDescriptions.findByName) {
        val animal: Animal? = AnimalDAO.findByName(it.name)
        if (animal == null) error(SimpleError("Not found"), HttpStatusCode.NotFound)
        else ok(animal)
    }
    
    implement(AnimalDescriptions.createAnimal) { animal: Animal ->
        // Animal has already been validated
        try {
            ok(AnimalDAO.create(animal))
        } catch (ex: Exception) {
            // Catch different errors here, such as the entity already exists
            
            // NOTE: Input validation has already taken place, so animal is guaranteed to be valid.
            // This will automatically respond with HttpStatusCode.BadRequest if the data model is invalid
        }
    }
}
```

## Exporting and Implementing Kafka Calls

## Exporting and Registering API Artifact with Gateway