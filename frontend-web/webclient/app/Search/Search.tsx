import * as React from "react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { ApplicationCard } from "Applications/Applications";
import { ProjectMetadata } from "Metadata/api";
import { SearchItem } from "Metadata/Search";
import { AllFileOperations } from "Utilities/FileUtilities";
import { SearchProps, SimpleSearchOperations, SimpleSearchStateProps } from ".";
import { HeaderSearchType, ReduxObject } from "DefaultObjects";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { Application } from "Applications";
import { Page } from "Types";
import { Dispatch } from "redux";
import { File, SortOrder, SortBy, AdvancedSearchRequest, FileType } from "Files";
import * as SSActions from "./Redux/SearchActions";
import { Error, Hide, Input, OutlineButton, Text, Flex, ToggleBadge } from "ui-components";
import Card, { CardGroup } from "ui-components/Card";
import { MainContainer } from "MainContainer/MainContainer";
import DetailedFileSearch from "Files/DetailedFileSearch";
import { toggleFilesSearchHidden, setFilename } from "Files/Redux/DetailedFileSearchActions";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import { setAppName } from "Applications/Redux/DetailedApplicationSearchActions";
import { FilesTable } from "Files/Files";

class Search extends React.Component<SearchProps> {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        this.props.toggleAdvancedSearch();
        if (!this.props.match.params[0]) { this.props.setError("No search text provided."); return };
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
            fileName: fileSearch.fileName,
            extensions: [...fileSearch.extensions],
            fileTypes,
            createdAt: typeof createdAt.after === "number" || typeof createdAt.before === "number" ? createdAt : undefined,
            modifiedAt: typeof modifiedAt.after === "number" || typeof modifiedAt.before === "number" ? modifiedAt : undefined,
            itemsPerPage: 25,
            page: 0
        }
    }

    componentWillUnmount = () => this.props.toggleAdvancedSearch();

    shouldComponentUpdate(nextProps: SearchProps): boolean {
        if (nextProps.match.params[0] !== this.props.match.params[0]) {
            this.props.setSearch(nextProps.match.params[0]);
            this.fetchAll(nextProps.match.params[0]);
        }
        if (nextProps.match.params.priority !== this.props.match.params.priority) {
            this.props.setPrioritizedSearch(nextProps.match.params.priority as HeaderSearchType);
        }
        return true;
    }

    setPath = (text: string) => {
        this.props.setPrioritizedSearch(text as HeaderSearchType);
        this.props.history.push(`/simplesearch/${text.toLocaleLowerCase()}/${this.props.search}`);
    }

    fetchAll(search: string) {
        const { ...props } = this.props;
        props.setError();
        props.searchFiles(this.fileSearchBody);
        props.searchApplications(search, this.props.applications.pageNumber, this.props.applications.itemsPerPage);
        props.searchProjects(search, this.props.projects.pageNumber, this.props.projects.itemsPerPage);
    }

    search() {
        if (!this.props.search) return;
        this.props.history.push(`/simplesearch/${this.props.match.params.priority}/${this.props.search}`);
    }

    render() {
        const { search, files, projects, applications, filesLoading, applicationsLoading, projectsLoading, errors } = this.props;
        const fileOperations = AllFileOperations(true, false, false, this.props.history);
        const errorMessage = !!errors.length ? (<Error error={errors.join("\n")} clearError={() => this.props.setError(undefined)} />) : null;
        // FIXME Following is format from Semantic we no longer use.
        const panes = [
            {
                menuItem: "Files", render: () => (
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
                        onItemsPerPageChanged={itemsPerPage => this.props.searchFiles({ ...this.fileSearchBody, page: 0, itemsPerPage })}
                        onPageChanged={pageNumber => this.props.searchFiles({ ...this.fileSearchBody, page: pageNumber })}
                    />)
            },
            {
                menuItem: "Projects", render: () => (
                    <Pagination.List
                        loading={projectsLoading}
                        pageRenderer={page => page.items.map((it, i) => (<SearchItem key={i} item={it} />))}
                        page={projects}
                        onItemsPerPageChanged={itemsPerPage => this.props.searchProjects(search, 0, itemsPerPage)}
                        onPageChanged={pageNumber => this.props.searchProjects(search, pageNumber, projects.itemsPerPage)}
                    />
                )
            },
            {
                menuItem: "Applications", render: () => (
                    <Pagination.List
                        loading={applicationsLoading}
                        pageRenderer={({ items }) =>
                            <CardGroup>
                                {items.map(app =>
                                    <ApplicationCard
                                        key={`${app.description.info.name}${app.description.info.version}`}
                                        /* favoriteApp={favoriteApp} */
                                        app={app}
                                        isFavorite={app.favorite}
                                    />)}
                            </CardGroup>
                        }
                        page={applications}
                        onItemsPerPageChanged={(itemsPerPage) => this.props.searchApplications(search, 0, itemsPerPage)}
                        onPageChanged={(pageNumber) => this.props.searchApplications(search, pageNumber, applications.itemsPerPage)}
                    />
                )
            }
        ];
        const activeIndex = SearchPriorityToNumber(this.props.match.params.priority);
        return (
            <MainContainer
                header={
                    <React.Fragment>
                        {errorMessage}
                        <Hide xl md>
                            <form onSubmit={e => { e.preventDefault(); this.search(); }}>
                                <Input onChange={({ target: { value } }) => this.props.setSearch(value)} />
                            </form>
                        </Hide>
                        <Card mt="0.5em" height="3em" width="100%">
                            <Flex>
                                <ToggleBadge bg="lightGray" pb="12px" pt="10px" fontSize={2} onClick={() => this.setPath("files")} color={"black"} selected={activeIndex === 0}>{panes[0].menuItem}</ToggleBadge>
                                <ToggleBadge bg="lightGray" pb="12px" pt="10px" fontSize={2} onClick={() => this.setPath("projects")} color={"black"} selected={activeIndex === 1}>{panes[1].menuItem}</ToggleBadge>
                                <ToggleBadge bg="lightGray" pb="12px" pt="10px" fontSize={2} onClick={() => this.setPath("applications")} color={"black"} selected={activeIndex === 2}>{panes[2].menuItem}</ToggleBadge>
                            </Flex>
                        </Card>
                    </React.Fragment>
                }
                main={panes[activeIndex].render()}
                sidebar={<SearchBar active={panes[activeIndex].menuItem as MenuItemName} />}
            />
        );
    }
};

