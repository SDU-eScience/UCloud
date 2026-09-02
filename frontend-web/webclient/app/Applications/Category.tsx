import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Flex, Grid, MainContainer} from "@/ui-components";
import {usePage} from "@/Navigation/Redux";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router-dom";
import {useAppSearch} from "./Search";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {displayErrorMessageOrDefault, doNothing, extractErrorMessage} from "@/UtilityFunctions";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {AppCard2} from "@/Applications/Landing";
import {useDiscovery} from "@/Applications/Hooks";
import {injectStyle} from "@/Unstyled";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import {fetchAll} from "@/Utilities/PageUtilities";
import {Client} from "@/Authentication/HttpClientInstance";
import {Feature, hasFeature} from "@/Features";
import {Button} from "@/ui-components/Button";
import Icon from "@/ui-components/Icon";
import Text from "@/ui-components/Text";
import {TooltipV2} from "@/ui-components/Tooltip";
import {dialogStore} from "@/Dialog/DialogStore";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {Permission, ResourceAclEntry} from "@/UCloud/ResourceApi";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {SettingsAction, SettingsSection} from "@/ui-components/SettingsComponents";
import {addStandardDialog} from "@/UtilityComponents";
import * as Heading from "@/ui-components/Heading";
import {useGlobal} from "@/Utilities/ReduxHooks";

const OverviewStyle = injectStyle("app-overview", k => `
    ${k} {
        margin: 0 auto;
        padding-top: 16px;
        padding-bottom: 16px;
        display: flex;
        flex-direction: column;
        gap: 16px;
        max-width: 1100px;
        min-width: 600px;
        min-height: 100vh;
    }
`);

const ApplicationsCategory: React.FunctionComponent = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const projectId = useProjectId();
    const idParam = getQueryParam(location.search, "categoryId");
    const id = parseInt(idParam ?? "-1");

    const [discovery] = useDiscovery();
    const [categoryState, fetchCategory] = useCloudAPI(AppStore.retrieveCategory({id, ...discovery}), null);
    const category = categoryState.data;
    const groups = category?.status?.groups ?? [];
    const [editableCategory, setEditableCategory] = React.useState<AppStore.AppCatalogCustomCategory | null>(null);
    const [accessRevision, setAccessRevision] = React.useState(0);

    const refresh = useCallback(() => {
        fetchCategory(AppStore.retrieveCategory({id, ...discovery})).then(doNothing);
    }, [id, discovery]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useEffect(() => {
        if (categoryState.error) {
            displayErrorMessageOrDefault(categoryState.error.why, "Failed to fetch category");
        }
    }, [categoryState.error])

    useEffect(() => {
        let cancelled = false;
        setEditableCategory(null);
        fetchAll(next => callAPI(AppStore.browseCustomCategories({itemsPerPage: 250, next}))).then(categories => {
            if (cancelled) return;
            const matchingCategory = categories.find(item => item.id === id || item.backedBy === id);
            const canEdit = matchingCategory?.permissions.myself.includes("EDIT") === true;
            setEditableCategory(canEdit ? matchingCategory ?? null : null);
        }).catch(() => {
            if (cancelled) return;
            setEditableCategory(null);
        });
        return () => {
            cancelled = true;
        };
    }, [id, projectId, accessRevision]);

    const title = category?.specification?.title ?? "Applications";
    usePage(title, SidebarTabId.APPLICATIONS);
    const refreshAll = useCallback(() => {
        refresh();
        setAccessRevision(revision => revision + 1);
    }, [refresh]);
    useSetRefreshFunction(refreshAll);
    const appSearch = useAppSearch();

    const canCreateApplication = !!editableCategory && (hasFeature(Feature.CONTAINER_REPOSITORIES));
    const createApplication = useCallback(() => {
        if (!editableCategory) return;
        navigate(AppRoutes.apps.creator({
            operation: "newCustom",
            applicationKind: "custom",
            workspace: projectId ?? "personal",
            category: editableCategory.id,
            returnTo: location.pathname + location.search,
        }));
    }, [editableCategory, navigate, projectId, location]);
    const canManageCategory = editableCategory?.permissions.myself.includes("ADMIN") === true;
    const openCategoryManagement = useCallback(() => {
        if (!editableCategory) return;
        dialogStore.addDialog(
            <CategoryManagementDialog
                category={editableCategory}
                isEmpty={groups.length === 0}
                categoryLoaded={category != null}
                showAcl={editableCategory.owner.project != null}
                onUpdated={refreshAll}
                onDeleted={() => navigate(AppRoutes.apps.landing())}
            />,
            doNothing,
            true,
            largeModalStyle,
        );
    }, [category, editableCategory, groups.length, navigate, refreshAll]);

    return (
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer main={<>
                    <Flex mb="16px" alignItems={"center"}>
                        <h3 className="title">{title}</h3>
                        <Box ml="auto" />
                        <UtilityBar onSearch={appSearch} trailing={
                            <>
                                {!canManageCategory ? null : (
                                    <TooltipV2 tooltip="Manage this category" triggerStyle={{display: "inline-flex", alignItems: "center"}}>
                                        <Icon name="heroCog6Tooth" size={24} cursor="pointer" color="textPrimary" onClick={openCategoryManagement} />
                                    </TooltipV2>
                                )}
                                {!canCreateApplication ? null : (
                                    <TooltipV2 tooltip="Create an application in this category" triggerStyle={{display: "inline-flex", alignItems: "center"}}>
                                        <Icon name="heroPlus" size={24} cursor="pointer" color="textPrimary" onClick={createApplication} />
                                    </TooltipV2>
                                )}
                            </>
                        } />
                    </Flex>

                    {groups.length === 0 ? (
                        <EmptyCategoryPlaceholder
                            canCreateApplication={canCreateApplication}
                            onCreateApplication={createApplication}
                        />
                    ) : (
                        <AppGrid>
                            {groups.map(section =>
                                <AppCard2
                                    fullWidth
                                    key={section.metadata.id}
                                    title={section.specification.title}
                                    isApplication={false}
                                    description={section.specification.description}
                                    name={section.metadata.id.toString()}
                                    applicationName={section.specification.defaultFlavor}
                                />
                            )}
                        </AppGrid>
                    )}
                </>}
                />
            </div>
        </div>
    );
};

