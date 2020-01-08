package dk.sdu.cloud.kubernetes.monitor.services

interface AlertNotifier {
    suspend fun onAlert(alert: Alert)
}
