export interface Notification {
    type: string
    jobId?: string
    ts: number
    status?: string
    id: string
    isRead: boolean
}
