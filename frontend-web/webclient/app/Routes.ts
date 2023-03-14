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

const admin = {
    userCreation: () => "/admin/userCreation",
    applicationStudio: () => "/applications/studio",
    news: () => "/admin/news",
    providers: () => "/admin/providers",
    scripts: () => "/admin/scripts"
};

const AppRoutes = {
    news,
    users,
    project,
    admin,
};

export default AppRoutes;