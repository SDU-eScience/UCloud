import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Flex, Grid} from "@/ui-components";
import {AppCard, AppCardType} from "./Card";
import {useTitle} from "@/Navigation/Redux";
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

function link(group: ApplicationGroup): string {
    if (group.specification.defaultFlavor) {
        return AppRoutes.jobs.create(group.specification.defaultFlavor);
    } else {
        return AppRoutes.apps.group(group.metadata.id.toString());
    }
}

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
    useTitle(title);
    useSetRefreshFunction(refresh);
    const appSearch = useAppSearch();

    return (
        <div>
            <div className={Gradient}>
                <div className={GradientWithPolygons}>
                    <div className={OverviewStyle}>
                        <Flex alignItems={"center"}>
                            <h3>{title}</h3>
                            <Box ml="auto"/>
                            <UtilityBar onSearch={appSearch}/>
                        </Flex>

                        <Grid gridTemplateColumns={"repeat(auto-fit, minmax(430px, 1fr))"} gap={"16px"}>
                            {groups.map(section =>
                                <AppCard
                                    key={section.metadata.id}
                                    title={section.specification.title}
                                    logo={section.metadata.id.toString()}
                                    type={AppCardType.GROUP}
                                    description={section.specification.description}
                                    link={link(section)}
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