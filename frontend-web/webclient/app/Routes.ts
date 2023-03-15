const users = {
    settings: () => "/users/settings"
};

const news = {
    detailed: (id: string | number) => `/news/detailed/${id}`,
    list: (filter: string) => `/news/list/${filter}`,
};

const admin = {
    userCreation: () => "/admin/userCreation",
    applicationStudio: () => "/applications/studio",
    news: () => "/admin/news",
    providers: () => "/admin/providers",
    scripts: () => "/admin/scripts"
};

const shares = {
    sharedByMe: () => "/shares/outgoing",
    sharedWithMe: () => "/shares"
}

const project = {
    members: (projectId: string) => `/projects/${projectId}/members`,
    usage: (projectId: string) => `/project/resources/${projectId}`,
    allocations: (projectId: string) => `/project/allocations/${projectId}`,
    grants: (projectId: string) => `/project/grants/ingoing/${projectId}`,
    settings: (projectId: string) => `/project/settings/${projectId}`,
    subprojects: (projectId: string) => `/subprojects/${projectId}`
}

const AppRoutes = {
    news,
    users,
    admin,
    shares,
    project
};

export default AppRoutes;