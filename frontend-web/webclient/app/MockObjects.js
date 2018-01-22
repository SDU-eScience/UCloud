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
    href: "/files/",
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

export { SidebarOptionsList }