package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.*
import kotlinx.serialization.Serializable
import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.store.api.AppParameterValue.Text

fun manageHeader(job: Job, config: IMConfiguration): String {

    val job_content =
        job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
    val job_timelimit =
        "${job.specification?.timeAllocation?.hours}:${job.specification?.timeAllocation?.minutes}:${job.specification?.timeAllocation?.seconds}"
    val request_product = job.specification?.product as ProductReference

    val job_partition = config.plugins?.compute?.plugins?.first { it.id == TAG }?.configuration?.partition
    val mountpoint = config.plugins!!.compute!!.plugins!!.first { it.id == TAG }!!.configuration!!.mountpoint
    val job_nodes = job.specification!!.replicas

    val product_cpu = config.plugins?.compute?.products?.first { it.id == request_product.id }?.cpu.toString()
    val product_mem = config.plugins?.compute?.products?.first { it.id == request_product.id }?.mem.toString()
    val product_gpu = config.plugins?.compute?.products?.first { it.id == request_product.id }?.gpu.toString()

    //sbatch will stop processing further #SBATCH directives once the first non-comment non-whitespace line has been reached in the script.
    // remove whitespaces
    val fileBody = job_content.value.lines().map { it.trim() }.toMutableList()
    val headerSuffix =
        """
            #
            # POSTFIX START
            #
            #SBATCH --chdir ${mountpoint}/${job.id} 
            #SBATCH --cpus-per-task ${product_cpu} 
            #SBATCH --mem ${product_mem} 
            #SBATCH --gpus-per-node ${product_gpu} 
            #SBATCH --time ${job_timelimit} 
            #SBATCH --nodes ${job_nodes} 
            #SBATCH --job-name ${job.id} 
            #SBATCH --partition ${job_partition} 
            #SBATCH --parsable
            #SBATCH --output=std.out 
            #SBATCH --error=std.err
            #
            # POSTFIX END
            #
        """.trimIndent().lines()

    println(headerSuffix)

    //find first nonwhitespace non comment line
    var headerEnd: Int = 0
    run loop@{
        fileBody.forEachIndexed { idx, line ->
            //println(line)
            if (!line.trim().startsWith("#")) {
                headerEnd = idx
                return@loop
            }
        }
    }

    // append lines starting with headerEnd
    fileBody.addAll(headerEnd, headerSuffix)

    println(fileBody)

    // append shebang
    return fileBody.joinToString(prefix = "#!/usr/bin/bash \n", separator = "\n", postfix = "\n#EOF\n")

}
