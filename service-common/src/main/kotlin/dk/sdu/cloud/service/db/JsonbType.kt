package dk.sdu.cloud.service.db

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.hibernate.collection.internal.PersistentList
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.DynamicParameterizedType
import org.hibernate.usertype.DynamicParameterizedType.PARAMETER_TYPE
import org.hibernate.usertype.UserCollectionType
import org.hibernate.usertype.UserType
import org.postgresql.util.PGobject
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.*

const val JSONB_TYPE = "dk.sdu.cloud.service.db.JsonbType"
const val JSONB_LIST_TYPE = "dk.sdu.cloud.service.db.JsonbCollectionType"

open class JsonbType : UserType, DynamicParameterizedType {
    private lateinit var klass: Class<*>

    override fun replace(original: Any?, target: Any?, owner: Any?): Any = deepCopy(original)
    override fun assemble(cached: Serializable?, owner: Any?): Any = deepCopy(cached)
    override fun disassemble(value: Any?): Serializable = deepCopy(value) as Serializable
    override fun deepCopy(value: Any?): Any {
        return mapper.readValue(mapper.writeValueAsString(value), klass)
    }

    override fun hashCode(x: Any?): Int {
        return x?.hashCode() ?: 0
    }


    override fun equals(x: Any?, y: Any?): Boolean {
        return x == y
    }

    override fun returnedClass(): Class<*> {
        return klass
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: Any?,
        index: Int,
        session: SharedSessionContractImplementor?
    ) {
        st.setObject(index, PGobject().apply {
            type = "json"
            if (value != null) this.value = mapper.writeValueAsString(value)
        })
    }

    override fun nullSafeGet(
        rs: ResultSet,
        names: Array<out String>,
        session: SharedSessionContractImplementor?,
        owner: Any?
    ): Any? {
        val rawValue = rs.getObject(names[0]) as? PGobject ?: return null
        return mapper.readValue(rawValue.value, createType())
    }

    override fun isMutable(): Boolean = true

    override fun sqlTypes(): IntArray = intArrayOf(Types.JAVA_OBJECT)

    override fun setParameterValues(parameters: Properties) {
        val type = parameters[PARAMETER_TYPE] as? DynamicParameterizedType.ParameterType ?: return
        klass = type.returnedClass
    }

    protected open fun createType(): JavaType {
        val erasedParameters = Array<JavaType>(klass.typeParameters.size) {
            mapper.typeFactory.constructSimpleType(
                Any::class.java,
                emptyArray()
            )
        }

        return mapper.typeFactory.constructSimpleType(klass, erasedParameters)
    }

    protected val mapper = jacksonObjectMapper()
}

class JsonbCollectionType : JsonbType(), UserCollectionType {
    override fun instantiate(
        session: SharedSessionContractImplementor?,
        persister: CollectionPersister?
    ): PersistentCollection = PersistentList(session)

    private fun cast(collection: Any): PersistentList = collection as PersistentList

    override fun wrap(session: SharedSessionContractImplementor?, collection: Any?): PersistentCollection =
        PersistentList(session, collection as List<*>)

    override fun getElementsIterator(collection: Any): Iterator<*> =
        cast(collection).iterator()

    override fun contains(collection: Any, entity: Any): Boolean = cast(collection).contains(entity)

    override fun indexOf(collection: Any, entity: Any): Any = cast(collection).indexOf(entity)

    override fun replaceElements(
        original: Any,
        target: Any,
        persister: CollectionPersister?,
        owner: Any?,
        copyCache: MutableMap<Any?, Any?>?,
        session: SharedSessionContractImplementor?
    ): Any? {
        val originalList = cast(original)
        val targetList = cast(target)
        targetList.clear()
        targetList.addAll(originalList)

        return target
    }

    override fun instantiate(anticipatedSize: Int): Any = PersistentList()

    override fun createType(): JavaType {
        return mapper.typeFactory.constructCollectionType(List::class.java, returnedClass())
    }
}