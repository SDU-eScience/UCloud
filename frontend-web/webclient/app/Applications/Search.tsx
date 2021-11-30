import * as React from "react";
import {Box, Button, Checkbox, Flex, Input, Label, SelectableText, SelectableTextWrapper, Stamp} from "@/ui-components";
import {emptyPage, KeyCode} from "@/DefaultObjects";
import {joinToString, stopPropagation} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {useHistory} from "react-router";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {searchPage} from "@/Utilities/SearchUtilities";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {GridCardGroup} from "@/ui-components/Grid";
import {ApplicationCard} from "@/Applications/Card";
import * as Pagination from "@/Pagination";
import {SmallScreenSearchField} from "@/Navigation/Header";
import {BrowseType} from "@/Resource/BrowseType";
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

export const SearchWidget: React.FunctionComponent<{partOfResults?: boolean}> = ({partOfResults = false}) => {
    const history = useHistory();
    const queryParams = history.location.search;
    const tagRef = useRef<HTMLInputElement>(null);
    const searchRef = useRef<HTMLInputElement>(null);
    const [tags, setTags] = useState<Set<string>>(new Set());
    const [showAllVersions, setShowAllVersions] = useState(false);

    const addTag = useCallback((tag: string) => {
        setTags(tags => {
            const newTags = new Set<string>();
            tags.forEach(it => newTags.add(it));
            newTags.add(tag);
            return newTags;
        });
    }, [tags]);

    const removeTag = useCallback((tag: string) => {
        const newTags = new Set<string>();
        tags.forEach(it => newTags.add(it));
        newTags.delete(tag);
        setTags(newTags);
    }, [tags]);

    const clearTags = useCallback(() => {
        const newTags = new Set<string>();
        setTags(newTags);
    }, [setTags]);

    const toggleShowAllVersions = useCallback(() => {
        setShowAllVersions(!showAllVersions);
    }, [showAllVersions]);

    const parsedQuery = readQuery(queryParams);

    // useLayoutEffect to make sure that the refs have loaded
    useLayoutEffect(() => {
        const {tags, showAllVersions, query} = readQuery(queryParams);
        clearTags();
        tags.forEach(it => addTag(it));
        setShowAllVersions(showAllVersions);
        searchRef.current!.value = query;
    }, [queryParams]);

    function onSearch(e?: React.FormEvent<HTMLFormElement>): void {
        e?.preventDefault();
        let tagString = "";
        {
            const potentialTag = tagRef.current!.value;
            if (potentialTag !== "") {
                addTag(tagRef.current!.value);
                tagRef.current!.value = "";
            }

            const allTags = [...tags];
            if (potentialTag !== "") {
                allTags.push(potentialTag);
            }
            tagString = joinToString(allTags);
        }

        history.push(searchPage("applications", {
            query: searchRef.current!.value,
            tags: tagString,
            showAllVersions: showAllVersions.toString(),
            itemsPerPage: parsedQuery.itemsPerPage.toString(),
            page: parsedQuery.page.toString()
        }));
    }

    return (
        <Flex
            flexDirection="column"
            pl="0.5em"
            pr="0.5em"
            maxWidth={"500px"}
            margin={"0 auto"}
            mb={partOfResults ? "50px" : undefined}
        >
            <Box mt="0.5em">
                <form onSubmit={e => onSearch(e)}>
                    <Label>
                        <Heading.h5 pb="0.3em" pt="0.5em">Application name</Heading.h5>
                        <Input ref={searchRef} />
                    </Label>

                    <Label>
                        <Heading.h5 pb="0.3em" pt="0.5em">Tags</Heading.h5>
                        <SearchStamps
                            clearAll={clearTags}
                            onStampRemove={stamp => removeTag(stamp)}
                            stamps={tags}
                        />
                        <Input
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            width="100%"
                            ref={tagRef}
                            onKeyDown={e => {
                                if (e.keyCode === KeyCode.ENTER) {
                                    e.preventDefault();
                                    addTag(tagRef.current!.value);
                                    tagRef.current!.value = "";
                                }
                            }}
                            placeholder="Add tag with enter..."
                        />
                    </Label>
                    <Heading.h5 pb="0.3em" pt="0.5em">Options</Heading.h5>
                    <Label>
                        Show all versions
                        <Checkbox
                            checked={showAllVersions}
                            onChange={stopPropagation}
                            onClick={toggleShowAllVersions}
                        />
                    </Label>
                    <Button mt="0.5em" type="submit" fullWidth color="blue">Search</Button>
                </form>
            </Box>
        </Flex>
    );
};

export const SearchResults: React.FunctionComponent<{entriesPerPage: number}> = ({entriesPerPage}) => {
    const history = useHistory();
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
    }, [queryParams, entriesPerPage]);

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
