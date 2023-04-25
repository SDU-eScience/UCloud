import * as React from "react";
import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarLink";
import {isAdminOrPI, useProjectId} from "./Api";
import AppRoutes from "@/Routes";
import {useProject} from "./cache";

export function ProjectLinks(): JSX.Element {
    const activeProjectId = useProjectId();
    const project = useProject();

    const links: LinkInfo[] = React.useMemo(() => {
        const isPersonalWorkspace = !activeProjectId;
        const adminOrPi = isAdminOrPI(project.fetch().status.myRole);

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
        if (adminOrPi) {
            result.push({
                to: AppRoutes.project.settings(""),
                text: "Settings",
                icon: "projects",
                disabled: isPersonalWorkspace
            });
        }
        result.push({
            to: AppRoutes.project.subprojects(),
            text: "Subprojects",
            icon: "projects",
            disabled: isPersonalWorkspace
        });
        return result;
    }, [activeProjectId, project]);
    return <SidebarLinkColumn links={links} />
}