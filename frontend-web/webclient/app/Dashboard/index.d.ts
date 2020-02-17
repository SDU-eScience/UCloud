import {JobWithStatus} from "Applications";
import {File} from "Files";
import {Notification} from "Notifications"

export type DashboardProps = DashboardOperations & DashboardStateProps;

export interface DashboardStateProps {
    favoriteFiles: File[]
    recentFiles: File[]
    recentAnalyses: JobWithStatus[]
    notifications: Notification[]
    favoriteLoading: boolean
    analysesLoading: boolean
    recentLoading: boolean
    favoriteFilesLength?: number
    favoritesError?: string;
    recentFilesError?: string;
    recentJobsError?: string;
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
