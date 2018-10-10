import * as React from "react";
import { Tab, List, Icon, Card, Message, Responsive, Form } from "semantic-ui-react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { Link } from "react-router-dom";
import { Cloud } from "Authentication/SDUCloudObject";
import * as UF from "UtilityFunctions";
import { SingleApplication } from "Applications/Applications";
import { ProjectMetadata } from "Metadata/api";
import { SearchItem } from "Metadata/Search";
import { AllFileOperations, getParentPath, getFilenameFromPath } from "Utilities/FileUtilities";
import { SimpleSearchProps, SimpleSearchOperations } from ".";
import { HeaderSearchType, ReduxObject } from "DefaultObjects";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { Application } from "Applications";
import { Page } from "Types";
import { Dispatch } from "redux";
import { File } from "Files";
import * as SSActions from "./Redux/SimpleSearchActions";


class SimpleSearch extends React.Component<SimpleSearchProps> {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        if (!this.props.search) return;
        this.fetchAll(this.props.search);
    }

    shouldComponentUpdate(nextProps, _nextState): boolean {
        if (nextProps.match.params[0] !== this.props.match.params[0]) {
            this.props.setSearch(nextProps.match.params[0]);
            this.fetchAll(nextProps.match.params[0]);
        }
        if (nextProps.match.params.priority !== this.props.match.params.priority) {
            this.props.setPrioritizedSearch(nextProps.match.params.priority as HeaderSearchType);
        }
        return true;
    }

    setPath = (t) => {
        const { text } = t;
        this.props.setPrioritizedSearch(text as HeaderSearchType);
        this.props.history.push(`/simplesearch/${text.toLocaleLowerCase()}/${this.props.search}`);
    }

    fetchAll(search: string) {
        this.props.searchFiles(search, this.props.files.pageNumber, this.props.files.itemsPerPage);
        this.props.searchApplications(search, this.props.applications.pageNumber, this.props.applications.itemsPerPage);
        this.props.searchProjects(search, this.props.projects.pageNumber, this.props.projects.itemsPerPage);
    }

    search() {
        if (!this.props.search) return;
        this.props.history.push(`/simplesearch/${this.props.match.params.priority}/${this.props.search}`);
    }

    render() {
        const { search, files, projects, applications, filesLoading, applicationsLoading, projectsLoading, error } = this.props;
        const errorMessage = !!error ? (<Message color="red" content={error} onDismiss={() => this.props.setError(undefined)} />) : null;
        // Currently missing ACLS to allow for fileOperations
        const fileOperations = AllFileOperations(true, false, () => this.props.searchFiles(search, files.pageNumber, files.itemsPerPage), this.props.history);
        const panes = [
            {
                menuItem: "Files", render: () => (
                    <Tab.Pane loading={filesLoading}>
                        <Pagination.List
                            loading={filesLoading}
                            pageRenderer={(page) => (<SimpleFileList files={page.items} />)}
                            page={files}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.props.searchFiles(search, 0, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.props.searchFiles(search, pageNumber, files.itemsPerPage)}
                        />
                    </Tab.Pane>)
            },
            {
                menuItem: "Projects", render: () => (
                    <Tab.Pane loading={projectsLoading}>
                        <Pagination.List
                            loading={projectsLoading}
                            pageRenderer={(page) => page.items.map((it, i) => (<SearchItem key={i} item={it} />))}
                            page={projects}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.props.searchProjects(search, 0, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.props.searchProjects(search, pageNumber, projects.itemsPerPage)}
                        />
                    </Tab.Pane>
                )
            },
            {
                menuItem: "Applications", render: () => (
                    <Tab.Pane loading={applicationsLoading}>
                        <Pagination.List
                            loading={applicationsLoading}
                            pageRenderer={({ items }) =>
                                <Card.Group className="card-margin">
                                    {items.map((app, i) => <SingleApplication key={i} app={app} />)}
                                </Card.Group>
                            }
                            page={applications}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.props.searchApplications(search, 0, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.props.searchApplications(search, pageNumber, applications.itemsPerPage)}
                        />
                    </Tab.Pane>
                )
            }
        ];
        return (
            <React.StrictMode>
                {errorMessage}
                <Responsive maxWidth={999} as={Form} className="form-input-margin" onSubmit={() => this.search()}>
                    <Form.Input style={{ marginBottom: "15px" }} onChange={(_, { value }) => this.props.setSearch(value)} fluid />
                </Responsive>
                <Tab onTabChange={({ target }) => this.setPath(target)} activeIndex={SearchPriorityToNumber(this.props.match.params.priority)} panes={panes} />
            </React.StrictMode>
        );
    }
};

const SimpleFileList = ({ files }) => (
    <List size="large" relaxed>
        {files.map((f, i) => (
            <List.Item key={i}>
                <List.Content>
                    <Icon name={UF.iconFromFilePath(f.path, f.fileType, Cloud.homeFolder)} size={undefined} color={"blue"} />
                    <Link to={`/files/${f.fileType === "FILE" ? getParentPath(f.path) : f.path}`}>
                        {getFilenameFromPath(f.path)}
                    </Link>
                </List.Content>
                {/* <FileOperations fileOperations={fileOperations} files={[f]} /> */}
                <List.Content />
            </List.Item>
        ))}
    </List>
);

const SearchPriorityToNumber = (search: String): number => {
    if (search.toLocaleLowerCase() === "projects") return 1;
    if (search.toLocaleLowerCase() === "applications") return 2;
    return 0;
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    setFilesLoading: (loading: boolean) => dispatch(SSActions.setFilesLoading(loading)),
    setApplicationsLoading: (loading: boolean) => dispatch(SSActions.setApplicationsLoading(loading)),
    setProjectsLoading: (loading: boolean) => dispatch(SSActions.setProjectsLoading(loading)),
    setError: (error?: string) => dispatch(SSActions.setErrorMessage(error)),
    searchFiles: async (query: string, page: number, itemsPerPage: number) => dispatch(await SSActions.searchFiles(query, page, itemsPerPage)),
    searchApplications: async (query: string, page: number, itemsPerPage: number) => dispatch(await SSActions.searchApplications(query, page, itemsPerPage)),
    searchProjects: async (query: string, page: number, itemsPerPage: number) => dispatch(await SSActions.searchProjects(query, page, itemsPerPage)),
    setFilesPage: (page: Page<File>) => dispatch(SSActions.receiveFiles(page)),
    setApplicationsPage: (page: Page<Application>) => dispatch(SSActions.receiveApplications(page)),
    setProjectsPage: (page: Page<ProjectMetadata>) => dispatch(SSActions.receiveProjects(page)),
    setSearch: (search: string) => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: (sT: HeaderSearchType) => dispatch(setPrioritizedSearch(sT))
});

const mapStateToProps = ({ simpleSearch }: ReduxObject) => simpleSearch;

export default connect(mapStateToProps, mapDispatchToProps)(SimpleSearch)