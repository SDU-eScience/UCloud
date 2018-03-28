import React from "react";
import { Button, Table, ButtonToolbar } from "react-bootstrap";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { Link } from "react-router-dom";
import { Card } from "../Cards";
import { toLowerCaseAndCapitalize } from "../../UtilityFunctions";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import PromiseKeeper from "../../PromiseKeeper";
import { updatePageTitle } from "../../Actions/Status";
import { fetchPublications, setLoading } from "../../Actions/Zenodo";
import { connect } from "react-redux";


class ZenodoHome extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            sorting: {
                lastSorting: "lastUpdate",
                asc: true,
            }
        };
        const { dispatch } = props;
        dispatch(updatePageTitle("Zenodo Overview"));
        dispatch(setLoading(true));
        dispatch(fetchPublications());
    }

    render() {
        if (!this.props.connected && !this.props.loading) {
            return (<NotConnectedToZenodo />);
        } else {
            return (
                <div className="container">
                    <h4>Upload progress<small className="pull-right">Connected to Zenodo</small></h4>
                    <PublishStatus publications={this.props.publications} loading={this.props.loading} />
                </div>
            );
        }
    }
}

const PublishStatus = (props) => {
    let body = null;
    if (props.loading) {
        return (<BallPulseLoading loading={props.loading} />
        );
    }
    if (!props.publications.length) {
        body = (
            <h3>
                <small className="text-center">No publications found.</small>
            </h3>
        );
    } else {
        body = (
            <div>
                <Table responsive>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Name</th>
                            <th>Status</th>
                            <th />
                            <th>Info</th>
                            <th>Last update</th>
                        </tr>
                    </thead>
                    <PublicationList publications={props.publications} />
                </Table>
            </div>)
    }

    return (
        <div>
            <Card>
                <div className="card-body">
                    {body}
                    <Link to="/zenodo/publish/">
                        <Button>Create new upload</Button>
                    </Link>
                </div>
            </Card>
        </div>);
}

const PublicationList = (props) => {
    if (!props.publications) {
        return null;
    }
    const publicationList = props.publications.map((publication, index) => {
        let actionButton = null;
        if (publication.zenodoAction) {
            actionButton = (
                <a href={publication.zenodoAction} target="_blank">
                    <Button bsStyle="info">Finish publication at
                        Zenodo
                    </Button>
                </a>);
        }
        return (
            <tr key={index}>
                <td>{publication.id}</td>
                <td>{publication.name}</td>
                <td>{toLowerCaseAndCapitalize(publication.status)}</td>
                <td>{actionButton}</td>
                <td>
                    <Link to={`/zenodo/info/${window.encodeURIComponent(publication.id)}`}><Button>Show
                        More</Button></Link>
                </td>
                <td>{new Date(publication.modifiedAt).toLocaleString()}</td>
            </tr>);
    });
    return (
        <tbody>
            {publicationList}
        </tbody>
    );
};

const mapStateToProps = (state) => ({ connected, loading, publications } = state.zenodo);
export default connect(mapStateToProps)(ZenodoHome);
