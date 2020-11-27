import {emptyPage, HeaderSearchType} from "DefaultObjects";
import {AdvancedSearchRequest, DetailedFileSearchReduxState, FileType} from "Files";
import DetailedFileSearch from "Files/DetailedFileSearch";
import {EmbeddedFileTable} from "Files/FileTable";
import {setFilename, toggleFilesSearchHidden} from "Files/Redux/DetailedFileSearchActions";
import {MainContainer} from "MainContainer/MainContainer";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, useTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory, useLocation, useRouteMatch} from "react-router";
import {Dispatch} from "redux";
import {Box, SelectableText, SelectableTextWrapper} from "ui-components";
import Hide from "ui-components/Hide";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {searchPage} from "Utilities/SearchUtilities";
import {getQueryParamOrElse, RouterLocationProps} from "Utilities/URIUtilities";
import {prettierString} from "UtilityFunctions";
import {SearchProps, SimpleSearchOperations, SimpleSearchStateProps} from ".";
import * as SSActions from "./Redux/SearchActions";

function Search(props: SearchProps): JSX.Element {

    const match = useRouteMatch<{priority: string}>();
    const history = useHistory();
    const location = useLocation();


    React.useEffect(() => {
        props.toggleAdvancedSearch();
        const q = query({location, history});
        props.setSearch(q);
        props.setPrioritizedSearch(match.params.priority as HeaderSearchType);
        props.setRefresh(fetchAll);
        return () => {
            props.toggleAdvancedSearch();
            props.clear();
            props.setRefresh();
        };
    }, []);


    React.useEffect(() => {
        props.setPrioritizedSearch(match.params.priority as HeaderSearchType);
    }, [match.params.priority]);

    useTitle("Search");

    const setPath = (text: string): void => history.push(searchPage(text.toLocaleLowerCase(), props.search));

    function fetchAll(itemsPerPage?: number): void {
        props.searchFiles(fileSearchBody(
            props.fileSearch,
            props.search,
            itemsPerPage || props.files.itemsPerPage,
            props.files.pageNumber
        ));
        history.push(searchPage(match.params.priority, props.search));
    }

    const refreshFiles = (): void => props.searchFiles(fileSearchBody(
        props.fileSearch,
        props.search,
        props.files.itemsPerPage,
        props.files.pageNumber
    ));

    const {files} = props;

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
    const {priority} = match.params;
    const entriesPerPage = (
        <Box my="8px">
            <Spacer
                left={null}
                right={(
                    <Pagination.EntriesPerPageSelector
                        onChange={itemsPerPage => fetchAll(itemsPerPage)}
                        content={`${prettierString(priority)} per page`}
                        entriesPerPage={
                            props.files.itemsPerPage
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
                    disableNavigationButtons
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
        main = null /*(
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
                                    onFavorite={async () => props.setApplicationsPage(
                                        await favoriteApplicationFromPage({
                                            name: app.metadata.name,
                                            version: app.metadata.version,
                                            page: props.applications,
                                            client: Client
                                        })
                                    )}
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
        */
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
    clear: () => dispatch(SSActions.receiveFiles(emptyPage)),
    searchFiles: async body => {
        dispatch(SSActions.setFilesLoading(true));
        dispatch(await SSActions.searchFiles(body));
        dispatch(setFilename(body.fileName || ""));
    },
    setFilesPage: page => dispatch(SSActions.receiveFiles(page)),
    setSearch: search => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: sT => dispatch(setPrioritizedSearch(sT)),
    toggleAdvancedSearch: () => dispatch(toggleFilesSearchHidden()),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

const mapStateToProps = ({
    simpleSearch,
    detailedFileSearch,
}: ReduxObject): SimpleSearchStateProps => ({
    ...simpleSearch,
    fileSearch: detailedFileSearch,
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
        after: fileSearch.modifiedAfter?.valueOf(),
        before: fileSearch.modifiedBefore?.valueOf(),
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

/*
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
 */

function query(props: RouterLocationProps): string {
    return queryFromProps(props);
}

function queryFromProps(p: RouterLocationProps): string {
    return getQueryParamOrElse(p, "query", "");
}
