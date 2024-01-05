import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import AppRoutes from "@/Routes";
import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarComponents";

const linkInfo: LinkInfo[] = [
    {to: AppRoutes.admin.userCreation(), text: "User creation", icon: "heroUser"},
    {to: AppRoutes.admin.applicationStudio(), text: "Application studio", icon: "heroBuildingStorefront"},
    {to: AppRoutes.admin.news(), text: "News", icon: "heroNewspaper"},
    {to: AppRoutes.admin.providers(), text: "Providers", icon: "heroCloud"},
    {to: AppRoutes.admin.scripts(), text: "Scripts", icon: "heroPlayPause"},
];

if (DEVELOPMENT_ENV) {
    linkInfo.push({to: "/admin/devData", text: "Development Test Data", icon: "heroBeaker"})
}


function AdminLinks(): JSX.Element | null {
    if (!Client.userIsAdmin) return null;
    return <SidebarLinkColumn links={linkInfo} />;
}

export default AdminLinks;
