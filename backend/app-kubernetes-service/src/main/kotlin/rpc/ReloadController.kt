package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.services.K8Dependencies
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class ReloadController(val k8: K8Dependencies) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        /*
        if (k8.client.allowReloading) {
            implement(AppKubernetesDescriptions.reload) {
                k8.client.reload()

                // Initialize Kubernetes to use the development files
                k8.client.namespaces().create(Namespace().apply {
                    metadata = ObjectMeta().apply {
                        name = k8.nameAllocator.namespace
                    }
                })

                k8.client.persistentVolumes().create(
                    PersistentVolume().apply {
                        metadata = ObjectMeta().apply {
                            name = "storage"
                        }

                        spec = PersistentVolumeSpec().apply {
                            capacity = mapOf("storage" to Quantity("1000Gi"))
                            volumeMode = "Filesystem"
                            accessModes = listOf("ReadWriteMany")
                            persistentVolumeReclaimPolicy = "Retain"
                            storageClassName = ""
                            hostPath = HostPathVolumeSource().apply {
                                path = request.fileLocation
                            }
                        }
                    }
                )

                k8.client.persistentVolumeClaims().create(PersistentVolumeClaim().apply {
                    metadata = ObjectMeta().apply {
                        name = CEPHFS
                        namespace = k8.nameAllocator.namespace
                    }

                    spec = PersistentVolumeClaimSpec().apply {
                        accessModes = listOf("ReadWriteMany")
                        storageClassName = ""
                        volumeName = "storage"
                        resources = ResourceRequirements().apply {
                            requests = mapOf("storage" to Quantity("1000Gi"))
                        }
                    }
                })

                ok(Unit)
            }
        }
         */
        return@with
    }
}
