import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Flex, Grid, Link} from "@/ui-components";
import {AppCard, AppCardType} from "./Card";
import {usePage} from "@/Navigation/Redux";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useLocation} from "react-router";
import {useAppSearch} from "./Search";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {doNothing} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {ApplicationGroup} from "@/Applications/AppStoreApi";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {injectStyle} from "@/Unstyled";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {AppCard2} from "@/Applications/Landing";
import BaseLink from "@/ui-components/BaseLink";

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

const ApplicationsOverview: React.FunctionComponent = () => {
    const location = useLocation();
    const idParam = getQueryParam(location.search, "categoryId");
    const id = parseInt(idParam ?? "-1");

    const [categoryState, fetchCategory] = useCloudAPI(AppStore.retrieveCategory({id}), null);
    const category = categoryState.data;
    const groups = category?.status?.groups ?? [];

    const refresh = useCallback(() => {
        fetchCategory(AppStore.retrieveCategory({id})).then(doNothing);
    }, [id]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    const title = category?.specification?.title ?? "Applications";
    usePage(title, SidebarTabId.APPLICATIONS);
    useSetRefreshFunction(refresh);
    const appSearch = useAppSearch();

    return (
        <div>
            <div className={Gradient}>
                <div className={GradientWithPolygons}>
                    <div className={OverviewStyle}>
                        <Flex alignItems={"center"}>
                            <h3 className="title">{title}</h3>
                            <Box ml="auto"/>
                            <UtilityBar onSearch={appSearch}/>
                        </Flex>

                        <Grid gridTemplateColumns={"repeat(auto-fit, minmax(500px, 1fr))"} columnGap={"16px"} rowGap={"16px"}>
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
                        </Grid>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ApplicationsOverview;