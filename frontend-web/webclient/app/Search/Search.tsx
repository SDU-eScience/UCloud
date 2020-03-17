import {AdvancedSearchRequest as AppSearchRequest, DetailedApplicationSearchReduxState} from "Applications";
import {ApplicationCard} from "Applications/Card";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import {setAppQuery} from "Applications/Redux/DetailedApplicationSearchActions";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage, HeaderSearchType, ReduxObject} from "DefaultObjects";
import {AdvancedSearchRequest, DetailedFileSearchReduxState, FileType} from "Files";
import DetailedFileSearch from "Files/DetailedFileSearch";
import {EmbeddedFileTable} from "Files/FileTable";
import {setFilename, toggleFilesSearchHidden} from "Files/Redux/DetailedFileSearchActions";
import {MainContainer} from "MainContainer/MainContainer";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, SelectableText, SelectableTextWrapper} from "ui-components";
import {GridCardGroup} from "ui-components/Grid";
import Hide from "ui-components/Hide";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {favoriteApplicationFromPage} from "Utilities/ApplicationUtilities";
import {searchPage} from "Utilities/SearchUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {prettierString} from "UtilityFunctions";
import {SearchProps, SimpleSearchOperations, SimpleSearchStateProps} from ".";
import * as SSActions from "./Redux/SearchActions";

