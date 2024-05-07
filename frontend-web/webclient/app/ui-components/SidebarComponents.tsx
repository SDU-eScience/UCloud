import * as React from "react";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import {Flex, Truncate} from "@/ui-components";

export enum SidebarTabId {
    NONE = "",
    FILES = "Files",
    PROJECT = "Project",
    RESOURCES = "Resources",
    APPLICATIONS = "Applications",
    RUNS = "Runs",
    ADMIN = "Admin",
}

export interface LinkInfo {
    to: string;
    text: string;
    icon: IconName | React.ReactNode;
    disabled?: boolean;
    removed?: boolean;
    tab: SidebarTabId;
}

export function SidebarLinkColumn({links}: { links: LinkInfo[] }): React.ReactNode {
    return <>
        {links.map(l => <SidebarEntry {...l} key={l.text} />)}
    </>
}

export const SidebarEntry: React.FunctionComponent<LinkInfo> = (info) => {
    if (info.disabled) return null;

    const icon = typeof info.icon === "string" ?
        <Icon mt="2px" name={info.icon as IconName} color={"fixedWhite"} color2={"fixedWhite"} /> :
        info.icon;

    return <Link key={info.text} to={info.to} data-tab={info.tab}>
        <Flex flexDirection={"row"} gap={"4px"}>
            {icon}
            <Truncate style={{display: "flex", alignItems: "center"}} fontSize="14px" title={info.text} maxWidth={"150px"} color="var(--fixedWhite)">
                {info.text}
            </Truncate>
        </Flex>
    </Link>;
}

export const SidebarSectionHeader: React.FunctionComponent<{
    to?: string;
    children: React.ReactNode
    tab: SidebarTabId;
}> = ({to, children, tab}) => {
    if (to) {
        return <Link to={to} className={"heading"} data-tab={tab}><h3>{children}</h3></Link>;
    } else {
        return <h3 className={"no-link"}>{children}</h3>
    }
}

export const SidebarEmpty: React.FunctionComponent<{ children: React.ReactNode }> = ({children}) => {
    return <i>{children}</i>;
}