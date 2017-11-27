package org.esciencecloud.abc.services

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.abc.Request
import org.esciencecloud.abc.api.HPCStreams
import org.esciencecloud.abc.util.*
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.ext.StorageConnectionFactory
import kotlin.reflect.KClass

class HPCStreamService(
        private val storageConnectionFactory: StorageConnectionFactory,
        builder: KStreamBuilder,
        producer: KafkaProducer<String, String>
) {
    val appRequests = builder.stream(HPCStreams.AppRequests).authenticate()
    val appEvents = builder.stream(HPCStreams.AppEvents)
    val appEventsProducer = producer.forStream(HPCStreams.AppEvents)

    private fun <K, T : Any, V : Request<T>> KStream<K, V>.authenticate(): AuthenticatedStream<K, T> {
        val (auth, unauth) = mapValues { request ->
            val conn = with(request.header.performedFor) {
                storageConnectionFactory.createForAccount(username, password)
            }

            Pair(conn, request)
        }.diverge { _, value ->
            value.first is Ok
        }

        val authMapped = auth.mapValues { RequestAfterAuthentication.Authenticated(it.second, it.first.orThrow()) }
        val unauthMapped = unauth.mapValues {
            RequestAfterAuthentication.Unauthenticated(it.second, it.first as Error<Any>)
        }

        return AuthenticatedStream(authMapped, unauthMapped)
    }
}

data class AuthenticatedStream<K, R : Any>(
        val authenticated: KStream<K, RequestAfterAuthentication.Authenticated<R>>,
        val unauthenticated: KStream<K, RequestAfterAuthentication.Unauthenticated<R>>
) {
    fun filter(predicate: (K, R) -> Boolean): AuthenticatedStream<K, R> {
        val auth = authenticated.filter { key, value -> predicate(key, value.event) }
        val unauth = unauthenticated.filter { key, value -> predicate(key, value.event) }
        return AuthenticatedStream(auth, unauth)
    }

    @Suppress("UNCHECKED_CAST")
    fun <SubType : R> filterIsInstance(klass: KClass<SubType>): AuthenticatedStream<K, SubType> {
        val auth = authenticated.filter { _, value -> klass.isInstance(value.event) }.mapValues {
            it as RequestAfterAuthentication.Authenticated<SubType>
        }

        val unauth = unauthenticated.filter { _, value -> klass.isInstance(value.event) }.mapValues {
            it as RequestAfterAuthentication.Unauthenticated<SubType>
        }

        return AuthenticatedStream(auth, unauth)
    }

    fun <RespondType> respond(
            target: StreamDescription<K, RespondType>,
            onUnauthenticated: (K, RequestAfterAuthentication.Unauthenticated<R>) -> RespondType,
            onAuthenticated: (K, RequestAfterAuthentication.Authenticated<R>) -> RespondType
    ) {
        authenticated.map { k, v -> KeyValue(k, onAuthenticated(k, v)) }.to(target)
        unauthenticated.map { k, v -> KeyValue(k, onUnauthenticated(k, v)) }.to(target)
    }
}
