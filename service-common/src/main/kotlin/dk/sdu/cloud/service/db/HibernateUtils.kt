package dk.sdu.cloud.service.db

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import org.hibernate.Session
import org.hibernate.query.Query
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.hibernate.tool.schema.TargetType
import java.io.Serializable
import java.nio.file.Files
import java.util.*
import javax.persistence.criteria.*
import kotlin.reflect.KProperty1

@Suppress("unused")
interface HibernateEntity<E>

@Suppress("unused")
interface WithId<Id : Serializable>

inline fun <reified E> Session.typedQuery(query: String): org.hibernate.query.Query<E> {
    return createQuery(query, E::class.java)
}

inline fun <reified E> HibernateEntity<E>.list(
    session: Session,
    paginationRequest: NormalizedPaginationRequest? = null
): List<E> {
    val query = session.typedQuery<E>("from ${E::class.java.simpleName}")
    return if (paginationRequest != null) {
        query.paginatedList(paginationRequest)
    } else {
        query.list()
    }
}

fun <T> Query<T>.paginatedList(paginationRequest: NormalizedPaginationRequest): List<T> {
    with(paginationRequest) {
        maxResults = itemsPerPage
        firstResult = page * itemsPerPage
    }
    return list()
}

inline operator fun <F, reified E, reified Id : Serializable> F.get(
    session: Session,
    id: Id
): E? where F : HibernateEntity<E>, F : WithId<Id> {
    return findById(session, id)
}


inline fun <F, reified E, reified Id : Serializable> F.findById(
    session: Session,
    id: Id
): E? where F : HibernateEntity<E>, F : WithId<Id> {
    return session[E::class.java, id]
}


operator fun <X, Y> Path<X>.get(prop: KProperty1<X, Y>): Path<Y> {
    return get<Y>(prop.name)
}

