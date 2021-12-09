import * as React from "react";
import {Box, Stamp} from "@/ui-components";
import {emptyPage} from "@/DefaultObjects";
import {joinToString} from "@/UtilityFunctions";
import {useHistory} from "react-router";
import {useEffect} from "react";
import {searchPage} from "@/Utilities/SearchUtilities";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {GridCardGroup} from "@/ui-components/Grid";
import {ApplicationCard} from "@/Applications/Card";
import * as Pagination from "@/Pagination";
import {SmallScreenSearchField} from "@/Navigation/Header";
import {FilesSearchTabs} from "@/Files/FilesSearchTabs";

interface SearchQuery {
    tags: string[];
    query: string;
    showAllVersions: boolean;
    page: number;
    itemsPerPage: number;
}

interface SearchStampsProps {
    stamps: Set<string>;
    onStampRemove: (stamp: string) => void;
    clearAll: () => void;
}

export const SearchStamps: React.FunctionComponent<SearchStampsProps> = ({stamps, onStampRemove, clearAll}) => {
    return <Box pb="5px">
        {[...stamps].map(l => (
            <Stamp onClick={() => onStampRemove(l)} ml="2px" mt="2px" color="blue" key={l} text={l} />))}
        {stamps.size > 1 ? (<Stamp ml="2px" mt="2px" color="red" onClick={() => clearAll()} text="Clear all" />) : null}
    </Box>;
};


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

export const SearchResults: React.FunctionComponent<{entriesPerPage: number}> = ({entriesPerPage}) => {
    const history = useHistory();
    const [, invokeCommand] = useCloudCommand();
    const [results, fetchResults] = useCloudAPI<UCloud.Page<UCloud.compute.ApplicationSummaryWithFavorite>>(
        {noop: true},
        emptyPage
    );

    const queryParams = history.location.search;
    const parsedQuery = readQuery(queryParams);

    useEffect(() => {
        fetchResults(
            UCloud.compute.apps.searchApps({
                query: new URLSearchParams(queryParams).get("q") ?? "",
                itemsPerPage: 100,
                page: 0
            })
        );
    }, [queryParams, /* This isn't needed for this one, is it? */entriesPerPage]);

    const toggleFavorite = React.useCallback(async (appName: string, appVersion: string) => {
        await invokeCommand(UCloud.compute.apps.toggleFavorite({appName, appVersion}));
        fetchResults(
            UCloud.compute.apps.searchApps({
                query: new URLSearchParams(queryParams).get("q") ?? "",
                itemsPerPage: 100,
                page: 0
            })
        );
    }, [fetch]);

    return <>
        <FilesSearchTabs active={"APPLICATIONS"} />
        <SmallScreenSearchField />
        <Pagination.List
            loading={results.loading}
            page={results.data}
            pageRenderer={page => (
                <GridCardGroup>
                    {page.items.map(app => (
                        <ApplicationCard
                            key={`${app.metadata.name}${app.metadata.version}`}
                            app={app}
                            onFavorite={toggleFavorite}
                            isFavorite={app.favorite}
                            tags={app.tags}
                        />))
                    }
                </GridCardGroup>
            )}
            onPageChanged={newPage => {
                history.push(searchPage("applications", {
                    query: parsedQuery.query,
                    tags: joinToString(parsedQuery.tags),
                    showAllVersions: parsedQuery.showAllVersions.toString(),
                    page: newPage.toString(),
                    itemsPerPage: parsedQuery.itemsPerPage.toString(),
                }));
            }}
        />
    </>;
};
