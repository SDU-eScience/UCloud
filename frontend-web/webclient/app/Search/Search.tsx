import * as React from "react";
import * as Pagination from "Pagination";
import {connect} from "react-redux";
import {ApplicationCard} from "Applications/Card";
import {allFileOperations, favoriteFileFromPage} from "Utilities/FileUtilities";
import {SearchProps, SimpleSearchOperations, SimpleSearchStateProps} from ".";
import {HeaderSearchType, ReduxObject, emptyPage} from "DefaultObjects";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {Dispatch} from "redux";
import {SortOrder, SortBy, AdvancedSearchRequest, FileType} from "Files";
import * as SSActions from "./Redux/SearchActions";
import Error from "ui-components/Error";
import Text from "ui-components/Text";
import Flex from "ui-components/Flex";
import Hide from "ui-components/Hide";
import theme from "ui-components/theme";
import {MainContainer} from "MainContainer/MainContainer";
import {toggleFilesSearchHidden, setFilename} from "Files/Redux/DetailedFileSearchActions";
import {setAppName} from "Applications/Redux/DetailedApplicationSearchActions";
import FilesTable from "Files/FilesTable";
import {searchPage} from "Utilities/SearchUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import styled from "styled-components";
import {GridCardGroup} from "ui-components/Grid";
import {SidebarPages} from "ui-components/Sidebar";
import {setActivePage} from "Navigation/Redux/StatusActions";
import {Spacer} from "ui-components/Spacer";
import {Cloud} from "Authentication/SDUCloudObject";
import {prettierString} from "UtilityFunctions";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import DetailedFileSearch from "Files/DetailedFileSearch";

function Search(props: SearchProps) {
    React.useEffect(() => {
        props.toggleAdvancedSearch();
        props.setActivePage();
        const q = query();
        props.setSearch(q);
        props.setPrioritizedSearch(props.match.params.priority as HeaderSearchType);
        props.setRefresh(() => fetchAll(q));
        return () => {
            props.toggleAdvancedSearch();
            props.clear();
            props.setRefresh();
        }
    }, []);

    const query = (): string => queryFromProps(props);

    const queryFromProps = (props: SearchProps): string => {
        return getQueryParamOrElse(props, "query", "");
    };

    const fileSearchBody = (): AdvancedSearchRequest => {
        // FIXME Duplicate code
        const {...fileSearch} = props.fileSearch;
        let fileTypes: [FileType?, FileType?] = [];
        if (fileSearch.allowFiles) fileTypes.push("FILE");
        if (fileSearch.allowFolders) fileTypes.push("DIRECTORY");
        const createdAt = {
            after: !!fileSearch.createdAfter ? fileSearch.createdAfter.valueOf() : undefined,
            before: !!fileSearch.createdBefore ? fileSearch.createdBefore.valueOf() : undefined,
        };
        const modifiedAt = {
            after: !!fileSearch.modifiedAfter ? fileSearch.modifiedAfter.valueOf() : undefined,
            before: !!fileSearch.modifiedBefore ? fileSearch.modifiedBefore.valueOf() : undefined,
        };

        return {
            fileName: !!fileSearch.fileName ? fileSearch.fileName : query(),
            extensions: [...fileSearch.extensions],
            fileTypes,
            createdAt: typeof createdAt.after === "number" || typeof createdAt.before === "number" ? createdAt : undefined,
            modifiedAt: typeof modifiedAt.after === "number" || typeof modifiedAt.before === "number" ? modifiedAt : undefined,
            itemsPerPage: props.files.itemsPerPage || 25,
            page: 0
        }
    };

    React.useEffect(() => {
        props.setSearch(query());
        props.setPrioritizedSearch(props.match.params.priority as HeaderSearchType);
        fetchAll(query());
    }, [query(), props.match.params.priority]);

    const setPath = (text: string) => {
        props.setPrioritizedSearch(text as HeaderSearchType);
        props.history.push(searchPage(text.toLocaleLowerCase(), props.search));
    };

    function fetchAll(search: string, itemsPerPage?: number) {
        props.setError();
        props.searchFiles({
            ...fileSearchBody(),
            fileName: search,
            itemsPerPage: itemsPerPage || props.files.itemsPerPage
        });
        props.searchApplications(search, 0, itemsPerPage || props.applications.itemsPerPage);
    }

    const refreshFiles = () => props.searchFiles({...fileSearchBody()});
    const {search, files, applications, filesLoading, applicationsLoading, errors} = props;
    const fileOperations = allFileOperations({
        stateless: true,
        history: props.history,
        onDeleted: () => refreshFiles(),
        onExtracted: () => refreshFiles(),
        onSensitivityChange: () => refreshFiles(),
        setLoading: () => props.setFilesLoading(true),
    });

    const Tab = ({searchType}: { searchType: HeaderSearchType }): JSX.Element => (
        <SelectableText
            cursor="pointer"
            fontSize={2}
            onClick={() => setPath(searchType)}
            selected={priority === searchType}
            mr="1em"
        >
            {prettierString(searchType)}
        </SelectableText>
    );

    const allowedSearchTypes: HeaderSearchType[] = ["files", "applications"];

    let main;
    const {priority} = props.match.params;
    if (priority === "files") {
        main = <>
            <Hide xxl xl lg>
                <DetailedFileSearch cantHide/>
            </Hide>
            <Pagination.List
                loading={filesLoading}
                pageRenderer={page => (
                    <FilesTable
                        files={page.items}
                        sortOrder={SortOrder.ASCENDING}
                        sortingColumns={[SortBy.MODIFIED_AT, SortBy.SENSITIVITY_LEVEL]}
                        sortFiles={() => undefined}
                        onCheckFile={() => undefined}
                        refetchFiles={() => props.searchFiles(fileSearchBody())}
                        sortBy={SortBy.PATH}
                        onFavoriteFile={files => props.setFilesPage(favoriteFileFromPage(props.files, files, Cloud))}
                        fileOperations={fileOperations}
                    />
                )}
                page={files}
                onPageChanged={pageNumber => props.searchFiles({...fileSearchBody(), page: pageNumber})}
            />
        </>
    } else if (priority === "applications") {
        main = <>
            <Hide xxl xl lg>
                <DetailedApplicationSearch/>
            </Hide>
            <Pagination.List
                loading={applicationsLoading}
                pageRenderer={({items}) =>
                    <GridCardGroup>
                        {items.map(app =>
                            <ApplicationCard
                                key={`${app.metadata.name}${app.metadata.version}`}
                                app={app}
                                isFavorite={app.favorite}
                            />)}
                    </GridCardGroup>
                }
                page={applications}
                onPageChanged={pageNumber => props.searchApplications(search, pageNumber, applications.itemsPerPage)}
            />
        </>
    }

    return (
        <MainContainer
            header={
                <React.Fragment>
                    <Error error={errors.join("\n")} clearError={() => props.setError(undefined)}/>
                    <SearchOptions>
                        {allowedSearchTypes.map((pane, index) => <Tab searchType={pane} key={index}/>)}
                    </SearchOptions>
                    <Spacer left={null} right={<Pagination.EntriesPerPageSelector
                        onChange={itemsPerPage => fetchAll(props.search, itemsPerPage)}
                        content={`${prettierString(priority)} per page`}
                        entriesPerPage={
                            priority === "files" ? props.files.itemsPerPage : (props.applications.itemsPerPage)
                        }
                    />}/>
                </React.Fragment>
            }
            main={main}
        />
    )
}

