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
            icon: "heroUsers",
            removed: isPersonalWorkspace,
        })
        result.push({
            to: AppRoutes.project.usage(),
            text: "Resource Usage",
            icon: "heroChartPie"
        });
        result.push({
            to: AppRoutes.project.allocations(),
            text: "Resource Allocations",
            icon: "heroBanknotes",
        });
        result.push({
            to: AppRoutes.grants.outgoing(),
            text: "Grant Applications",
            icon: "heroDocumentText",
        });
        if (adminOrPi) {
            result.push({
                to: AppRoutes.project.settings(""),
                text: "Settings",
                icon: "heroWrenchScrewdriver",
                removed: isPersonalWorkspace
            });
        }
        result.push({
            to: AppRoutes.project.subprojects(),
            text: "Subprojects",
            icon: "heroUserGroup",
            removed: isPersonalWorkspace
        });
        return result;
    }, [activeProjectId, project]);
    return <SidebarLinkColumn links={links} />
}
