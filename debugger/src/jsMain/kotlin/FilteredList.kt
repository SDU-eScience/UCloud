package dk.sdu.cloud.debug

class FilteredList<E : Any>(private val capacity: Int) {
    val all = CircularList<E>(capacity)
    val filtered = CircularList<E>(capacity)
    private var _filterFunction: (E) -> Boolean = { true }
    var filterFunction: (E) -> Boolean
        get() = _filterFunction
        set(value) {
            _filterFunction = value
            reapplyFilter()
        }

    fun reapplyFilter() {
        filtered.clear()
        for (item in all) {
            if (!_filterFunction(item)) continue
            filtered.add(item)
        }
    }

    fun add(item: E) {
        all.add(item)
        if (_filterFunction(item)) {
            filtered.add(item)
        }
    }

    fun addAll(collection: Collection<E>) {
        all.addAll(collection)
        for (item in collection) {
            if (!_filterFunction(item)) continue
            filtered.add(item)
        }
    }

    fun clear() {
        all.clear()
        filtered.clear()
    }
}
