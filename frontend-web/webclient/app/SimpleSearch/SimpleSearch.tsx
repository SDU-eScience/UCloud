import * as React from "react";
import { Tab, List, Icon, Card, Message } from "semantic-ui-react";
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
import { FileOperations } from "Files/Files";

class SimpleSearch extends React.Component<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            files: emptyPage,
            filesLoading: true,
            applications: emptyPage,
            applicationsLoading: true,
            projects: emptyPage,
            projectsLoading: true,
            error: ""
        };
    }

    componentDidMount() {
        this.searchFiles(0, 25);
        this.searchApplications(0, 25);
        this.searchProjects(0, 25)
    }

    searchFiles = (pageNumber: number, itemsPerPage: number) => {
        const searchString = this.props.match.params[0];
        const { promises } = this.state;
        this.setState(() => ({ filesLoading: true }));
        promises.makeCancelable(Cloud.get(`/file-search?query=${searchString}&page=${pageNumber}&itemsPerPage=${itemsPerPage}`))
            .promise.then(({ response }) => this.setState(() => ({ files: response })))
            .catch(_ => this.setState(() => ({ error: `${this.state.error}An error occurred searching for files. ` })))
            .finally(() => this.setState(() => ({ filesLoading: false })));
    }

    searchApplications = (pageNumber: number, itemsPerPage: number) => {
        const searchString = this.props.match.params[0];
        const { promises } = this.state;
        this.setState(() => ({ applicationsLoading: true }));
        promises.makeCancelable(Cloud.get(`/hpc/apps?page=${pageNumber}&itemsPerPage=${itemsPerPage}`))
            .promise.then(({ response }) => this.setState(() => ({ applications: response })))
            .catch(() => this.setState(() => ({ error: `${this.state.error}An error occurred searching for applications. ` })))
            .finally(() => this.setState(() => ({ applicationsLoading: false })));
    }

    searchProjects = (pageNumber: number, itemsPerPage: number) => {
        const searchString = this.props.match.params[0];
        const { promises } = this.state;
        this.setState(() => ({ projectsLoading: true }));
        promises.makeCancelable(simpleSearch(searchString, pageNumber, itemsPerPage))
            .promise.then(response => this.setState(() => ({ projects: response })))
            .catch(() => this.setState(() => ({ error: `${this.state.error}An error occurred searching for projects. ` })))
            .finally(() => this.setState(() => ({ projectsLoading: false })));
    }

    setPath = (t) => {
        this.props.history.push(`/simplesearch/${t.text.toLocaleLowerCase()}/${this.props.match.params[0]}`);
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
                            pageRenderer={(page) => (<SimpleFileList fileOperations={fileOperations} files={page.items} />)}
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
                <Tab onTabChange={({ target }) => this.setPath(target)} activeIndex={SearchPriorityToNumber(this.props.match.params.priority)} panes={panes} />
            </React.StrictMode>
        );
    }
};

const SimpleFileList = ({ files, fileOperations }) => (
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
                <List.Content/>
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