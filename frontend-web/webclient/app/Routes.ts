import {buildQueryString} from "./Utilities/URIUtilities";

const users = {
    settings: () => "/users/settings",
    avatar: () => "/users/avatar",
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
    members: () => `/projects/members`,
    usage: () => `/project/resources/`,
    allocations: () => `/project/allocations/`,
    grants: () => `/project/grants/ingoing/`,
    settings: (page: string) => `/project/settings/${page}`,
    subprojects: () => `/subprojects/`,
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
    vnc: (jobId: string, rank: string) => `/applications/vnc/${jobId}/${rank}`,
};

const jobs = {
    create: (name: string, version: string) => `/jobs/create?app=${name}&version=${version}`,
    view: (jobId: string) => `/jobs/properties/${jobId}`,
    results: () => `/applications/results`,
    run: (name: string, version: string) => buildQueryString("/jobs/create", {app: name, version}),
};

const login = {
    login: () => "/login",
    loginSuccess: () => "/loginSuccess",
    loginWayf: () => "/login/wayf",
};

const dashboard = {
    dashboardA: () => "/",
    dashboardB: () => "/dashboard",
};

const resource = {
    properties: (namespace: string, id: string) => `/${namespace}/properties/${encodeURIComponent(id)}`
}

const AppRoutes = {
    apps,
    news,
    dashboard,
    users,
    admin,
    resource,
    shares,
    project,
    resources,
    login,
    jobs,
    syncthing
};

export default AppRoutes;