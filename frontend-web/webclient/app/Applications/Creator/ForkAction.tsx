import * as React from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {Application} from "@/Applications/AppStoreApi";
import {Client} from "@/Authentication/HttpClientInstance";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import {Button, Icon} from "@/ui-components";
import {checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";
import {Feature, hasFeature} from "@/Features";

export function ApplicationForkAction(props: {application: Application}): React.ReactNode {
    const projectId = useProjectId();
    const navigate = useNavigate();
    const location = useLocation();
    const canFork = hasFeature(Feature.CONTAINER_REPOSITORIES) &&
        (Client.userIsAdmin || (projectId != null && checkIsWorkspaceAdmin())
    );
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
