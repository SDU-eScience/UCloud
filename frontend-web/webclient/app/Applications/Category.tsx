import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Button, Flex, Grid, MainContainer} from "@/ui-components";
import {usePage} from "@/Navigation/Redux";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router-dom";
import {useAppSearch} from "./Search";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {doNothing} from "@/UtilityFunctions";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {AppCard2} from "@/Applications/Landing";
import {useDiscovery} from "@/Applications/Hooks";
import {injectStyle} from "@/Unstyled";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import {fetchAll} from "@/Utilities/PageUtilities";

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
    const [hasCustomGroup, setHasCustomGroup] = React.useState(false);
    const [accessRevision, setAccessRevision] = React.useState(0);

    const refresh = useCallback(() => {
        fetchCategory(AppStore.retrieveCategory({id, ...discovery})).then(doNothing);
    }, [id, discovery]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useEffect(() => {
        let cancelled = false;
        setEditableCategory(null);
        setHasCustomGroup(false);
        Promise.all([
            fetchAll(next => callAPI(AppStore.browseCustomCategories({itemsPerPage: 250, next}))),
            callAPI(AppStore.browseCustomGroups({itemsPerPage: 1})),
        ]).then(([categories, customGroups]) => {
            if (cancelled) return;
            const matchingCategory = categories.find(item => item.id === id || item.backedBy === id);
            const canEdit = matchingCategory?.permissions.myself.includes("EDIT") === true;
            setEditableCategory(canEdit ? matchingCategory ?? null : null);
            setHasCustomGroup(customGroups.items.length > 0);
        }).catch(() => {
            if (cancelled) return;
            setEditableCategory(null);
            setHasCustomGroup(false);
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

    return (
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer main={<>
                    <Flex mb="16px" alignItems={"center"}>
                        <h3 className="title">{title}</h3>
                        <Box ml="auto" />
                        <UtilityBar onSearch={appSearch} leading={
                            !editableCategory || !hasCustomGroup ? null : (
                                <Button height="25px" onClick={() => navigate(AppRoutes.apps.creator({
                                    operation: "newCustom",
                                    applicationKind: "custom",
                                    workspace: projectId ?? "personal",
                                    category: editableCategory.id,
                                    returnTo: location.pathname + location.search,
                                }))}>
                                    Create application
                                </Button>
                            )
                        } />
                    </Flex>

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
                </>}
                />
            </div>
        </div>
    );
};

export function AppGrid(props: React.PropsWithChildren): React.ReactNode {
    return <Grid gridTemplateColumns={"repeat(auto-fit, minmax(500px, 1fr))"} columnGap={"16px"} rowGap={"16px"}>
        {props.children}
    </Grid>
}

export default ApplicationsCategory;
