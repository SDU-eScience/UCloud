// TODO: RENAME ME
import * as React from "react";

import {SelectableText, SelectableTextWrapper} from "@/ui-components";
import {useNavigate} from "react-router";
import {Feature, hasFeature} from "@/Features";
import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarLink";
import AppRoutes from "@/Routes";

export enum ResourceOptions {
    PUBLIC_IP = "Public IPs",
    PUBLIC_LINKS = "Public Links",
    LICENSES = "Licenses",
    SSH_KEYS = "SSH Keys",
}

export const sidebarLinks: LinkInfo[] = [
    {icon: "starFilled", text: ResourceOptions.PUBLIC_IP, to: AppRoutes.resources.publicIps()},
    {icon: "starFilled", text: ResourceOptions.PUBLIC_LINKS, to: AppRoutes.resources.publicLinks()},
    {icon: "starFilled", text: ResourceOptions.LICENSES, to: AppRoutes.resources.licenses()},
    {icon: "starFilled", text: ResourceOptions.SSH_KEYS, to: AppRoutes.resources.sshKeys()}
];

export function ResourceLinks() {
    return <SidebarLinkColumn links={sidebarLinks} />
}