const EmptyCategoryPlaceholderClass = injectStyle("category-empty-placeholder", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 16px;
        padding: 64px 32px;
        text-align: center;
    }
`);

function EmptyCategoryPlaceholder(props: {
    canCreateApplication: boolean;
    onCreateApplication: () => void;
}): React.ReactNode {
    return (
        <div className={EmptyCategoryPlaceholderClass}>
            <Icon name="heroSquaresPlus" size="48" color="textSecondary" />
            <div>
                <Text fontSize={16} fontWeight={600} mb="4px">
                    This category is empty
                </Text>
                <Text fontSize={14} color="textSecondary">
                    {props.canCreateApplication
                        ? "Add an application to this category to get started."
                        : "No applications have been added to this category yet."}
                </Text>
            </div>
            {props.canCreateApplication ? (
                <Button onClick={props.onCreateApplication}>
                    <Icon name="heroPlus" mr={6} />
                    Create application
                </Button>
            ) : null}
        </div>
    );
}

const CategoryManagementClass = injectStyle("category-management", k => `
    ${k} {
        display: flex;
        flex-direction: column;
    }

    ${k} .category-management-header {
        padding-bottom: 32px;
    }
`);

function categoryAcl(category: AppStore.AppCatalogCustomCategory): ResourceAclEntry[] {
    return (category.permissions.others ?? []).flatMap(entry => {
        const entity = entry.entity;
        if (entity.type !== "project_group" || entity.projectId == null || entity.group == null) return [];
        const permissions = entry.permissions.filter(permission => permission === "READ" || permission === "EDIT") as Permission[];
        return [{
            entity: {
                type: "project_group",
                projectId: entity.projectId,
                group: entity.group,
            },
            permissions,
        }];
    });
}

function CategoryManagementDialog(props: {
    category: AppStore.AppCatalogCustomCategory;
    isEmpty: boolean;
    categoryLoaded: boolean;
    showAcl: boolean;
    onUpdated: () => void;
    onDeleted: () => void;
}): React.ReactNode {
    const projectId = useProjectId();
    const categoryId = props.category.id;
    const [, setLandingPage] = useGlobal("catalogLandingPage", AppStore.emptyLandingPage);
    const [acl, setAcl] = React.useState<ResourceAclEntry[]>(() => categoryAcl(props.category));
    const [busy, setBusy] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);

    const updateAcl = useCallback(async (group: string, permission: Permission | null) => {
        if (!projectId || busy) return;

        const entity: ResourceAclEntry["entity"] = {type: "project_group", projectId, group};
        const permissions: Permission[] = permission === "EDIT"
            ? ["READ", "EDIT"]
            : permission === "READ" ? ["READ"] : [];

        setBusy(true);
        setError(null);
        try {
            await callAPI(AppStore.updateCustomCategoryAcl({
                id: categoryId.toString(),
                added: permissions.length === 0 ? [] : [{entity, permissions}],
                deleted: [entity],
            }));
            setAcl(previous => [
                ...previous.filter(entry => entry.entity.type !== "project_group" || entry.entity.group !== group),
                ...(permissions.length === 0 ? [] : [{entity, permissions}]),
            ]);
            props.onUpdated();
        } catch (cause) {
            setError("Could not update category access. " + extractErrorMessage(cause as {request: XMLHttpRequest; response: any}));
        } finally {
            setBusy(false);
        }
    }, [busy, categoryId, projectId, props.onUpdated]);

    const deleteCategory = useCallback(async () => {
        if (!props.isEmpty || busy) return;

        setBusy(true);
        setError(null);
        try {
            await callAPI(AppStore.deleteCustomCategory({id: categoryId}));
            setLandingPage(AppStore.emptyLandingPage);
            dialogStore.success();
            props.onDeleted();
        } catch (cause) {
            setError("Could not delete category. " + extractErrorMessage(cause as {request: XMLHttpRequest; response: any}));
            setBusy(false);
        }
    }, [busy, categoryId, props.isEmpty, props.onDeleted, setLandingPage]);

    const requestDeleteCategory = useCallback(() => {
        addStandardDialog({
            title: "Delete category?",
            message: "This will permanently delete the empty category.",
            confirmText: "Delete category",
            confirmButtonColor: "errorMain",
            cancelButtonColor: "primaryMain",
            addToFront: true,
            onConfirm: deleteCategory,
        });
    }, [deleteCategory]);

    const anyGroupHasPermission = acl.some(entry => entry.permissions.length !== 0);

    return (
        <div className={CategoryManagementClass}>
            <div className="category-management-header">
                <Heading.h3>{props.category.specification.title}</Heading.h3>
            </div>

            {props.showAcl ? (
                <SettingsSection
                    title="Access"
                    description="Choose which project groups can read or edit this category."
                    mb={24}
                >
                    <PermissionsTable
                        acl={acl}
                        anyGroupHasPermission={anyGroupHasPermission}
                        showMissingPermissionHelp={false}
                        warning="No project groups have access to this category."
                        title="category"
                        readLabel="Use"
                        readIcon="heroPlay"
                        writeLabel="Create"
                        writeIcon="heroSquaresPlus"
                        updateAcl={updateAcl}
                    />
                </SettingsSection>
            ) : null}

            <SettingsSection title="Danger zone" mb={24}>
                <SettingsAction
                    title="Delete category"
                    description={!props.categoryLoaded
                        ? "Loading category contents..."
                        : props.isEmpty
                        ? "This category is empty and can be deleted."
                        : "This category cannot be deleted because it contains applications."}
                    action={
                        <Button color="errorMain" disabled={!props.categoryLoaded || !props.isEmpty || busy} onClick={requestDeleteCategory}>
                            <Icon name="heroTrash" />
                            Delete category
                        </Button>
                    }
                />
            </SettingsSection>

            {error ? <Text color="errorMain" mb="16px">{error}</Text> : null}
        </div>
    );
}

export function AppGrid(props: React.PropsWithChildren): React.ReactNode {
    return <Grid gridTemplateColumns={"repeat(auto-fit, minmax(500px, 1fr))"} columnGap={"16px"} rowGap={"16px"}>
        {props.children}
    </Grid>
}

export default ApplicationsCategory;
