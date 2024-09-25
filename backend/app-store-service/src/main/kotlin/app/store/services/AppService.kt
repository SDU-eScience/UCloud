package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.store.api.*
import info.debatty.java.stringsimilarity.Cosine
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// Utilities
// =================================================================================================================
fun isPrivileged(actorAndProject: ActorAndProject): Boolean {
    return isPrivileged(actorAndProject.actor)
}

fun isPrivileged(actor: Actor): Boolean {
    return actor == Actor.System || ((actor as? Actor.User)?.principal?.role ?: Role.GUEST) in Roles.PRIVILEGED
}

// Internal storage classes
// =================================================================================================================
class InternalVersions {
    private val listOfVersions = AtomicReference<List<String>>(emptyList())
    fun get(): List<String> = listOfVersions.get()

    fun add(version: String) {
        while (true) {
            val old = get()
            val new = old + version
            if (listOfVersions.compareAndSet(old, new)) break
        }
    }
}

class InternalGroup(
    val id: Long,
    title: String,
    description: String,
    defaultFlavor: String?,
    logo: ByteArray?,
    applications: Set<NameAndVersion>,
    categories: Set<Int>,
    logoHasText: Boolean,
    colorRemappingLight: Map<Int, Int>?,
    colorRemappingDark: Map<Int, Int>?,
    curator: String
) {
    data class GroupDescription(
        val title: String,
        val description: String,
        val defaultFlavor: String?,
        @Suppress("ArrayInDataClass") val logo: ByteArray?,
        val applications: Set<NameAndVersion>,
        val categories: Set<Int>,
        val logoHasText: Boolean,
        val colorRemappingLight: Map<Int, Int>?,
        val colorRemappingDark: Map<Int, Int>?,
        val curator: String,

        // Pre-computed search indexes
        val titleSearchShingle: Map<String, Int>,
        val descriptionSearchShingle: Map<String, Int>,
    )

    private val ref = AtomicReference(
        GroupDescription(
            title,
            description,
            defaultFlavor,
            logo,
            applications,
            categories,
            logoHasText,
            colorRemappingLight,
            colorRemappingDark,
            curator,

            emptyMap(),
            emptyMap(),
        )
    )

    fun get() = ref.get()!!

    fun updateMetadata(
        title: String? = null,
        description: String? = null,
        defaultFlavor: String? = null,
        logo: ByteArray? = null,
        newLogoHasText: Boolean? = null,
        colorRemappingLight: Map<Int, Int>?,
        colorRemappingDark: Map<Int, Int>?,
    ) {
        while (true) {
            val oldRef = ref.get()

            val newTitle = title ?: oldRef.title
            val newDescription = description ?: oldRef.description

            val flavors = oldRef.applications.mapNotNull {
                applications[it]?.metadata?.flavorName
            }.joinToString(" ")

            val titleSearch = searchCosine.getProfile("$newTitle $flavors".lowercase())
            val descriptionSearch = searchCosine.getProfile(newDescription.lowercase())

            val newRef = oldRef.copy(
                title = newTitle,
                description = newDescription,
                defaultFlavor = defaultFlavor ?: oldRef.defaultFlavor,
                logo = if (logo?.size == 0) null else logo ?: oldRef.logo,
                logoHasText = newLogoHasText ?: oldRef.logoHasText,
                colorRemappingLight = colorRemappingLight,
                colorRemappingDark = colorRemappingDark,
                titleSearchShingle = titleSearch,
                descriptionSearchShingle = descriptionSearch,
            )
            if (ref.compareAndSet(oldRef, newRef)) break
        }
    }

    fun addApplications(newApplications: Set<NameAndVersion>) {
        while (true) {
            val oldRef = ref.get()

            val updatedApps = oldRef.applications + newApplications

            val flavors = updatedApps.mapNotNull {
                applications[it]?.metadata?.flavorName
            }.joinToString(" ")

            val titleSearchShingle = searchCosine.getProfile("${oldRef.title} $flavors".lowercase())

            val newRef = oldRef.copy(
                applications = updatedApps,
                titleSearchShingle = titleSearchShingle,
            )
            if (ref.compareAndSet(oldRef, newRef)) break
        }
    }

    fun removeApplications(appsToRemove: Set<NameAndVersion>) {
        while (true) {
            val oldRef = ref.get()

            val updatedApps = oldRef.applications - appsToRemove

            val flavors = updatedApps.mapNotNull {
                applications[it]?.metadata?.flavorName
            }.joinToString(" ")

            val titleSearchShingle = searchCosine.getProfile("${oldRef.title} $flavors".lowercase())

            val newRef = oldRef.copy(
                applications = updatedApps,
                titleSearchShingle = titleSearchShingle,
            )
            if (ref.compareAndSet(oldRef, newRef)) break
        }
    }

    fun addCategories(categoryIds: Set<Int>) {
        while (true) {
            val oldRef = ref.get()
            val newRef = oldRef.copy(
                categories = oldRef.categories + categoryIds
            )
            if (ref.compareAndSet(oldRef, newRef)) break
        }
    }

    fun removeCategories(categoryIds: Set<Int>) {
        while (true) {
            val oldRef = ref.get()
            val newRef = oldRef.copy(
                categories = oldRef.categories - categoryIds
            )
            if (ref.compareAndSet(oldRef, newRef)) break
        }
    }

    fun toApiModel(): ApplicationGroup {
        val info = ref.get()

        return ApplicationGroup(
            ApplicationGroup.Metadata(id.toInt()),
            ApplicationGroup.Specification(
                info.title,
                info.description,
                info.defaultFlavor,
                info.categories,
                ApplicationGroup.ColorReplacements(info.colorRemappingLight, info.colorRemappingDark),
                info.logoHasText,
                info.curator
            ),
            ApplicationGroup.Status(null)
        )
    }
}

