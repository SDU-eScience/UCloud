import * as React from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {Application, customAppsWorkspaceAdmin} from "@/Applications/AppStoreApi";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import {Button, Icon} from "@/ui-components";

export function ApplicationForkAction(props: {application: Application}): React.ReactNode {
    const projectId = useProjectId();
    const navigate = useNavigate();
    const location = useLocation();
    const canFork = customAppsWorkspaceAdmin();
    if (!canFork) return null;

    const sourceApplicationKind = props.application.metadata.origin === "CUSTOM" && props.application.metadata.variant == null
        ? "custom"
        : "managed";
    const sourceProvider = sourceApplicationKind === "custom"
        ? props.application.invocation.tool.tool?.description.supportedProviders?.[0]
        : undefined;
    return (
        <Button onClick={() => navigate(AppRoutes.apps.creator({
            operation: "fork",
            applicationKind: "custom",
            workspace: projectId ?? "personal",
            name: sourceApplicationKind === "custom"
                ? props.application.metadata.name.replace(/^custom-/, "")
                : props.application.metadata.name,
            version: props.application.metadata.version,
            sourceApplicationKind,
            sourceProvider,
            returnTo: location.pathname + location.search,
        }))}>
            <Icon name="fork" mr={8} />
            Fork
        </Button>
    );
}
