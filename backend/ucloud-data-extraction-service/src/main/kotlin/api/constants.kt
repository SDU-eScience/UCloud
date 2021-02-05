package dk.sdu.cloud.ucloud.data.extraction.api

import okhttp3.internal.UTC
import org.joda.time.DateTimeZone
import org.joda.time.Days
import org.joda.time.LocalDateTime
import java.net.HttpURLConnection
import java.net.URL

const val TYPE_1_HPC_CENTER_ID = "f0679faa-242e-11eb-3aba-b187bcbee6d4"
const val TYPE_1_HPC_SUB_CENTER_ID_SDU ="0534ca5e-242f-11eb-2dca-2fe365de2d94"
const val TYPE_1_HPC_SUB_CENTER_ID_AAU = "1003d37e-242f-11eb-186e-0722713fb0ad"

const val TYPE_3_HPC_CENTER_ID = "a498f984-e864-43ec-95b0-e46ab0b13e33"
const val TYPE_3_HPC_SUB_CENTER_ID_SDU = "849c4cfb-b6b7-4766-a48d-281e8276b399"

const val TYPE_1_CPU_CORES = 2048L
const val TYPE_1_CPU_CORE_PRICE = 1.433


const val TYPE_1_GPU_CORES = 0L
const val TYPE_1_GPU_CORE_PRICE = 0.0

const val TYPE_1_CEPH_MB_PRICE = 0.0

enum class ProductType(val catagoryId: String) {
    CPU("u1-standard") {
        override fun getPricing() = TYPE_1_CPU_CORE_PRICE
                       },
    GPU("u1-gpu") {
        override fun getPricing() = TYPE_1_GPU_CORE_PRICE
                  },
    STORAGE("u1-cephfs") {
        override fun getPricing() = TYPE_1_CEPH_MB_PRICE
    };

    abstract fun getPricing(): Double
}
