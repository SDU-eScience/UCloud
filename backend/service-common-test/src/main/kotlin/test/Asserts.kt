package dk.sdu.cloud.service.test

import kotlin.test.assertTrue

fun <T> assertCollectionHasItem(
    collection: Iterable<T>,
    description: String = "Custom matcher",
    matcher: (T) -> Boolean
) {
    assertTrue("Expected collection to contain an item that matches $description: $collection ") {
        collection.any(matcher)
    }
}

fun <T> assertThatInstance(
    instance: T,
    description: String = "Custom matcher",
    matcher: (T) -> Boolean
) {
    assertTrue("Expected instance to match $description. Actual value: $instance") { matcher(instance) }
}

fun <T, P> assertThatProperty(
    instance: T,
    property: (T) -> P,
    description: String = "Custom matcher",
    matcher: (P) -> Boolean
) {
    val prop = property(instance)
    assertTrue(
        "Expected instance's property to match $description." +
                "\n  Actual value: $instance." +
                "\n  Computed property: $prop"
    ) {
        matcher(prop)
    }
}

fun <T, P> assertThatPropertyEquals(
    instance: T,
    property: (T) -> P,
    value: P,
    description: String = "Custom matcher"
) {
    assertThatProperty(instance, property, description) { it == value }
}
