import { Analysis } from "Applications";
import { File } from "Files";
import { Notification } from "Notifications"

export interface DashboardProps extends DashboardOperations, DashboardStateProps { }

export interface DashboardStateProps {
    favoriteFiles: File[]
    recentFiles: File[]
    recentAnalyses: Analysis[]
    notifications: Notification[]
    favoriteLoading: boolean
    analysesLoading: boolean
    recentLoading: boolean
    favoriteFilesLength?: number
}

export interface DashboardOperations {
    onInit: () => void
    receiveFavorites: (files: File[]) => void
    setAllLoading: (loading: boolean) => void
    fetchUsage: () => void
    fetchFavorites: () => void
    fetchRecentFiles: () => void
    fetchRecentAnalyses: () => void
    notificationRead: (id: number) => void
    readAll: () => void
    setRefresh: (refresh?: () => void) => void
}