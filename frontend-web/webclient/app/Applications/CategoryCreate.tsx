import * as React from "react";
import {useCallback, useState} from "react";
import {callAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import * as AppStore from "@/Applications/AppStoreApi";
import AppRoutes from "@/Routes";
import {useNavigate} from "react-router-dom";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Box, Button, Card, Flex, Icon, Input, Link, MainContainer, Text, TextArea} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {extractErrorMessage, doNothing} from "@/UtilityFunctions";
import {useProjectId} from "@/Project/Api";
import {checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";
import {Feature, hasFeature} from "@/Features";
import {KeyboardNavigation, SubmitShortcut, useSubmitShortcut} from "@/Applications/KeyboardNavigation";
import {DocumentTypography} from "@/ui-components/Markdown";
import {injectStyle} from "@/Unstyled";
import {Permission, ResourceAclEntry} from "@/UCloud/ResourceApi";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {useGlobal} from "@/Utilities/ReduxHooks";

const TITLE_KEY = "custom-category-title";
const DESCRIPTION_KEY = "custom-category-description";

const CategoryCreateHeaderClass = injectStyle("category-create-header", key => `
    ${key} {
        display: flex;
        margin: 32px 50px 24px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 24px 16px;
        }
    }
`);

const CategoryCreateContentClass = injectStyle("category-create-content", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 24px;
        max-width: 960px;
        margin: 0 50px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 0 16px;
        }
    }
`);

const CategoryCreateSubmitClass = injectStyle("category-create-submit", key => `
    ${key} {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 8px;
        margin: 0 0 48px;
    }

    @media (max-width: 600px) {
        ${key} {
            align-items: stretch;
            flex-direction: column;
        }

        ${key} > div:first-child {
            margin-right: 0 !important;
        }

        ${key} > a, ${key} > button {
            width: 100%;
        }
    }
`);

export default function CategoryCreate(): React.ReactNode {
    usePage("Create category", SidebarTabId.APPLICATIONS);
    const projectId = useProjectId();
    const navigate = useNavigate();
    const [, setLandingPage] = useGlobal("catalogLandingPage", AppStore.emptyLandingPage);

    const allowed = Client.userIsAdmin || (
        hasFeature(Feature.CONTAINER_REPOSITORIES) &&
        projectId != null &&
        checkIsWorkspaceAdmin()
    );

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [acl, setAcl] = useState<ResourceAclEntry[]>([]);

    const updateAcl = useCallback(async (group: string, permission: Permission | null) => {
        if (!projectId) return;

        const entity: ResourceAclEntry["entity"] = {type: "project_group", projectId, group};
        const permissions: Permission[] = permission === "EDIT"
            ? ["READ", "EDIT"]
            : permission === "READ" ? ["READ"] : [];

        setAcl(previous => [
            ...previous.filter(entry => entry.entity.type !== "project_group" || entry.entity.group !== group),
            ...(permissions.length === 0 ? [] : [{entity, permissions}]),
        ]);
    }, [projectId]);

    const submit = useCallback(async () => {
        if (loading) return;
        const title = (document.getElementById(TITLE_KEY) as HTMLInputElement).value.trim();
        const description = (document.getElementById(DESCRIPTION_KEY) as HTMLTextAreaElement).value.trim();
        if (!title) {
            setError("A category title is required.");
            return;
        }
        setLoading(true);
        setError(null);
        try {
            const result = await callAPI<{id: number}>(AppStore.createCustomCategory({
                kind: "Custom",
                specification: {title, description},
                acl,
            }));
            setLandingPage(AppStore.emptyLandingPage);
            navigate(AppRoutes.apps.category(result.id));
        } catch (e) {
            setError(extractErrorMessage(e as {request: XMLHttpRequest; response: any}));
            setLoading(false);
        }
    }, [acl, loading, navigate, setLandingPage]);

    useSubmitShortcut(submit, loading);

    if (!allowed) {
        return (
            <MainContainer
                main={
                    <Flex height="100%" flexDirection="column" alignItems="center" justifyContent="center" gap="16px">
                        <Text fontSize={18} fontWeight={600}>Custom category creation is not available</Text>
                        <Text color="textSecondary">You do not have permission to create a custom category.</Text>
                        <Button onClick={() => navigate(AppRoutes.apps.landing())}>Back</Button>
                    </Flex>
                }
            />
        );
    }

    return <MainContainer
        main={
            <DocumentTypography>
                <div className={CategoryCreateHeaderClass}>
                    <div>
                        <Heading.h2>New custom category</Heading.h2>
                        <div style={{maxWidth: "960px", color: "var(--textSecondary)"}}>
                            Create a workspace category for custom applications. Custom categories are
                            available to this workspace and its members.
                        </div>
                    </div>
                </div>
                <KeyboardNavigation>
                    <div className={CategoryCreateContentClass}>
                        <Card>
                            <Box p="16px">
                                <FieldGroup>
                                    <FieldRow
                                        title="Title"
                                        description="The name shown on the applications landing page."
                                        required
                                        error={error ?? undefined}
                                        control={<Input id={TITLE_KEY} width="100%" placeholder="My category" />}
                                    />
                                    <FieldRow
                                        title="Description"
                                        description="A short description of the category."
                                        control={<TextArea id={DESCRIPTION_KEY} width="100%" rows={5}
                                            placeholder="What can users do in this category?" />}
                                    />
                                </FieldGroup>
                            </Box>
                        </Card>

                        {projectId == null ? null : (
                            <Card>
                                <Box p="16px">
                                    <Heading.h3>Access</Heading.h3>
                                    <Text color="textSecondary" mb="12px">
                                        By default, only project administrators can use this category. Grant access to
                                        project groups below.
                                    </Text>
                                    <PermissionsTable
                                        acl={acl}
                                        anyGroupHasPermission={acl.some(entry => entry.permissions.length !== 0)}
                                        showMissingPermissionHelp={false}
                                        warning="No project groups have access to this category."
                                        title="category"
                                        updateAcl={updateAcl}
                                    />
                                </Box>
                            </Card>
                        )}

                        <div className={CategoryCreateSubmitClass}>
                            <Link to={AppRoutes.apps.landing()}>
                                <Button onClick={doNothing} color={"secondaryMain"}>Cancel</Button>
                            </Link>
                            <Button onClick={submit} color={"successMain"} disabled={loading}>
                                {loading ? <Icon name={"refresh"} spin /> : null}
                                Create category<SubmitShortcut />
                            </Button>
                        </div>
                    </div>
                </KeyboardNavigation>
            </DocumentTypography>
        }
    />;
}
