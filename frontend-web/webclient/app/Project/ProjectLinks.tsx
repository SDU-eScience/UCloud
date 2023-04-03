import * as React from "react";
import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarLink";
import {useProjectId} from "./Api";
import AppRoutes from "@/Routes";

export function ProjectLinks(): JSX.Element {
    const activeProjectId = useProjectId();

    const links: LinkInfo[] = React.useMemo(() => {
        const isPersonalWorkspace = !activeProjectId;

        const result: LinkInfo[] = [];
        result.push({
            to: AppRoutes.project.members(),
            text: "Members",
            icon: "projects",
            disabled: isPersonalWorkspace,
        })
        result.push({
            to: AppRoutes.project.usage(),
            text: "Resource Usage",
            icon: "projects"
        });
        result.push({
            to: AppRoutes.project.allocations(),
            text: "Resource Allocations",
            icon: "projects",
        });
        result.push({
            to: AppRoutes.project.grants(),
            text: "Grant Applications",
            icon: "projects",
        });
        result.push({
            to: AppRoutes.project.settings(""),
            text: "Settings",
            icon: "projects",
            disabled: isPersonalWorkspace
        });
        result.push({
            to: AppRoutes.project.subprojects(),
            text: "Subprojects",
            icon: "projects",
            disabled: isPersonalWorkspace
        });
        return result;
    }, [activeProjectId]);
    return <SidebarLinkColumn links={links} />
}