class InternalAcl(acl: Set<EntityWithPermission>) {
    private val acl = AtomicReference(acl)

    fun get(): Set<EntityWithPermission> {
        return acl.get()
    }

    fun addEntries(entries: Set<EntityWithPermission>) {
        while (true) {
            val old = acl.get()
            val new = old + entries
            if (acl.compareAndSet(old, new)) break
        }
    }

    fun removeEntries(entries: Set<AccessEntity>) {
        while (true) {
            val old = acl.get()
            val new = old.filter { it.entity !in entries }.toSet()
            if (acl.compareAndSet(old, new)) break
        }
    }
}

class InternalCategory(
    title: String,
    groups: Set<Int>,
    priority: Int,
    curator: String? = null
) {
    private val title = AtomicReference(title)
    private val groups = AtomicReference(groups)
    private val priority = AtomicInteger(priority)
    private val curator = AtomicReference(curator)

    fun updateTitle(newTitle: String) {
        title.set(newTitle)
    }

    fun addGroup(set: Set<Int>) {
        while (true) {
            val old = groups.get()
            val new = old + set
            if (groups.compareAndSet(old, new)) break
        }
    }

    fun removeGroup(set: Set<Int>) {
        while (true) {
            val old = groups.get()
            val new = old - set
            if (groups.compareAndSet(old, new)) break
        }
    }

    fun updatePriority(value: Int) {
        priority.set(value)
    }

    fun title() = title.get()
    fun groups() = groups.get()
    fun priority() = priority.get()
    fun curator() = curator.get()
}

data class InternalCurator(
    val id: String,
    val canManageCatalog: Boolean,
    val projectId: String,
    val mandatedPrefix: String,
)

class InternalSpotlight(spotlight: Spotlight) {
    private val ref = AtomicReference(spotlight)

    fun get() = ref.get()

    fun update(transform: (Spotlight) -> Spotlight) {
        while (true) {
            val old = ref.get()
            val new = transform(old)
            if (ref.compareAndSet(old, new)) break
        }
    }
}

class InternalTopPicks(topPicks: List<TopPick>) {
    private val ref = AtomicReference(topPicks)

    fun get() = ref.get()

    fun update(transform: (List<TopPick>) -> List<TopPick>) {
        while (true) {
            val old = ref.get()
            val new = transform(old)
            if (ref.compareAndSet(old, new)) break
        }
    }
}

class InternalCarrousel(items: List<CarrouselItem>) {
    private val ref = AtomicReference(items)
    private val images = AtomicReference<List<ByteArray>>(emptyList())

    fun get() = ref.get()
    fun getImages() = images.get()

    fun update(transform: (List<CarrouselItem>) -> List<CarrouselItem>) {
        while (true) {
            val old = ref.get()
            val new = transform(old)
            if (ref.compareAndSet(old, new)) break
        }
    }

    fun updateImages(transform: (List<ByteArray>) -> List<ByteArray>) {
        while (true) {
            val old = images.get()
            val new = transform(old)
            if (images.compareAndSet(old, new)) break
        }
    }
}

class InternalStars(stars: Set<String>) {
    private val stars = AtomicReference(stars)

    fun toggle(application: String) {
        while (true) {
            val old = stars.get()
            val new = if (application in old) {
                old - application
            } else {
                old + application
            }

            if (stars.compareAndSet(old, new)) break
        }
    }

    fun set(isStarred: Boolean, application: String) {
        while (true) {
            val old = stars.get()
            val new = if (isStarred) {
                old + application
            } else {
                old - application
            }

            if (stars.compareAndSet(old, new)) break
        }
    }

    fun get() = stars.get()!!
}


// Introduction
// =====================================================================================================================
// Applications in UCloud are the key abstraction used for users to perform a unit of execution. An application
// describes the software package that will run the actual job. An application is an abstract concept which depend
// heavily on the service provider itself. This idea is best described through a number of examples:
//
// An application can be:
//
// - A container image + description of the command to run
// - A virtual machine base image (e.g. Ubuntu 22.04) + description of how to run it
// - A pre-installed application + description of the command to run
//
// As you can probably tell, an application tells the service provider how to load the appropriate software artifacts,
// and how to subsequently launch them with the user's input. Below is a short summary of the information contained in
// an application:
//
// - Metadata:      Every application is identified by a name and a version (see `NameAndVersion`)
// - Tool:          The tool is an abstraction which identifies the container image/VM base image/modules to load.
// - Parameters:    The parameters of an application is a description of the input parameters a user can supply
// - Invocation:    The invocation describes how to invoke the tool using the user input. For example, this can
//                  construct a specific command to run inside a container.
// - Control flags: Various control flags can change the behavior of applications, such as allowing certain resources
//                  to be attached to it (e.g. public links)
//
// Users can select applications from a catalog. The catalog uses a hierarchical structure for discovery:
//
// 1. Categories:   Categories are an overall selection of applications and typically describe a given field
//                  (e.g. Natural Sciences). They contain groups.
// 2. Groups:       Groups are collection of identical software, but in potentially differing configurations (e.g.
//                  VS Code for Java and VSCode for C++). Contains applications.
// 3. Applications: Applications describe a single piece of software in a specific application. Each application can
//                  have a flavor name (e.g. Java or C++)

val groupIdAllocatorForTestsOnly = AtomicInteger(0)
const val DESIRED_LOGO_WIDTH = 300


val searchCosine = Cosine()

fun Cosine.safeSimilarity(profileA: Map<String, Int>, profileB: Map<String, Int>): Double {
    if (profileA.isEmpty()) return 0.0
    if (profileB.isEmpty()) return 0.0
    return this.similarity(profileA, profileB)
}