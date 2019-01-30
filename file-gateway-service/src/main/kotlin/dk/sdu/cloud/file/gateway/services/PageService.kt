package dk.sdu.cloud.file.gateway.services

import dk.sdu.cloud.service.Page

fun <F, R> Page<F>.withNewItems(newItems: List<R>): Page<R> {
    assert(newItems.size == items.size)
    return Page(itemsInTotal, itemsPerPage, pageNumber, newItems)
}
