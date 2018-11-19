import { Analysis } from "Applications";
import { File } from "Files";
import { Notification } from "Notifications"

export interface DashboardProps extends DashboardOperations, DashboardStateProps { }

export interface DashboardStateProps {
    // Redux store props
    favoriteFiles: File[]
    recentFiles: File[]
    recentAnalyses: Analysis[]
    notifications: Notification[]
    favoriteLoading: boolean
    analysesLoading: boolean
    recentLoading: boolean
    favoriteFilesLength?: number
    errors: string[]
}

export interface DashboardOperations {
    // Redux operations
    errorDismiss: () => void
    receiveFavorites: (files: File[]) => void
    updatePageTitle: () => void
    setAllLoading: (loading: boolean) => void
    fetchFavorites: () => void
    fetchRecentFiles: () => void
    fetchRecentAnalyses: () => void
    notificationRead: (id: number) => void
}