class CriteriaBuilderContext<R, T>(
    val builder: CriteriaBuilder,
    val criteria: CriteriaQuery<R>,
    val entity: Root<T>
) {
    infix fun <E> Expression<E>.equal(value: E): Predicate {
        return builder.equal(this, value)
    }

    infix fun <E> Expression<E>.equal(value: Expression<E>): Predicate {
        return builder.equal(this, value)
    }

    infix fun <E> Expression<E>.notEqual(value: E): Predicate {
        return builder.notEqual(this, value)
    }

    infix fun <E> Expression<E>.notEqual(value: Expression<E>): Predicate {
        return builder.notEqual(this, value)
    }

    inline infix fun <reified E : Comparable<E>> Expression<E>.greaterThan(value: E): Predicate {
        return if (isNumberType(E::class.java)) {
            @Suppress("UNCHECKED_CAST")
            builder.gt(this as Expression<out Number>, value as Number)
        } else {
            builder.greaterThan(this, value)
        }
    }

    inline infix fun <reified E : Comparable<E>> Expression<E>.lessThan(value: E): Predicate {
        return if (isNumberType(E::class.java)) {
            @Suppress("UNCHECKED_CAST")
            builder.lt(this as Expression<out Number>, value as Number)
        } else {
            builder.lessThan(this, value)
        }
    }

    inline infix fun <reified E : Comparable<E>> Expression<E>.greaterThan(value: Expression<E>): Predicate {
        return if (isNumberType(E::class.java)) {
            @Suppress("UNCHECKED_CAST")
            builder.gt(this as Expression<out Number>, value as Expression<out Number>)
        } else {
            builder.greaterThan(this, value)
        }
    }

    inline infix fun <reified E : Comparable<E>> Expression<E>.lessThan(value: Expression<E>): Predicate {
        return if (isNumberType(E::class.java)) {
            @Suppress("UNCHECKED_CAST")
            builder.lt(this as Expression<out Number>, value as Expression<out Number>)
        } else {
            builder.lessThan(this, value)
        }
    }

    fun <E> ascending(expression: Expression<E>): Order {
        return builder.asc(expression)
    }

    fun <E> descinding(expression: Expression<E>): Order {
        return builder.desc(expression)
    }

    fun <E : Number> average(expression: Expression<E>): Expression<Double> {
        return builder.avg(expression)
    }

    fun <E : Number> sum(expression: Expression<E>): Expression<E> {
        return builder.sum(expression)
    }

    fun <E : Number> max(expression: Expression<E>): Expression<E> {
        // TODO These should handle both max and "greatest"
        return builder.max(expression)
    }

    fun <E : Number> min(expression: Expression<E>): Expression<E> {
        // TODO These should handle both min and "least"
        return builder.min(expression)
    }

    fun <E> count(expression: Expression<E>, distinct: Boolean = false): Expression<Long> {
        return if (!distinct) builder.count(expression) else builder.countDistinct(expression)
    }

    // TODO subqueries. need an additional builder for these

    fun not(expression: Expression<Boolean>): Predicate {
        return builder.not(expression)
    }

    infix fun Predicate.and(other: Predicate): Predicate {
        return builder.and(this, other)
    }

    fun allOf(vararg predicates: Predicate): Predicate {
        return builder.and(*predicates)
    }

    infix fun Predicate.or(other: Predicate): Predicate {
        return builder.or(this, other)
    }

    fun anyOf(vararg predicates: Predicate): Predicate {
        return builder.or(*predicates)
    }

    fun <E> isNull(expression: Expression<E>): Predicate {
        return builder.isNull(expression)
    }

    fun <E> isNotNull(expression: Expression<E>): Predicate {
        return builder.isNotNull(expression)
    }

    fun <E : Number> negate(expression: Expression<E>): Expression<E> {
        return builder.neg(expression)
    }

    fun <E : Number> absolute(expression: Expression<E>): Expression<E> {
        return builder.abs(expression)
    }

    infix fun <E : Number> Expression<E>.add(value: E): Expression<E> {
        return builder.sum(this, value)
    }

    infix fun <E : Number> Expression<E>.add(value: Expression<E>): Expression<E> {
        return builder.sum(this, value)
    }

    infix fun <E : Number> Expression<E>.multiply(value: E): Expression<E> {
        return builder.prod(this, value)
    }

    infix fun <E : Number> Expression<E>.multiply(value: Expression<E>): Expression<E> {
        return builder.prod(this, value)
    }

    infix fun <E : Number> Expression<E>.subtract(value: E): Expression<E> {
        return builder.diff(this, value)
    }

    infix fun <E : Number> Expression<E>.subtract(value: Expression<E>): Expression<E> {
        return builder.diff(this, value)
    }

    infix fun <E : Number> Expression<E>.integerDivision(value: E): Expression<Number> {
        return builder.quot(this, value)
    }

    infix fun <E : Number> Expression<E>.integerDivision(value: Expression<E>): Expression<Number> {
        return builder.quot(this, value)
    }

    infix fun <E : Number> Number.integerDivision(value: Expression<E>): Expression<Number> {
        return builder.quot(this, value)
    }

    infix fun Expression<Int>.mod(value: Int): Expression<Int> {
        return builder.mod(this, value)
    }

    infix fun Expression<Int>.mod(value: Expression<Int>): Expression<Int> {
        return builder.mod(this, value)
    }

    infix fun Int.mod(value: Expression<Int>): Expression<Int> {
        return builder.mod(this, value)
    }

    infix fun <E> Expression<E>.isInCollection(expression: Collection<E>): Predicate {
        return if (expression.isEmpty()) builder.isFalse(literal(false))
        else this.`in`(expression)
    }

    infix fun <E> Expression<E>.isInCollection(expression: Expression<Collection<E>>): Predicate {
        return this.`in`(expression)
    }

    fun <E : Number> sqrt(expression: Expression<E>): Expression<Double> {
        return builder.sqrt(expression)
    }

    fun <E> literal(value: E): Expression<E> {
        return builder.literal(value)
    }

    inline fun <reified E> nullLiteral(): Expression<E> {
        return builder.nullLiteral(E::class.java)
    }

    inline fun <reified E> parameter(name: String): ParameterExpression<E> {
        return builder.parameter(E::class.java, name)
    }

    fun <E : Collection<*>> isEmpty(expression: Expression<E>): Predicate {
        return builder.isEmpty(expression)
    }

    fun <E : Collection<*>> isNotEmpty(expression: Expression<E>): Predicate {
        return builder.isNotEmpty(expression)
    }

    fun <E : Collection<*>> size(expression: Expression<E>): Expression<Int> {
        return builder.size(expression)
    }

    fun <E : Collection<*>> size(collection: E): Expression<Int> {
        return builder.size(collection)
    }

    fun <I, E : Collection<I>> Expression<E>.contains(value: Expression<I>): Predicate {
        return builder.isMember(value, this)
    }

    fun <I, E : Collection<I>> Expression<E>.contains(value: I): Predicate {
        return builder.isMember(value, this)
    }

    infix fun Expression<String>.like(pattern: Expression<String>): Predicate {
        return builder.like(this, pattern)
    }

    infix fun Expression<String>.like(pattern: String): Predicate {
        return builder.like(this, pattern)
    }

    fun Expression<String>.like(pattern: Expression<String>, escapeChar: Expression<Char>): Predicate {
        return builder.like(this, pattern, escapeChar)
    }

    fun Expression<String>.like(pattern: Expression<String>, escapeChar: Char): Predicate {
        return builder.like(this, pattern, escapeChar)
    }

    fun Expression<String>.like(pattern: String, escapeChar: Expression<Char>): Predicate {
        return builder.like(this, pattern, escapeChar)
    }

    fun Expression<String>.like(pattern: String, escapeChar: Char): Predicate {
        return builder.like(this, pattern, escapeChar)
    }

    infix fun Expression<String>.concat(value: Expression<String>): Expression<String> {
        return builder.concat(this, value)
    }

    infix fun Expression<String>.concat(value: String): Expression<String> {
        return builder.concat(this, value)
    }

    fun Expression<String>.substring(from: Expression<Int>): Expression<String> {
        return builder.substring(this, from)
    }

    fun Expression<String>.substring(from: Expression<Int>, length: Expression<Int>): Expression<String> {
        return builder.substring(this, from, length)
    }

    fun Expression<Boolean>.toPredicate() = builder.isTrue(this)

    companion object {
        @PublishedApi
        internal fun isNumberType(other: Class<*>): Boolean {
            return Number::class.java.isAssignableFrom(other)
        }
    }
}

