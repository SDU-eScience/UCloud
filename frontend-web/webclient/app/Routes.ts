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
    applicationStudio: () => "/applications/studio/groups",
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
    sharedWithMe: () => "/shares",
}

const resources = {
    publicIps: () => "/public-ips",
    publicLinks: () => "/public-links",
    licenses: () => "/licenses",
    sshKeys: () => "/ssh-keys",
    sshKeysCreate: () => "/ssh-keys/create",
    sshKeysProperties: () => "/ssh-keys/properties"
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
    studioGroups: () => "/applications/studio/groups",
    studioTopPicks: () => "/applications/studio/topPicks",
    studioHero: () => "/applications/studio/hero",
    studioSpotlights: () => "/applications/studio/spotlights",
    studioSpotlightsEditor: (id?: number) => buildQueryString("/applications/studio/spotlights/editor", {id}),
    studioCategories: () => "/applications/studio/categories",
    studioApp: (app: string) => `/applications/studio/a/${app}`,
    studioGroup: (group: string) => `/applications/studio/g/${group}`,
    shell: (jobId: string, rank: string) => `/applications/shell/${jobId}/${rank}`,
    web: (jobId: string, rank: string) => `/applications/web/${jobId}/${rank}`,
    vnc: (jobId: string, rank: string) => `/applications/vnc/${jobId}/${rank}`,
    fork: (name?: string, version?: string) => buildQueryString("/applications/fork", {name, version}),
};

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
    syncthing,
    grants,
    accounting,
    providers,
    files,
};

export default AppRoutes;