type MenuItemName = "Files" | "Projects" | "Applications";
type SearchBarProps = { active: MenuItemName }
const SearchBar = (props: SearchBarProps) => {
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
    if (search.toLocaleLowerCase() === "applications") return 2;
    return 0;
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    setFilesLoading: (loading) => dispatch(SSActions.setFilesLoading(loading)),
    setApplicationsLoading: (loading) => dispatch(SSActions.setApplicationsLoading(loading)),
    setProjectsLoading: (loading) => dispatch(SSActions.setProjectsLoading(loading)),
    setError: (error) => dispatch(SSActions.setErrorMessage(error)),
    searchFiles: async (body) => {
        dispatch(SSActions.setFilesLoading(true));
        dispatch(await SSActions.searchFiles(body));
        dispatch(setFilename(body.fileName ? body.fileName : ""));
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
    setFilesPage: (page: Page<File>) => dispatch(SSActions.receiveFiles(page)),
    setApplicationsPage: (page: Page<Application>) => dispatch(SSActions.receiveApplications(page)),
    setProjectsPage: (page: Page<ProjectMetadata>) => dispatch(SSActions.receiveProjects(page)),
    setSearch: (search) => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: (sT: HeaderSearchType) => dispatch(setPrioritizedSearch(sT)),
    toggleAdvancedSearch: () => dispatch(toggleFilesSearchHidden())
});

const mapStateToProps = ({ simpleSearch, detailedFileSearch, detailedApplicationSearch }: ReduxObject): SimpleSearchStateProps => ({
    ...simpleSearch,
    fileSearch: detailedFileSearch,
    applicationSearch: detailedApplicationSearch
});

export default connect(mapStateToProps, mapDispatchToProps)(Search)