inline fun <reified Result, reified Root> Session.createCriteriaBuilder(): CriteriaBuilderContext<Result, Root> {
    val criteriaBuilder = criteriaBuilder
    val criteria = criteriaBuilder.createQuery(Result::class.java)
    val root = criteria.from(Root::class.java)
    return CriteriaBuilderContext<Result, Root>(criteriaBuilder, criteria, root)
}

// The "noinline"s are working around what seems to be a kotlin bug. For some reason it fails inlining for the
// orderBy lambda.
inline fun <reified T> Session.criteria(
    distinct: Boolean = false,
    noinline orderBy: CriteriaBuilderContext<T, T>.() -> List<Order>? = { null },
    noinline predicate: CriteriaBuilderContext<T, T>.() -> Predicate
): Query<T> {
    return createCriteriaBuilder<T, T>().run {
        criteria.select(entity)
        criteria.where(predicate())
        criteria.distinct(distinct)
        val order = orderBy()
        if (order != null) criteria.orderBy(order)
        criteria
    }.createQuery(this)
}

// The "noinline"s are working around what seems to be a kotlin bug. For some reason it fails inlining for the
// orderBy lambda.
inline fun <reified T> Session.paginatedCriteria(
    pagination: NormalizedPaginationRequest,
    distinct: Boolean = false,
    noinline orderBy: CriteriaBuilderContext<*, T>.() -> List<Order>? = { null },
    noinline predicate: CriteriaBuilderContext<*, T>.() -> Predicate
): Page<T> {
    val itemsInTotal = createCriteriaBuilder<Long, T>().run {
        criteria.where(predicate())
        criteria.select(count(entity))
    }.createQuery(this).uniqueResult()

    val pageItems = criteria(distinct, orderBy, predicate).paginatedList(pagination)

    return Page(
        itemsInTotal.toInt(),
        pagination.itemsPerPage,
        pagination.page,
        pageItems
    )
}

inline fun <reified T> Session.countWithPredicate(
    distinct: Boolean = false,
    selection: CriteriaBuilderContext<*, T>.() -> Expression<*> = { entity },
    predicate: CriteriaBuilderContext<*, T>.() -> Predicate
): Long {
    return createCriteriaBuilder<Long, T>().run {
        criteria.where(predicate())
        criteria.select(count(selection(), distinct))
    }.createQuery(this).uniqueResult()
}

fun <T> CriteriaQuery<T>.createQuery(session: Session): Query<T> {
    return session.createQuery(this)
}

fun HibernateSessionFactory.generateDDL(): String {
    val file = Files.createTempFile("table", ".sql").toFile()
    val schemaExport = SchemaExport().apply {
        setHaltOnError(true)
        setOutputFile(file.absolutePath)
        setFormat(true)
    }

    schemaExport.execute(EnumSet.of(TargetType.SCRIPT), SchemaExport.Action.CREATE, metadata)
    return file.readText()
}