// FIXME: Move to own file.
export const SearchOptions = styled(Flex)`
    border-bottom: 1px solid ${({theme}) => theme.colors.lightGray};
    cursor: pointer;
`;

SearchOptions.defaultProps = {
    theme
};

export const SelectableText = styled(Text) <{selected: boolean}>`
    border-bottom: ${props => props.selected ? `2px solid ${theme.colors.blue}` : ""};
`;

SelectableText.defaultProps = {
    theme
};

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    setFilesLoading: loading => dispatch(SSActions.setFilesLoading(loading)),
    setApplicationsLoading: loading => dispatch(SSActions.setApplicationsLoading(loading)),
    setError: error => dispatch(SSActions.setErrorMessage(error)),
    clear: () => {
        dispatch(SSActions.receiveFiles(emptyPage))
        dispatch(SSActions.receiveFiles(emptyPage))
    },
    searchFiles: async body => {
        dispatch(SSActions.setFilesLoading(true));
        dispatch(await SSActions.searchFiles(body));
        dispatch(setFilename(body.fileName || ""));
    },
    searchApplications: async (query, page, itemsPerPage) => {
        dispatch(SSActions.setApplicationsLoading(true));
        dispatch(await SSActions.searchApplications(query, page, itemsPerPage));
        dispatch(setAppName(query));
    },
    setFilesPage: page => dispatch(SSActions.receiveFiles(page)),
    setApplicationsPage: page => dispatch(SSActions.receiveApplications(page)),
    setSearch: search => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: sT => dispatch(setPrioritizedSearch(sT)),
    toggleAdvancedSearch: () => dispatch(toggleFilesSearchHidden()),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

const mapStateToProps = ({simpleSearch, detailedFileSearch, detailedApplicationSearch}: ReduxObject): SimpleSearchStateProps & { favFilesCount: number } => ({
    ...simpleSearch,
    favFilesCount: simpleSearch.files.items.filter(it => it.favorited).length,
    fileSearch: detailedFileSearch,
    applicationSearch: detailedApplicationSearch
});

export default connect(mapStateToProps, mapDispatchToProps)(Search)
