import * as React from "react";
import {doNothing} from "@/UtilityFunctions";
import {useLocation, useNavigate} from "react-router";
import {useCallback, useEffect} from "react";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useCloudAPI} from "@/Authentication/DataHook";
import {Box, Flex, MainContainer} from "@/ui-components";
import AppRoutes from "@/Routes";
import {emptyPage} from "@/Utilities/PageUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useDiscovery} from "@/Applications/Hooks";
import {AppCard2} from "./Landing";
import {AppGrid} from "@/Applications/Category";
import {NoResultsBody} from "@/UtilityComponents";

export function useAppSearch(): (query: string) => void {
    const navigate = useNavigate();
    return useCallback(query => {
        navigate(AppRoutes.apps.search(query));
    }, [navigate]);
}

function readQuery(queryParams: string): string {
    return getQueryParamOrElse(queryParams, "q", "");
}

const SearchResults: React.FunctionComponent = () => {
    const location = useLocation();
    const query = readQuery(location.search);
    const [discovery] = useDiscovery();
    const [results, fetchResults] = useCloudAPI(
        AppStore.search({query, itemsPerPage: 250}),
        emptyPage
    );

    const refresh = useCallback(() => {
        fetchResults(AppStore.search({query, itemsPerPage: 250, ...discovery})).then(doNothing);
    }, [query, discovery]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    usePage("Search results", SidebarTabId.APPLICATIONS);
    useSetRefreshFunction(refresh);

    const appSearch = useAppSearch();

    return (

        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer
                    main={<>
                        <Flex mb="16px" alignItems={"center"}>
                            <h3>Search results</h3>
                            <Box ml="auto" />
                            <UtilityBar onSearch={appSearch} initialSearchQuery={query} />
                        </Flex>

                        {results.data.items.length !== 0 ? null : (
                            <NoResultsBody title={`No applications found with query '${query}'`} children={undefined} />
                        )}

                        <AppGrid>
                            {results.data.items.map(app => (
                                <AppCard2
                                    key={app.metadata.name}
                                    title={app.metadata.title}
                                    isApplication
                                    description={app.metadata.description}
                                    name={app.metadata.name}
                                    fullWidth
                                    applicationName={app.metadata.name}
                                />
                            ))}
                        </AppGrid>
                    </>}
                />
            </div>
        </div>
    );
};


export default SearchResults;