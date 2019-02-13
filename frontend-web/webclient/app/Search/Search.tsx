import * as React from "react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { NewApplicationCard } from "Applications/Card";
import { SearchItem } from "Project/Search";
import { AllFileOperations } from "Utilities/FileUtilities";
import { SearchProps, SimpleSearchOperations, SimpleSearchStateProps } from ".";
import { HeaderSearchType, ReduxObject, emptyPage } from "DefaultObjects";
import { setPrioritizedSearch, setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { Dispatch } from "redux";
import { SortOrder, SortBy, AdvancedSearchRequest, FileType } from "Files";
import * as SSActions from "./Redux/SearchActions";
import { Error, Hide, Input, Text, Flex, theme } from "ui-components";
import { MainContainer } from "MainContainer/MainContainer";
import DetailedFileSearch from "Files/DetailedFileSearch";
import { toggleFilesSearchHidden, setFilename } from "Files/Redux/DetailedFileSearchActions";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import { setAppName } from "Applications/Redux/DetailedApplicationSearchActions";
import { FilesTable } from "Files/FilesTable";
import { searchPage } from "Utilities/SearchUtilities";
import { getQueryParamOrElse } from "Utilities/URIUtilities";
import styled from "styled-components";
import { GridCardGroup } from "ui-components/Grid";
import { SidebarPages } from "ui-components/Sidebar";
import { setActivePage } from "Navigation/Redux/StatusActions";

interface SearchPane {
    headerType: HeaderSearchType
    menuItem: string
    render: () => JSX.Element
}

class Search extends React.Component<SearchProps> {

    componentDidMount() {
        this.props.toggleAdvancedSearch();
        this.props.setActivePage();
        const query = this.query;
        this.props.setSearch(query);
        this.props.setPrioritizedSearch(this.props.match.params.priority as HeaderSearchType);
        this.fetchAll(query);
        this.props.setRefresh(() => this.fetchAll(query));
    }

    componentWillReceiveProps(_nextProps) {
        this.props.setRefresh(() => this.fetchAll(this.query));
    }

    queryFromProps = (props: SearchProps): string => {
        return getQueryParamOrElse(props, "query", "");
    }

    get query(): string {
        return this.queryFromProps(this.props);
    }

    get fileSearchBody(): AdvancedSearchRequest {
        // FIXME Duplicate code
        const { ...fileSearch } = this.props.fileSearch;
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
            fileName: !!fileSearch.fileName ? fileSearch.fileName : this.query,
            extensions: [...fileSearch.extensions],
            fileTypes,
            createdAt: typeof createdAt.after === "number" || typeof createdAt.before === "number" ? createdAt : undefined,
            modifiedAt: typeof modifiedAt.after === "number" || typeof modifiedAt.before === "number" ? modifiedAt : undefined,
            itemsPerPage: 25,
            page: 0
        }
    }

    componentWillUnmount = () => {
        this.props.toggleAdvancedSearch();
        this.props.clear();
        this.props.setRefresh();
    }

    shouldComponentUpdate(nextProps: SearchProps): boolean {
        // TODO It seems like a bad idea to perform side-effects in this method!

        const currentQuery = this.query;
        const nextQuery = this.queryFromProps(nextProps);
        if (nextQuery !== currentQuery) {
            this.props.setSearch(nextQuery);
            this.fetchAll(nextQuery);
            return false;
        }
        if (nextProps.match.params.priority !== this.props.match.params.priority) {
            this.props.setPrioritizedSearch(nextProps.match.params.priority as HeaderSearchType);
            this.fetchAll(nextQuery);
            return false;
        }
        return true;
    }

    setPath = (text: string) => {
        this.props.setPrioritizedSearch(text as HeaderSearchType);
        this.props.history.push(searchPage(text.toLocaleLowerCase(), this.props.search));
    }

    fetchAll(search: string) {
        const { ...props } = this.props;
        props.setError();
        props.searchFiles({ ...this.fileSearchBody, fileName: search });
        props.searchApplications(search, this.props.applications.pageNumber, this.props.applications.itemsPerPage);
        props.searchProjects(search, this.props.projects.pageNumber, this.props.projects.itemsPerPage);
    }

    search() {
        if (!this.props.search) return;
        this.props.history.push(searchPage(this.props.match.params.priority, this.props.search));
    }

    render() {
        const { search, files, projects, applications, filesLoading, applicationsLoading, projectsLoading, errors } = this.props;
        const fileOperations = AllFileOperations({
            stateless: true,
            history: this.props.history,
            setLoading: () => this.props.setFilesLoading(true)
        });
        // FIXME: Search Pane approach is obsolete
        const panes: SearchPane[] = [
            {
                headerType: "files",
                menuItem: "Files",
                render: () => (
                    <Pagination.List
                        loading={filesLoading}
                        pageRenderer={page => (
                            <FilesTable
                                files={page.items}
                                sortOrder={SortOrder.ASCENDING}
                                sortingColumns={[SortBy.MODIFIED_AT, SortBy.SENSITIVITY]}
                                sortFiles={() => undefined}
                                onCheckFile={() => undefined}
                                refetchFiles={() => this.props.searchFiles(this.fileSearchBody)}
                                sortBy={SortBy.PATH}
                                fileOperations={fileOperations}
                            />
                        )}
                        page={files}
                        onPageChanged={pageNumber => this.props.searchFiles({ ...this.fileSearchBody, page: pageNumber })}
                    />
                )
            },
            {
                headerType: "projects",
                menuItem: "Projects",
                render: () => (
                    <Pagination.List
                        loading={projectsLoading}
                        pageRenderer={page => page.items.map((it, i) => (<SearchItem key={i} item={it} />))}
                        page={projects}
                        onPageChanged={pageNumber => this.props.searchProjects(search, pageNumber, projects.itemsPerPage)}
                    />
                )
            },
            {
                headerType: "applications",
                menuItem: "Applications",
                render: () => (
                    <Pagination.List
                        loading={applicationsLoading}
                        pageRenderer={({ items }) =>
                            <GridCardGroup>
                                {items.map(app =>
                                    <NewApplicationCard
                                        key={`${app.metadata.name}${app.metadata.version}`}
                                        app={app}
                                        isFavorite={app.favorite}
                                    />)}
                            </GridCardGroup>
                        }
                        page={applications}
                        onPageChanged={(pageNumber) => this.props.searchApplications(search, pageNumber, applications.itemsPerPage)}
                    />
                )
            }
        ];
        const activeIndex = SearchPriorityToNumber(this.props.match.params.priority);

        const Tab = ({ pane, index }: { pane: SearchPane, index: number }): JSX.Element => (
            <SelectableText
                key={index}
                cursor="pointer"
                fontSize={2}
                onClick={() => this.setPath(pane.headerType)}
                selected={activeIndex === index}
                mr="1em"
            >
                {pane.menuItem}
            </SelectableText>
        );
        return (
            <MainContainer
                header={
                    < React.Fragment >
                        <Error error={errors.join("\n")} clearError={() => this.props.setError(undefined)} />
                        <Hide xxl xl md>
                            <form onSubmit={e => (e.preventDefault(), this.search())}>
                                <Input onChange={({ target: { value } }) => this.props.setSearch(value)} />
                            </form>
                        </Hide>
                        <SearchOptions>
                            {panes.map((pane, index) => <Tab pane={pane} index={index} key={index} />)}
                        </SearchOptions>
                    </React.Fragment>
                }
                main={panes[activeIndex].render()}
                sidebar={< SearchBar active={panes[activeIndex].menuItem as MenuItemName} />}
            />
        );
    }
};

