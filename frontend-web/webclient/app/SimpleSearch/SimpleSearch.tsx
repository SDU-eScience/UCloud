import * as React from "react";
import { Tab, List, Icon, Card, Message, Responsive, Grid, Form } from "semantic-ui-react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { Link } from "react-router-dom";
import { Cloud } from "Authentication/SDUCloudObject";
import * as uf from "UtilityFunctions";
import PromiseKeeper from "PromiseKeeper";
import { SingleApplication } from "Applications/Applications";
import { simpleSearch } from "Metadata/api";
import { SearchItem } from "Metadata/Search";
import { emptyPage } from "DefaultObjects";
import { AllFileOperations } from "Utilities/FileUtilities";
import { SimpleSearchProps, SimpleSearchState } from ".";
import { HeaderSearchType } from "DefaultObjects";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";

class SimpleSearch extends React.Component<SimpleSearchProps, SimpleSearchState> {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            files: emptyPage,
            filesLoading: false,
            applications: emptyPage,
            applicationsLoading: false,
            projects: emptyPage,
            projectsLoading: false,
            error: "",
            search: this.props.match.params[0]
        };
    }

    componentDidMount() {
        if (this.state.search)
            this.fetchAll();
    }

    searchFiles = (pageNumber: number, itemsPerPage: number) => {
        const { promises, search } = this.state;
        this.setState(() => ({ filesLoading: true }));
        promises.makeCancelable(Cloud.get(`/file-search?query=${search}&page=${pageNumber}&itemsPerPage=${itemsPerPage}`))
            .promise.then(({ response }) => this.setState(() => ({ files: response })))
            .catch(_ => this.setState(() => ({ error: `${this.state.error}An error occurred searching for files. ` })))
            .finally(() => this.setState(() => ({ filesLoading: false })));
    }

    searchApplications = (pageNumber: number, itemsPerPage: number) => {
        const { promises, search } = this.state;
        this.setState(() => ({ applicationsLoading: true }));
        promises.makeCancelable(Cloud.get(`/hpc/apps?page=${pageNumber}&itemsPerPage=${itemsPerPage}`))
            .promise.then(({ response }) => this.setState(() => ({ applications: response })))
            .catch(() => this.setState(() => ({ error: `${this.state.error}An error occurred searching for applications. ` })))
            .finally(() => this.setState(() => ({ applicationsLoading: false })));
    }

    searchProjects = (pageNumber: number, itemsPerPage: number) => {
        const { promises, search } = this.state;
        this.setState(() => ({ projectsLoading: true }));
        promises.makeCancelable(simpleSearch(search, pageNumber, itemsPerPage))
            .promise.then(response => this.setState(() => ({ projects: response })))
            .catch(() => this.setState(() => ({ error: `${this.state.error}An error occurred searching for projects. ` })))
            .finally(() => this.setState(() => ({ projectsLoading: false })));
    }

    setPath = (t) => {
        const { text } = t;
        this.props.dispatch(setPrioritizedSearch(text as HeaderSearchType));
        this.props.history.push(`/simplesearch/${text.toLocaleLowerCase()}/${this.state.search}`);
    }

    fetchAll() {
        this.searchFiles(this.state.files.pageNumber, this.state.files.itemsPerPage);
        this.searchApplications(this.state.applications.pageNumber, this.state.applications.itemsPerPage);
        this.searchProjects(this.state.projects.pageNumber, this.state.projects.itemsPerPage);
    }

    search() {
        if (this.state.search) {
            this.props.history.push(`/simplesearch/${this.props.match.params.priority}/${this.state.search}`);
            this.fetchAll();
        }
    }

    render() {
        const { files, projects, applications, filesLoading, applicationsLoading, projectsLoading, error } = this.state;
        const errorMessage = !!error ? (<Message color="red" content={error} onDismiss={() => this.setState(() => ({ error: "" }))} />) : null;
        const fileOperations = AllFileOperations(true, null, () => this.searchFiles(files.pageNumber, files.itemsPerPage), this.props.history);
        const panes = [
            {
                menuItem: "Files", render: () => (
                    <Tab.Pane loading={filesLoading}>
                        <Pagination.List
                            pageRenderer={(page) => (<SimpleFileList files={page.items} />)}
                            page={files}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.searchFiles(files.pageNumber, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.searchFiles(pageNumber, files.itemsPerPage)}
                        />
                    </Tab.Pane>)
            },
            {
                menuItem: "Projects", render: () => (
                    <Tab.Pane loading={projectsLoading}>
                        <Pagination.List
                            loading={projectsLoading}
                            pageRenderer={(page) => page.items.map((it, i) => (
                                <SearchItem key={i} item={it} />))}
                            page={projects}
                            onItemsPerPageChanged={(itemsPerPage: number) => this.searchProjects(files.pageNumber, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.searchProjects(pageNumber, files.itemsPerPage)}
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
                            onItemsPerPageChanged={(itemsPerPage: number) => this.searchApplications(files.pageNumber, itemsPerPage)}
                            onPageChanged={(pageNumber: number) => this.searchApplications(pageNumber, files.itemsPerPage)}
                        />
                    </Tab.Pane>
                )
            }
        ];
        return (
            <React.StrictMode>
                {errorMessage}
                <Responsive maxWidth={999} as={Form} className="form-input-margin" onSubmit={() => this.search()}>
                    <Form.Input style={{ marginBottom: "15px" }} onChange={(_, { value }) => this.setState(() => ({ search: value }))} fluid />
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
                    <Icon name={uf.iconFromFilePath(f.path, f.fileType, Cloud.homeFolder)} size={null} color={"blue"} />
                    <Link to={`/files/${f.fileType === "FILE" ? uf.getParentPath(f.path) : f.path}`}>
                        {uf.getFilenameFromPath(f.path)}
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

export default connect()(SimpleSearch)