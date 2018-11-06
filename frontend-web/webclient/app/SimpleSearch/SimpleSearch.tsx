import * as React from "react";
import { List as SList, Icon as SIcon, Card as SCard, Message as SMessage, Responsive as SResponsive, Form as SForm, Menu as SMenu, Segment as SSegment } from "semantic-ui-react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { Link } from "react-router-dom";
import { Cloud } from "Authentication/SDUCloudObject";
import * as UF from "UtilityFunctions";
import { ApplicationCard } from "Applications/Applications";
import { ProjectMetadata } from "Metadata/api";
import { SearchItem } from "Metadata/Search";
import { AllFileOperations, getParentPath, replaceHomeFolder } from "Utilities/FileUtilities";
import { SimpleSearchProps, SimpleSearchOperations } from ".";
import { HeaderSearchType, ReduxObject } from "DefaultObjects";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { Application } from "Applications";
import { Page } from "Types";
import { Dispatch } from "redux";
import { File } from "Files";
import * as SSActions from "./Redux/SimpleSearchActions";
import { Error } from "ui-components";
import { CardGroup } from "ui-components/Card";


class SimpleSearch extends React.Component<SimpleSearchProps> {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        if (!this.props.match.params[0]) { this.props.setError("No search text provided."); return };
        this.fetchAll(this.props.match.params[0]);
    }

    shouldComponentUpdate(nextProps: SimpleSearchProps, _nextState): boolean {
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
        props.searchFiles(search, this.props.files.pageNumber, this.props.files.itemsPerPage);
        props.searchApplications(search, this.props.applications.pageNumber, this.props.applications.itemsPerPage);
        props.searchProjects(search, this.props.projects.pageNumber, this.props.projects.itemsPerPage);
    }

    search() {
        if (!this.props.search) return;
        this.props.history.push(`/simplesearch/${this.props.match.params.priority}/${this.props.search}`);
    }

    render() {
        const { search, files, projects, applications, filesLoading, applicationsLoading, projectsLoading, errors } = this.props;
        const errorMessage = !!errors.length ? (<Error error={errors.join("\n")} clearError={() => this.props.setError(undefined)} />) : null;
        const panes = [
            {
                menuItem: "Files", render: () => (
                    <SSegment basic loading={filesLoading}>
                        <Pagination.List
                            loading={filesLoading}
                            pageRenderer={page => (<SimpleFileList files={page.items} />)}
                            page={files}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.props.searchFiles(search, 0, itemsPerPage)}
                            onPageChanged={pageNumber => this.props.searchFiles(search, pageNumber, files.itemsPerPage)}
                        />
                    </SSegment>)
            },
            {
                menuItem: "Projects", render: () => (
                    <SSegment basic loading={projectsLoading}>
                        <Pagination.List
                            loading={projectsLoading}
                            pageRenderer={(page) => page.items.map((it, i) => (<SearchItem key={i} item={it} />))}
                            page={projects}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.props.searchProjects(search, 0, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.props.searchProjects(search, pageNumber, projects.itemsPerPage)}
                        />
                    </SSegment>
                )
            },
            {
                menuItem: "Applications", render: () => (
                    <SSegment basic loading={applicationsLoading}>
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
                    </SSegment>
                )
            }
        ];
        const activeIndex = SearchPriorityToNumber(this.props.match.params.priority);
        return (
            <React.StrictMode>
                {errorMessage}
                <SResponsive maxWidth={999} as={SForm} className="form-input-margin" onSubmit={() => this.search()}>
                    <SForm.Input style={{ marginBottom: "15px" }} onChange={(_, { value }) => this.props.setSearch(value)} fluid />
                </SResponsive>

                <SMenu pointing>
                    <SMenu.Item name={panes[0].menuItem} active={0 === activeIndex} onClick={() => this.setPath("files")} />
                    <SMenu.Item name={panes[1].menuItem} active={1 === activeIndex} onClick={() => this.setPath("projects")} />
                    <SMenu.Item name={panes[2].menuItem} active={2 === activeIndex} onClick={() => this.setPath("applications")} />
                </SMenu>

                {panes[activeIndex].render()}
            </React.StrictMode>
        );
    }
};

export const SimpleFileList = ({ files }) => (
    <SList size="large" relaxed>
        {files.map((f, i) => (
            <SList.Item key={i}>
                <SList.Content>
                    <SIcon name={UF.iconFromFilePath(f.path, f.fileType, Cloud.homeFolder)} size={undefined} color={"blue"} />
                    <Link to={`/files/${f.fileType === "FILE" ? getParentPath(f.path) : f.path}`}>
                        {replaceHomeFolder(f.path, Cloud.homeFolder)}
                    </Link>
                </SList.Content>
                {/* <FileOperations fileOperations={fileOperations} files={[f]} /> */}
                <SList.Content />
            </SList.Item>
        ))}
    </SList>
);

const SearchPriorityToNumber = (search: string): number => {
    if (search.toLocaleLowerCase() === "projects") return 1;
    if (search.toLocaleLowerCase() === "applications") return 2;
    return 0;
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    setFilesLoading: (loading: boolean) => dispatch(SSActions.setFilesLoading(loading)),
    setApplicationsLoading: (loading: boolean) => dispatch(SSActions.setApplicationsLoading(loading)),
    setProjectsLoading: (loading: boolean) => dispatch(SSActions.setProjectsLoading(loading)),
    setError: (error?: string) => dispatch(SSActions.setErrorMessage(error)),
    searchFiles: async (query: string, page: number, itemsPerPage: number) => {
        dispatch(SSActions.setFilesLoading(true));
        dispatch(await SSActions.searchFiles(query, page, itemsPerPage));
    },
    searchApplications: async (query: string, page: number, itemsPerPage: number) => {
        dispatch(SSActions.setApplicationsLoading(true));
        dispatch(await SSActions.searchApplications(query, page, itemsPerPage));
    },
    searchProjects: async (query: string, page: number, itemsPerPage: number) => {
        dispatch(SSActions.setProjectsLoading(true));
        dispatch(await SSActions.searchProjects(query, page, itemsPerPage))
    },
    setFilesPage: (page: Page<File>) => dispatch(SSActions.receiveFiles(page)),
    setApplicationsPage: (page: Page<Application>) => dispatch(SSActions.receiveApplications(page)),
    setProjectsPage: (page: Page<ProjectMetadata>) => dispatch(SSActions.receiveProjects(page)),
    setSearch: (search: string) => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: (sT: HeaderSearchType) => dispatch(setPrioritizedSearch(sT))
});

const mapStateToProps = ({ simpleSearch }: ReduxObject) => simpleSearch;

export default connect(mapStateToProps, mapDispatchToProps)(SimpleSearch)