function Search(props: SearchProps) {
    React.useEffect(() => {
        props.toggleAdvancedSearch();
        props.setActivePage();
        const q = query(props);
        props.setSearch(q);
        props.setPrioritizedSearch(props.match.params.priority as HeaderSearchType);
        props.setRefresh(() => fetchAll());
        fetchAll();
        return () => {
            props.toggleAdvancedSearch();
            props.clear();
            props.setRefresh();
        };
    }, []);

    React.useEffect(() => {
        props.setPrioritizedSearch(props.match.params.priority as HeaderSearchType);
    }, [props.match.params.priority]);

    React.useEffect(() => {
        props.setSearch(props.search);
        fetchAll();
    }, [query(props)]);

    const setPath = (text: string) => props.history.push(searchPage(text.toLocaleLowerCase(), props.search));

    function fetchAll(itemsPerPage?: number) {
        props.searchFiles(fileSearchBody(
            props.fileSearch,
            props.search,
            itemsPerPage || props.files.itemsPerPage,
            props.files.pageNumber
        ));
        props.searchApplications(applicationSearchBody(
            props.applicationSearch,
            props.search,
            itemsPerPage || props.applications.itemsPerPage,
            props.applications.pageNumber
        ));
        props.history.push(searchPage(props.match.params.priority, props.search));
    }

    const refreshFiles = () => props.searchFiles(fileSearchBody(
        props.fileSearch,
        props.search,
        props.files.itemsPerPage,
        props.files.pageNumber
    ));

    const {files, applications, applicationsLoading} = props;

    const Tab = ({searchType}: {searchType: HeaderSearchType}): JSX.Element => (
        <SelectableText
            cursor="pointer"
            fontSize={3}
            onClick={() => setPath(searchType)}
            selected={priority === searchType}
            mr="1em"
        >
            {prettierString(searchType)}
        </SelectableText>
    );

    const allowedSearchTypes: HeaderSearchType[] = ["files", "applications"];

    let main: React.ReactNode = null;
    const {priority} = props.match.params;
    const entriesPerPage = (

        <Box my="8px">
            <Spacer
                left={null}
                right={(
                    <Pagination.EntriesPerPageSelector
                        onChange={itemsPerPage => fetchAll(itemsPerPage)}
                        content={`${prettierString(priority)} per page`}
                        entriesPerPage={
                            priority === "files" ? props.files.itemsPerPage :
                                props.applications.itemsPerPage
                        }
                    />
                )}
            />
        </Box>
    );
    if (priority === "files") {
        main = (
            <>
                <Hide xxl xl lg>
                    <DetailedFileSearch cantHide />
                    {entriesPerPage}
                </Hide>
                <EmbeddedFileTable
                    onPageChanged={page => props.searchFiles(
                        fileSearchBody(props.fileSearch, props.search, props.files.itemsPerPage, page)
                    )}
                    page={files ? files : emptyPage}
                    onReloadRequested={refreshFiles}
                    includeVirtualFolders={false}
                />
            </>
        );
    } else if (priority === "applications") {
        main = (
            <>
                <Hide xxl xl lg>
                    <DetailedApplicationSearch />
                    {entriesPerPage}
                </Hide>
                <Pagination.List
                    loading={applicationsLoading}
                    pageRenderer={({items}) => (
                        <GridCardGroup>
                            {items.map(app => (
                                <ApplicationCard
                                    onFavorite={async () => props.setApplicationsPage(await favoriteApplicationFromPage({
                                        name: app.metadata.name,
                                        version: app.metadata.version,
                                        page: props.applications,
                                        client: Client
                                    }))}
                                    key={`${app.metadata.name}${app.metadata.version}`}
                                    app={app}
                                    isFavorite={app.favorite}
                                    tags={app.tags}
                                />))}
                        </GridCardGroup>
                    )}
                    page={applications}
                    onPageChanged={pageNumber => props.searchApplications(
                        applicationSearchBody(
                            props.applicationSearch,
                            props.search,
                            props.applications.itemsPerPage,
                            pageNumber
                        ))
                    }
                />
            </>
        );
    }

    return (
        <MainContainer
            header={(
                <React.Fragment>
                    <SelectableTextWrapper>
                        {allowedSearchTypes.map((pane, index) => <Tab searchType={pane} key={index} />)}
                    </SelectableTextWrapper>
                    <Hide md sm xs>
                        <Spacer left={null} right={entriesPerPage} />
                    </Hide>
                </React.Fragment>
            )}
            main={main}
        />
    );
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    setFilesLoading: loading => dispatch(SSActions.setFilesLoading(loading)),
    setApplicationsLoading: loading => dispatch(SSActions.setApplicationsLoading(loading)),
    clear: () => dispatch(SSActions.receiveFiles(emptyPage)),
    searchFiles: async body => {
        dispatch(SSActions.setFilesLoading(true));
        dispatch(await SSActions.searchFiles(body));
        dispatch(setFilename(body.fileName || ""));
    },
    searchApplications: async body => {
        dispatch(SSActions.setApplicationsLoading(true));
        dispatch(await SSActions.searchApplications(body));
        dispatch(setAppQuery(body.query || ""));
    },
    setFilesPage: page => dispatch(SSActions.receiveFiles(page)),
    setApplicationsPage: page => dispatch(SSActions.receiveApplications(page)),
    setSearch: search => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: sT => dispatch(setPrioritizedSearch(sT)),
    toggleAdvancedSearch: () => dispatch(toggleFilesSearchHidden()),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

const mapStateToProps = ({
    simpleSearch,
    detailedFileSearch,
    detailedApplicationSearch
}: ReduxObject): SimpleSearchStateProps & {favFilesCount: number, favAppCount: number} => ({
    ...simpleSearch,
    favFilesCount: simpleSearch.files.items.filter(it => it.favorited).length,
    favAppCount: simpleSearch.applications.items.filter(it => it.favorite).length,
    fileSearch: detailedFileSearch,
    applicationSearch: detailedApplicationSearch
});

export default connect(mapStateToProps, mapDispatchToProps)(Search);

export function fileSearchBody(
    fileSearch: DetailedFileSearchReduxState,
    fileName: string,
    itemsPerPage: number,
    page: number
): AdvancedSearchRequest {
    const fileTypes: [FileType?, FileType?] = [];
    if (fileSearch.allowFiles) fileTypes.push("FILE");
    if (fileSearch.allowFolders) fileTypes.push("DIRECTORY");
    const modifiedAt = {
        after: !!fileSearch.modifiedAfter ? fileSearch.modifiedAfter.valueOf() : undefined,
        before: !!fileSearch.modifiedBefore ? fileSearch.modifiedBefore.valueOf() : undefined,
    };

    return {
        fileName,
        extensions: [...fileSearch.extensions],
        fileTypes,
        modifiedAt: typeof modifiedAt.after === "number" ||
            typeof modifiedAt.before === "number" ? modifiedAt : undefined,
        includeShares: fileSearch.includeShares,
        itemsPerPage,
        page
    };
}

export function applicationSearchBody(
    body: DetailedApplicationSearchReduxState,
    appName: string,
    itemsPerPage: number,
    page: number
): AppSearchRequest {
    const {tags, showAllVersions} = body;
    return {
        query: appName,
        tags: tags.size > 0 ? [...tags] : undefined,
        showAllVersions,
        itemsPerPage,
        page
    };
}

function query(props: SearchProps): string {
    return queryFromProps(props);
}

function queryFromProps(p: SearchProps): string {
    return getQueryParamOrElse(p, "query", "");
}
