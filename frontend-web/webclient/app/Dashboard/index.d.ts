import {JobWithStatus} from "Applications";
import {File} from "Files";
import {Notification} from "Notifications"

export type DashboardProps = DashboardOperations & DashboardStateProps;

export interface DashboardStateProps {
    favoriteFiles: File[]
    recentAnalyses: JobWithStatus[]
    notifications: Notification[]
    favoriteLoading: boolean
    analysesLoading: boolean
    favoriteFilesLength?: number
    favoritesError?: string;
    recentJobsError?: string;
}

export interface DashboardOperations {
    onInit: () => void
    receiveFavorites: (files: File[]) => void
    setAllLoading: (loading: boolean) => void
    fetchUsage: () => void
    fetchFavorites: () => void
    fetchRecentAnalyses: () => void
    notificationRead: (id: number) => void
    readAll: () => void
    setRefresh: (refresh?: () => void) => void
}
