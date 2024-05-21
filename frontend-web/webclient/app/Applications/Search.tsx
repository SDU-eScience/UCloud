import * as React from "react";
import {displayErrorMessageOrDefault, doNothing} from "@/UtilityFunctions";
import {useLocation, useNavigate} from "react-router";
import {useCallback, useEffect} from "react";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import Grid from "@/ui-components/Grid";
import {AppCard, AppCardType} from "@/Applications/Card";
import {Box, Flex, Icon, Input} from "@/ui-components";
import * as Pages from "./Pages";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {emptyPage} from "@/Utilities/PageUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {ApplicationSummaryWithFavorite} from "@/Applications/AppStoreApi";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

export function useAppSearch(): (query: string) => void {
    const navigate = useNavigate();
    return useCallback(query => {
        navigate(AppRoutes.apps.search(query));
    }, [navigate]);
}

function readQuery(queryParams: string): string {
    return getQueryParamOrElse(queryParams, "q", "");
}

const OverviewStyle = injectStyle("search-results", k => `
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

const SearchResults: React.FunctionComponent = () => {
    const location = useLocation();
    const dispatch = useDispatch();
    const query = readQuery(location.search);
    const [results, fetchResults] = useCloudAPI(
        AppStore.search({query, itemsPerPage: 250}),
        emptyPage
    );

    const refresh = useCallback(() => {
        fetchResults(AppStore.search({query, itemsPerPage: 250})).then(doNothing);
    }, [query]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    usePage("Search results", SidebarTabId.APPLICATIONS);
    useSetRefreshFunction(refresh);

    const favoriteStatus = useSelector<ReduxObject, ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        const favoriteApp = favoriteStatus.find(it => it.metadata.name === app.metadata.name);
        const isFavorite = favoriteApp !== undefined ? true : app.favorite;

        dispatch(toggleAppFavorite(app, !isFavorite));

        try {
            await callAPI(AppStore.toggleStar({
                name: app.metadata.name
            }));
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to toggle favorite");
            dispatch(toggleAppFavorite(app, !isFavorite));
        }
    }, [favoriteStatus]);

    const appSearch = useAppSearch();

    return <div>
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <div className={OverviewStyle}>
                    <Flex alignItems={"center"}>
                        <h3>Search results</h3>
                        <Box ml="auto"/>
                        <UtilityBar onSearch={appSearch} initialSearchQuery={query}/>
                    </Flex>

                    <Grid gridTemplateColumns={"repeat(auto-fit, minmax(430px, 1fr))"} gap={"16px"}>
                        {results.data.items.map(app => (
                            <AppCard
                                key={app.metadata.name}
                                title={app.metadata.title}
                                description={app.metadata.description}
                                logo={app.metadata.name}
                                type={AppCardType.APPLICATION}
                                link={Pages.run(app.metadata.name)}
                                onFavorite={onFavorite}
                                isFavorite={favoriteStatus.find(it => it.metadata.name === app.metadata.name) !== undefined ? true : app.favorite}
                                application={app}
                            />
                        ))}
                    </Grid>
                </div>
            </div>
        </div>
    </div>
};


export default SearchResults;