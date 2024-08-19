package dk.sdu.cloud.extract.data.api

const val MINUTES_PER_HOUR = 60L

const val SDU_CLOUD_PROJECT_ID = "3196deee-c3c2-464b-b328-4d3c5d02b953"
const val SDU_TYPE_1_CLOUD_PROJECT_ID = "624b33a1-876b-4f57-977e-c4b2f8cc3989"
const val AAU_TYPE_1_CLOUD_PROJECT_ID = "550f9fde-2411-4731-973a-2afb2c61e971"
const val AU_TYPE_1_CLOUD_PROJECT_ID = "204515b3-eb95-4a33-a70e-0b58c1e370ac"
const val CBS_TYPE_1_CLOUD_PROJECT_ID = "332e9fd7-9046-4491-a71b-92365f506afe"
const val DTU_TYPE_1_CLOUD_PROJECT_ID = "8a1f6023-fdf8-4c45-bee1-2f345b084e71"
const val ITU_TYPE_1_CLOUD_PROJECT_ID = "95a3c926-cc56-4abe-88a4-8e92e42c113d"
const val KU_TYPE_1_CLOUD_PROJECT_ID = "bc0bee4d-0390-41df-ada1-cda226fd097e"
const val RUC_TYPE_1_CLOUD_PROJECT_ID = "c48b0d67-e71f-4168-aee1-cbe748aa498e"


const val TYPE_1_HPC_CENTER_ID = "f0679faa-242e-11eb-3aba-b187bcbee6d4"
const val TYPE_1_HPC_SUB_CENTER_ID_SDU ="0534ca5e-242f-11eb-2dca-2fe365de2d94"
const val TYPE_1_HPC_SUB_CENTER_ID_AAU = "1003d37e-242f-11eb-186e-0722713fb0ad"

const val TYPE_3_HPC_CENTER_ID = "a498f984-e864-43ec-95b0-e46ab0b13e33"
const val TYPE_3_HPC_SUB_CENTER_ID_SDU = "849c4cfb-b6b7-4766-a48d-281e8276b399"

const val TYPE_1_CPU_CORES = 2048L

const val TYPE_1_GPU_CORES = 76L

enum class ProductType {
    CPU,
    GPU,
    STORAGE,
    OTHER;

    companion object {
        fun createFromType(productType: String, isGPU: Boolean): ProductType{
            return when {
                productType == "COMPUTE" && !isGPU -> CPU;
                productType == "COMPUTE" && isGPU -> GPU;
                productType == "STORAGE" -> STORAGE
                else -> OTHER
            }
        }
    }
}
