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

const resources = {
    publicIps: () => "/public-ips",
    publicLinks: () => "/public-links",
    licenses: () => "/licenses",
    sshKeys: () => "/ssh-keys",
}

const project = {
    members: (projectId: string) => `/projects/${projectId}/members`,
    usage: (projectId: string) => `/project/resources/${projectId}`,
    allocations: (projectId: string) => `/project/allocations/${projectId}`,
    grants: (projectId: string) => `/project/grants/ingoing/${projectId}`,
    settings: (projectId: string) => `/project/settings/${projectId}`,
    subprojects: (projectId: string) => `/subprojects/${projectId}`,
}

const syncthing = {
    syncthing: () => "/syncthing"
}

const apps = {
    applications: () => "/applications",
    overview: () => "/applications/overview",
    search: () => "/applications/search",
    studio: () => "/applications/studio",
    studioTool: (tool: string) => `/applications/studio/t/${tool}`,
    studioApp: (app: string) => `/applications/studio/a/${app}`,
    shell: (jobId: string, rank: string) => `/applications/shell/${jobId}/${rank}`,
    web: (jobId: string, rank: string) => `/applications/web/${jobId}/${rank}`,
    vnc: (jobId: string, rank: string) => `/applications/vnc/${jobId}/${rank}`
};

const login = {
    login: () => "/login",
    loginSuccess: () => "/loginSuccess",
    loginWayf: () => "/login/wayf",
};

const dashboard = {
    dashboardA: () => "/",
    dashboardB: () => "/dashboard",
}

const AppRoutes = {
    apps,
    news,
    dashboard,
    users,
    admin,
    shares,
    project,
    resources,
    login,
    syncthing
};

export default AppRoutes;