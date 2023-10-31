export interface DashboardStateProps {
    loading: boolean;
}

export interface DashboardOperations {
    setAllLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
    setActiveProject: (projectId?: string) => void;
}
