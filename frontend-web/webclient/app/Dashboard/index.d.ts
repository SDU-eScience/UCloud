import {JobWithStatus} from "Applications";
import {Notification} from "Notifications";

export type DashboardProps = DashboardOperations & DashboardStateProps;

export interface DashboardStateProps {
    recentAnalyses: JobWithStatus[];
    notifications: Notification[];
    analysesLoading: boolean;
    recentJobsError?: string;
}

export interface DashboardOperations {
    onInit: () => void;
    setAllLoading: (loading: boolean) => void;
    fetchRecentAnalyses: () => void;
    notificationRead: (id: number) => void;
    readAll: () => void;
    setRefresh: (refresh?: () => void) => void;
    setActiveProject: (projectId?: string) => void;
}
