import {Notification} from "@/Notifications";

export type DashboardProps = DashboardOperations & DashboardStateProps;

export interface DashboardStateProps {
    notifications: Notification[];
}

export interface DashboardOperations {
    onInit: () => void;
    setAllLoading: (loading: boolean) => void;
    notificationRead: (id: number) => void;
    readAll: () => void;
    setRefresh: (refresh?: () => void) => void;
    setActiveProject: (projectId?: string) => void;
}
