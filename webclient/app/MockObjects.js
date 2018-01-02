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



// FIXME END

export { SidebarOptionsList, Applications }