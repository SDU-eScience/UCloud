import * as React from "react";
import * as Heading from "@/ui-components/Heading";
import {emptyPage} from "@/DefaultObjects";
import {joinToString} from "@/UtilityFunctions";
import {useLocation, useNavigate} from "react-router";
import {useCallback, useEffect} from "react";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import Grid from "@/ui-components/Grid";
import {AppCard, AppCardStyle, AppCardType} from "@/Applications/Card";
import * as Pagination from "@/Pagination";
import {Box, Flex, Icon, Input} from "@/ui-components";
import * as Pages from "./Pages";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";


const AppSearchBoxClass = injectStyle("app-search-box", k => `
    ${k} {
        align-items: center;
    }

    ${k} div {
        width: 300px;
        position: relative;
        align-items: center;
        justify-content: space-evenly;
    }

    ${k} input.search-field {
        width: 100%;
        padding-right: 2.5rem;
    }

    ${k} button {
        background: none;
        border: 0;
        padding: 0px 10px 1px 10px;
        cursor: pointer;
        position: absolute;
        right: 0;
        height: 2.4rem;
    }

    ${k} .search-icon {
        margin-right: 15px;
        margin-left: 15px;
    }
`);



export const AppSearchBox: React.FunctionComponent<{value?: string; hidden?: boolean}> = props => {
    const navigate = useNavigate();
    const inputRef = React.useRef<HTMLInputElement>(null);
    const [isHidden, setHidden] = React.useState(props.hidden ?? true);

    const onSearch = useCallback(() => {
        const queryCurrent = inputRef.current;
        if (!queryCurrent) return;

        const queryValue = queryCurrent.value;

        if (queryValue === "") return;

        navigate(AppRoutes.apps.search(queryValue));
    }, [inputRef.current]);


    return <Flex className={AppSearchBoxClass}>
        {isHidden ? null : (
            <Flex>
                <Input
                    className="search-field"
                    defaultValue={props.value}
                    inputRef={inputRef}
                    placeholder="Search for applications..."
                    onKeyUp={e => {
                        if (e.key === "Enter") {
                            onSearch();
                        }
                    }}
                    autoFocus
                />
                <button>
                    <Icon name="search" size={20} color="darkGray" my="auto" onClick={onSearch} />
                </button>
            </Flex>
        )}
        <Icon name="heroMagnifyingGlass" cursor="pointer" size="24" color="blue" className="search-icon" onClick={() =>
            setHidden(!isHidden)
        } />
    </Flex>;
}


interface SearchQuery {
    tags: string[];
    query: string;
    showAllVersions: boolean;
    page: number;
    itemsPerPage: number;
}

function readQuery(queryParams: string): SearchQuery {
    const tags: string[] = [];
    const tagsQuery = getQueryParamOrElse(queryParams, "tags", "");
    tagsQuery.split(",").forEach(it => {
        if (it !== "") {
            tags.push(it);
        }
    });
    const showAllVersions = getQueryParamOrElse(queryParams, "showAllVersions", "false") === "true";
    const query = getQueryParamOrElse(queryParams, "query", "");
    let itemsPerPage = parseInt(getQueryParamOrElse(queryParams, "itemsPerPage", "25"), 10);
    let page = parseInt(getQueryParamOrElse(queryParams, "page", "0"), 10);
    if (isNaN(itemsPerPage) || itemsPerPage <= 0) itemsPerPage = 25;
    if (isNaN(page) || page < 0) page = 0;

    return {query, tags, showAllVersions, itemsPerPage, page};
}

export const SearchResults: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const dispatch = useDispatch();
    const [results, fetchResults] = useCloudAPI<UCloud.Page<UCloud.compute.ApplicationSummaryWithFavorite>>(
        {noop: true},
        emptyPage
    );

    const queryParams = location.search;
    const parsedQuery = readQuery(queryParams);

    useEffect(() => {
        fetchResults(
            UCloud.compute.apps.searchApps({
                query: new URLSearchParams(queryParams).get("q") ?? "",
                itemsPerPage: 100,
                page: parsedQuery.page,
            })
        );
    }, [queryParams]);

    const favoriteStatus = useSelector<ReduxObject, ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        const favoriteApp = favoriteStatus.find(it => it.metadata.name === app.metadata.name);
        const isFavorite = favoriteApp !== undefined ? true : app.favorite;

        dispatch(toggleAppFavorite(app, !isFavorite));

        try {
            await callAPI(UCloud.compute.apps.toggleFavorite({
                appName: app.metadata.name
            }));
        } catch (e) {
            snackbarStore.addFailure("Failed to toggle favorite", false);
            dispatch(toggleAppFavorite(app, !isFavorite));
        }
    }, [favoriteStatus]);

    return <Box mx="auto" maxWidth="1340px">
        <Flex width="100%" mt="30px" justifyContent="space-between">
            <Heading.h2>Search results</Heading.h2>
            <Flex justifyContent="right">
                <AppSearchBox value={new URLSearchParams(queryParams).get("q") ?? ""} hidden={false} />
                <ContextSwitcher />
            </Flex>
        </Flex>
        <Box mt="30px" />

        <Pagination.List
            loading={results.loading}
            page={results.data}
            pageRenderer={page => (
                <Grid
                    width="100%"
                    gridTemplateColumns={`repeat(auto-fill, 312px)`}
                    gridGap="30px"
                >
                    {page.items.map(app => (
                        <AppCard
                            key={app.metadata.name}
                            title={app.metadata.title}
                            description={app.metadata.description}
                            logo={app.metadata.name}
                            type={AppCardType.APPLICATION}
                            cardStyle={AppCardStyle.WIDE}
                            link={Pages.run(app.metadata.name)}
                            onFavorite={onFavorite}
                            isFavorite={favoriteStatus.find(it => it.metadata.name === app.metadata.name) !== undefined ? true : app.favorite}
                            application={app}
                        />
                    ))}
                </Grid>
            )}
            onPageChanged={newPage => {
                navigate(buildQueryString("/applications/search", {
                    q: new URLSearchParams(queryParams).get("q") ?? "",
                    tags: joinToString(parsedQuery.tags),
                    showAllVersions: parsedQuery.showAllVersions.toString(),
                    page: newPage.toString(),
                    itemsPerPage: parsedQuery.itemsPerPage.toString(),
                }));
            }}
        />
    </Box>;
};
