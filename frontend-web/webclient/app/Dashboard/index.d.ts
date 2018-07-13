import { Analysis } from "Applications";
import { File } from "Files";

export interface DashboardProps extends DashboardOperations, DashboardStateProps {}

export interface DashboardStateProps {
    // Redux store props
    favoriteFiles, recentFiles: File[]
    recentAnalyses: Analysis[]
    notifications: any[]
    favoriteLoading, analysesLoading, recentLoading: boolean
    favoriteFilesLength: number
}

export interface DashboardOperations {
    // Redux operations
    receiveFavorites: (files: File[]) => void
    updatePageTitle: () => void
    setAllLoading: (loading: boolean) => void
    fetchFavorites: () => void
    fetchRecentFiles: () => void
    fetchRecentAnalyses: () => void
}