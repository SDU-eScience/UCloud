import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import AppRoutes from "@/Routes";
import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarLink";

const linkInfo: LinkInfo[] = [
    {to: AppRoutes.admin.userCreation(), text: "User creation", icon: "user"},
    {to: AppRoutes.admin.applicationStudio(), text: "Application studio", icon: "appStore"},
    {to: AppRoutes.admin.news(), text: "News", icon: "warning"},
    {to: AppRoutes.admin.providers(), text: "Providers", icon: "cloudTryingItsBest"},
    {to: AppRoutes.admin.scripts(), text: "Scripts", icon: "play"},
];

if (DEVELOPMENT_ENV) {
    linkInfo.push({to: "/admin/devData", text: "Development Test Data", icon: "activity"})
}


function AdminLinks(): JSX.Element | null {
    if (!Client.userIsAdmin) return null;
    return <SidebarLinkColumn links={linkInfo} />;
}

export default AdminLinks;
