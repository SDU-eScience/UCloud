import * as React from "react";
import { Tab, List, Icon, Card } from "semantic-ui-react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { Link } from "react-router-dom";
import { Cloud } from "Authentication/SDUCloudObject";
import * as uf from "UtilityFunctions";
import PromiseKeeper from "PromiseKeeper";
import { SingleApplication } from "Applications/Applications";

class SimpleSearch extends React.Component<any, any> {
    constructor(props) {
        super(props);
        const { match } = props;
        this.state = {
            currentPane: match.params.priority,
            searchString: match.params[0],
            promises: new PromiseKeeper(),
            files: [],
            filesLoading: false,
            applications: [],
            applicationsLoading: false,
            projects: [],
            projectsLoading: false
        };
    }

    componentDidMount() {
        const { promises, searchString } = this.state;
        this.setState(() => ({ filesLoading: true, applicationsLoading: true, projectsLoading: true }));
        promises.makeCancelable(Cloud.get(`/api/file-search?query=${searchString}`)).promise.then(({ response }) => {
            this.setState(() => ({ files: response }))
        }).catch(err => console.log(err)).finally(() => this.setState(() => ({
            filesLoading: false, files: [{
                "fileType": "DIRECTORY",
                "path": "/home/jonas@hinchely.dk/App Data"
            }, {
                "fileType": "DIRECTORY",
                "path": "/home/jonas@hinchely.dk/App Data"
            }, {
                "fileType": "FILE",
                "path": "/home/jonas@hinchely.dk/plantuml.jar"
            }, {
                "fileType": "DIRECTORY",
                "path": "/home/jonas@hinchely.dk/App Data"
            }, {
                "fileType": "FILE",
                "path": "/home/jonas@hinchely.dk/plantuml.jar"
            }]
        })));
        promises.makeCancelable(Cloud.get(`/hpc/apps?page=0&itemsPerPage=100`).then(({ response }) =>
            this.setState(() => ({ applications: response }))).catch(() => null)
            .finally(() => this.setState({ applicationsLoading: false }))
        );

        // fetchProjects
        // fetchApplications
    }

    render() {
        const { files, project, applications, filesLoading, applicationsLoading, projectsLoading } = this.state;
        const panes = [
            {
                menuItem: "Files", render: () => (
                    <Tab.Pane loading={filesLoading}>
                        <Pagination.List
                            pageRenderer={(page) => (<SimpleFileList files={files} />)}
                            page={{ items: files, itemsPerPage: 25, pageNumber: 0, itemsInTotal: 5 }}
                            onItemsPerPageChanged={(itemsPerPage: number) => null}
                            onPageChanged={(pageNumber: number) => null}
                        />
                    </Tab.Pane>)
            },
            {
                menuItem: "Projects", render: () => (
                    <Tab.Pane loading={projectsLoading}>
                        
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
                            onItemsPerPageChanged={(size) => null}
                            onPageChanged={(pageNumber) => null}
                        />
                    </Tab.Pane>
                )
            }
        ];
        return (
            <Tab panes={panes} />
        );
    }
};

function SimpleFileList({ files }) {
    return (
        <List size="large" relaxed>
            {files.map(({ fileType, path }, i) => (
                <List.Item key={i}>
                    <List.Content>
                        <Icon name={fileType === "DIRECTORY" ? "folder" : "file outline"} size={null} color={"blue"} />
                        <Link to={`/files/${fileType === "FILE" ? uf.getParentPath(path) : path}`}>
                            {uf.getFilenameFromPath(path)}
                        </Link>
                    </List.Content>
                </List.Item>
            ))}
        </List>
    );
}

function SimpleProjectList({ projects }) {

}

export default connect()(SimpleSearch)