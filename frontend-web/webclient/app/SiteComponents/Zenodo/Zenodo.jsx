import React from "react";
import { Button, Table, Container } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { Link } from "react-router-dom";
import { toLowerCaseAndCapitalize } from "../../UtilityFunctions";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import { updatePageTitle } from "../../Actions/Status";
import { fetchPublications, setZenodoLoading } from "../../Actions/Zenodo";
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
        dispatch(setZenodoLoading(true));
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

const PublishStatusBody = ({ publications }) =>
    !publications.length ?
        <h3>
            <small className="text-center">No publications found.</small>
        </h3> :
        <Container>
            <Table>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell>ID</Table.HeaderCell>
                        <Table.HeaderCell>Name</Table.HeaderCell>
                        <Table.HeaderCell>Status</Table.HeaderCell>
                        <Table.HeaderCell />
                        <Table.HeaderCell>Info</Table.HeaderCell>
                        <Table.HeaderCell>Last update</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <PublicationList publications={publications} />
            </Table>
        </Container>


const PublishStatus = (props) => {
    if (props.loading) {
        return (<BallPulseLoading loading={props.loading} />);
    }

    return (
        <div>
            <PublishStatusBody publications={props.publications} />
            <Link to="/zenodo/publish/">
                <Button>Create new upload</Button>
            </Link>
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
            <Table.Row key={index}>
                <Table.Cell>{publication.id}</Table.Cell>
                <Table.Cell>{publication.name}</Table.Cell>
                <Table.Cell>{toLowerCaseAndCapitalize(publication.status)}</Table.Cell>
                <Table.Cell>{actionButton}</Table.Cell>
                <Table.Cell>
                    <Link to={`/zenodo/info/${window.encodeURIComponent(publication.id)}`}>
                        <Button>Show More</Button>
                    </Link>
                </Table.Cell>
                <Table.Cell>{new Date(publication.modifiedAt).toLocaleString()}</Table.Cell>
            </Table.Row>);
    });
    return (
        <Table.Body>
            {publicationList}
        </Table.Body>
    );
};

const mapStateToProps = (state) => ({ connected, loading, publications } = state.zenodo);
export default connect(mapStateToProps)(ZenodoHome);
