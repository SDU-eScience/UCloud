import {buildQueryString} from "./Utilities/URIUtilities";

const users = {
    settings: () => "/users/settings",
    avatar: () => "/users/avatar",
    registration: () => "/registration",
    verifyEmail: () => "/verifyEmail",
    verifyResult: () => "/verifyResult",
};

const news = {
    detailed: (id: string | number) => `/news/detailed/${id}`,
    list: (filter: string) => `/news/list/${filter}`,
};

const admin = {
    userCreation: () => "/admin/userCreation",
    news: () => "/admin/news",
    providers: () => "/admin/providers",
    scripts: () => "/admin/scripts",
    playground: () => "/playground",
};

const providers = {
    detailed: (id: string) => `/providers/detailed/${id}`
}

const shares = {
    sharedByMe: () => "/shares/outgoing",
    sharedByMeFile: (path: string) => `/shares/outgoing?path=${encodeURI(path)}`,
    sharedWithMe: () => "/shares",
}

const resources = {
    publicIps: () => "/public-ips",
    publicLinks: () => "/public-links",
    licenses: () => "/licenses",
    sshKeys: () => "/ssh-keys",
    sshKeysCreate: () => "/ssh-keys/create",
    sshKeysProperties: () => "/ssh-keys/properties",
    apiTokens: () => "/api-tokens",
    apiTokensCreate: () => "/api-tokens/create"
}

const project = {
    members: () => `/projects/members`,
    usage: () => accounting.usage(),
    allocations: () => accounting.allocations(),
    settings: (page: string) => `/project/settings${page === "" ? "" : "/" + page}`,
    subprojects: () => accounting.allocations(),
}

const syncthing = {
    syncthing: () => "/syncthing"
}

const apps = {
    landing: () => "/applications",
    category: (categoryId?: number) => buildQueryString(`/applications/category`, {categoryId}),
    group: (id: string) => `/applications/group/${id}`,
    search: (q?: string) => "/applications/search" + (q ? `?q=${q}` : ""),
    shell: (jobId: string, rank: string) => `/applications/shell/${jobId}/${rank}`,
    web: (jobId: string, rank: string) => `/applications/web/${jobId}/${rank}`,
    vnc: (jobId: string, rank: string) => `/applications/vnc/${jobId}/${rank}`,
};

const appStudio = {
    groups: () => "/applications/studio/groups",
    topPicks: () => "/applications/studio/topPicks",
    hero: () => "/applications/studio/hero",
    spotlights: () => "/applications/studio/spotlights",
    spotlightsEditor: (id?: number) => buildQueryString("/applications/studio/spotlights/editor", {id}),
    categories: () => "/applications/studio/categories",
    app: (app: string) => `/applications/studio/a/${app}`,
    group: (group: string) => `/applications/studio/g/${group}`,
}

const jobs = {
    list: () => `/jobs`,
    create: (name: string, version?: string, importId?: string) => buildQueryString(`/jobs/create`, {app: name, version, import: importId}),
    view: (jobId: string) => `/jobs/properties/${jobId}`,
    results: () => `/applications/results`,
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

const grants = {
    editor: (id?: string | number) => buildQueryString("/grants", {id}),
    ingoing: () => "/grants/ingoing",
    outgoing: () => "/grants/outgoing",
    grantGiverInitiatedEditor: (opts: {
        title: string,
        projectId?: string,
        piUsernameHint: string,
        start: number,
        end: number,
        subAllocator: boolean,
    }) => buildQueryString("/grants", {...opts, type: "grantGiverInitiated"}),
    newApplication: (values: {
        projectId?: string,
    }) => buildQueryString("/grants", {...values, type: "applicantInitiated"})
}

const accounting = {
    usage: () => "/usage",
    allocations: () => "/allocations",
}

const files = {
    drives: () => "/drives",
    drive: (driveId: string) => buildQueryString("/files", {path: "/" + driveId}),
    path: (path: string) => buildQueryString("/files", {path}),
    preview: (path: string) => "/files/properties/" + encodeURIComponent(path)
}

const supportAssist = {
    base: () => "/support-assist",
    user() {return this.base() + "/user"},
    project() {return this.base() + "/project"},
    allocation() {return this.base() + "/allocation"},
    job() {return this.base() + "/job"},

}

const AppRoutes = {
    apps,
    appStudio,
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
    syncthing,
    grants,
    accounting,
    providers,
    files,
    supportAssist,
    prefix: "/app",
};

export default AppRoutes;
