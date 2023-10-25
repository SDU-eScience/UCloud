import * as React from "react";

import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarLink";
import AppRoutes from "@/Routes";

export enum ResourceOptions {
    PUBLIC_IP = "Public IPs",
    PUBLIC_LINKS = "Public Links",
    LICENSES = "Licenses",
    SSH_KEYS = "SSH Keys",
}

export const sidebarLinks: LinkInfo[] = [
    {icon: "heroGlobeEuropeAfrica", text: ResourceOptions.PUBLIC_IP, to: AppRoutes.resources.publicIps()},
    {icon: "heroLink", text: ResourceOptions.PUBLIC_LINKS, to: AppRoutes.resources.publicLinks()},
    {icon: "heroDocumentCheck", text: ResourceOptions.LICENSES, to: AppRoutes.resources.licenses()},
    {icon: "heroKey", text: ResourceOptions.SSH_KEYS, to: AppRoutes.resources.sshKeys()}
];

export function ResourceLinks() {
    return <SidebarLinkColumn links={sidebarLinks} />
}
