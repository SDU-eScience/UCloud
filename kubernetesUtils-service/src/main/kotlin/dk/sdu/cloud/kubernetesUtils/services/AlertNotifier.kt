package dk.sdu.cloud.kubernetesUtils.services


interface AlertNotifier {
    suspend fun onAlert(alert: Alert)
}
