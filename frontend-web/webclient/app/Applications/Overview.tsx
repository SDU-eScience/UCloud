import * as React from "react";
import {useCallback, useEffect} from "react";
import {Box, Flex, Grid, MainContainer} from "@/ui-components";
import {usePage} from "@/Navigation/Redux";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useLocation} from "react-router";
import {useAppSearch} from "./Search";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {doNothing} from "@/UtilityFunctions";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {AppCard2} from "@/Applications/Landing";

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
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer main={<>
                    <Flex mb="16px" alignItems={"center"}>
                        <h3 className="title">{title}</h3>
                        <Box ml="auto" />
                        <UtilityBar onSearch={appSearch} />
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

export default ApplicationsOverview;