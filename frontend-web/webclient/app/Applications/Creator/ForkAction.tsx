import * as React from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {Application, customAppsWorkspaceAdmin} from "@/Applications/AppStoreApi";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import {Icon} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";

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
        <TooltipV2 tooltip="Fork this application" triggerStyle={{display: "inline-flex", alignItems: "center"}}>
            <Icon
                name="fork"
                size={24}
                cursor="pointer"
                color="textPrimary"
                onClick={() => navigate(AppRoutes.apps.creator({
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
                }))}
            />
        </TooltipV2>
    );
}