export const SearchOptions = styled(Flex)`
    border-bottom: 1px solid ${theme.colors.lightGray};
`;

export const SelectableText = styled(Text) <{ selected: boolean }>`
    border-bottom: ${props => props.selected ? `2px solid ${theme.colors.blue}` : undefined};
`;

type MenuItemName = "Files" | "Projects" | "Applications";
interface SearchBarProps { active: MenuItemName }
const SearchBar = (props: SearchBarProps) => {
    return null;
    // @ts-ignore
    switch (props.active) {
        case "Files":
            return <DetailedFileSearch />
        case "Projects":
            return null;
        case "Applications":
            return <DetailedApplicationSearch />;
    }
}

const SearchPriorityToNumber = (search: string): number => {
    if (search.toLocaleLowerCase() === "projects") return 1;
    else if (search.toLocaleLowerCase() === "applications") return 2;
    return 0;
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    setFilesLoading: loading => dispatch(SSActions.setFilesLoading(loading)),
    setApplicationsLoading: loading => dispatch(SSActions.setApplicationsLoading(loading)),
    setProjectsLoading: loading => dispatch(SSActions.setProjectsLoading(loading)),
    setError: error => dispatch(SSActions.setErrorMessage(error)),
    clear: () => {
        dispatch(SSActions.receiveFiles(emptyPage))
        dispatch(SSActions.receiveFiles(emptyPage))
        dispatch(SSActions.receiveProjects(emptyPage))
    },
    searchFiles: async (body) => {
        dispatch(SSActions.setFilesLoading(true));
        dispatch(await SSActions.searchFiles(body));
        dispatch(setFilename(body.fileName || ""));
    },
    searchApplications: async (query, page, itemsPerPage) => {
        dispatch(SSActions.setApplicationsLoading(true));
        dispatch(await SSActions.searchApplications(query, page, itemsPerPage));
        dispatch(setAppName(query));
    },
    searchProjects: async (query, page, itemsPerPage) => {
        dispatch(SSActions.setProjectsLoading(true));
        dispatch(await SSActions.searchProjects(query, page, itemsPerPage));
    },
    setFilesPage: page => dispatch(SSActions.receiveFiles(page)),
    setApplicationsPage: page => dispatch(SSActions.receiveApplications(page)),
    setProjectsPage: page => dispatch(SSActions.receiveProjects(page)),
    setSearch: search => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: sT => dispatch(setPrioritizedSearch(sT)),
    toggleAdvancedSearch: () => dispatch(toggleFilesSearchHidden()),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({ simpleSearch, detailedFileSearch, detailedApplicationSearch }: ReduxObject): SimpleSearchStateProps => ({
    ...simpleSearch,
    fileSearch: detailedFileSearch,
    applicationSearch: detailedApplicationSearch
});

export default connect(mapStateToProps, mapDispatchToProps)(Search)