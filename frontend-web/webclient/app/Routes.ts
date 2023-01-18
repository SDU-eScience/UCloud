const users = {
    settings: () => "/users/settings"
};

const news = {
    detailed: (id: string | number) => `/news/detailed/${id}`,
    list: (filter: string) => `/news/list/${filter}`,
};

const project = {
    members: (projectId: string) => `/projects/${projectId}/members`
}

const AppRoutes = {
    news,
    users,
    project,
};

export default AppRoutes;