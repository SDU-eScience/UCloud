// FIXME START: Options for dashboard. Should be retrieved from DB

const DashboardOption = {
    name: "Dashboard",
    icon: "nav-icon",
    href: "/dashboard",
    children: null,
};

const FilesOptions = {
    name: "Files",
    icon: "nav-icon",
    href: "/files",
    children: null
};

const ProjectsOption = {
    name: "Projects",
    icon: "nav-icon",
    href: "/projects",
    children: null
};

const AppsApplicationsOption = {
    name: "Applications",
    icon: "",
    href: "/apps/applications",
    children: null
};

const AppsWorkflowsOption = {
    name: "Workflows",
    icon: "",
    href: "/apps/workflows",
    children: null,
};

const AppsAnalysesOption = {
    name: "Analyses",
    icon: "",
    href: "/apps/analyses",
    children: null,
};

const AppsOptions = {
    name: "Apps",
    icon: "",
    href: "",
    children: [AppsApplicationsOption, AppsWorkflowsOption, AppsAnalysesOption]
};

const ActivityNotificationsOption = {
    name: "Notifications",
    icon: "",
    href: "/activity/notifications",
    children: null,
};

const ActivityOptions = {
    name: "Activity",
    icon: "",
    href: "",
    children: [ActivityNotificationsOption]
};

const SidebarOptionsList = [
    DashboardOption, FilesOptions, ProjectsOption, AppsOptions, ActivityOptions
];
// FIXME END

// FIXME START GET APPS FROM DB
class ApplicationAbacus {
    constructor(applicationInfo, parameters) {
        this.info = applicationInfo;
        this.parameters = parameters;
    }
}

class ApplicationInfo {
    constructor(name, version, rating = 5.0, isPrivate = false, description = "An app to be run on Abacus", author = "Anyone") {
        this.name = name;
        this.version = version;
        this.rating = rating;
        this.isPrivate = isPrivate;
        this.description = description;
        this.author = author;
    }
}

class ApplicationField {
    constructor(name, prettyName, description, type, defaultValue, isOptional) {
        this.name = name;
        this.prettyName = prettyName;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.isOptional = isOptional;
    }
}

const Applications = [
    new ApplicationAbacus(new ApplicationInfo("Particle Simulator", "1.0"),
        [ new ApplicationField("input", "Input File", "The input file for the application.", "input_file", null, false),
            new ApplicationField("threads", "MPI Threads", "The number of MPI threads to be used.", "integer", "4", true) ]),
    new ApplicationAbacus(new ApplicationInfo("Particle Simulation Video Generator", "5.0"),
         [ new ApplicationField("input", "Input file", "The input file containing the results of a particle simulation.", "input_file", null, false),
            new ApplicationField("format", "File format", "The format which the file should be outputted as. Possible values: ogg (default)", "text", "ogg", true) ])
];

function getMockApp(name, version) {
    return Applications.first(app => app.info.name === name && app.info.version === version );
}

// FIXME END

// FIXME START GET FILES FROM SERVER

class AccessEntry {
    constructor(entity, right) {
        this.entity = entity;
        this.right = right;
    }
}

class StoragePath {
    constructor(uri, path, host, name) {
        this.uri = uri;
        this.path = path;
        this.host = host;
        this.name = name;
    }
}

let FileType = {
    FILE: 0,
    DIRECTORY: 1,
};

class StorageFile {
    constructor(type, path, createdAt, modifiedAt, size, acl) {
        this.type = type;
        this.path = path;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.size = size;
        this.acl = acl;
    }
}

let  AccessRight = {
    NONE: 0,
    READ: 1,
    READ_WRITE: 2,
    OWN: 3,
};

class iRODSUser {
    constructor(name, displayname, zone, type) {
        this.name = name;
        this.displayName = displayname;
        this.zone = zone;
        this.type = type;
    }
}

// FIXME END GET FILES FROM SERVER
// FIXME START GET ACTUAL STATUS
const status1 = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended.",
};

const status2 = {
    title: "Scheduled maintenance",
    level: "MAINTENANCE",
    body: "Maintenance is scheduled from 18 PM until midnight CET.",
};
const status3 = {
    title: "An error has occurred",
    level: "ERROR",
    body: "An error has occurred. The site will be back momentarily."
};
const status4 = {
    title: "No issues, upcoming maintenance",
    level: "UPCOMING MAINTENANCE",
    body: "Maintenance is scheduled from 18 PM until midnight CET."
};
// FIXME END GET ACTUAL STATUS

const Statuses = [ status1, status2, status3, status4 ];

export { SidebarOptionsList, Applications, getMockApp, Statuses }