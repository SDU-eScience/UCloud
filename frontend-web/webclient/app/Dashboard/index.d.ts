export type DashboardProps = DashboardOperations & DashboardStateProps;

export interface DashboardStateProps {
}

export interface DashboardOperations {
    onInit: () => void;
    setAllLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
    setActiveProject: (projectId?: string) => void;
}
