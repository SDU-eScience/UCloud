import * as React from "react";
import { Button, Table, Header, Responsive, Dropdown, Grid, Divider } from "semantic-ui-react";
import { Link } from "react-router-dom";
import { toLowerCaseAndCapitalize } from "../../UtilityFunctions";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import { updatePageTitle } from "../Navigation/Redux/StatusActions";
import { fetchPublications, setZenodoLoading } from "./Redux/ZenodoActions";
import { connect } from "react-redux";
import { dateToString } from "../../Utilities/DateUtilities";
import { List } from "../Pagination/List";
import { ZenodoHomeProps, ZenodoHomeState } from ".";

class ZenodoHome extends React.Component<ZenodoHomeProps, ZenodoHomeState> {
    constructor(props) {
        super(props);
        this.state = {
            sorting: {
                lastSorting: "lastUpdate",
                asc: true,
            }
        };
        const { updatePageTitle, fetchPublications } = props;
        updatePageTitle("Zenodo Publications");
        fetchPublications(0, 25);
    }

    render() {
        const { connected, loading, fetchPublications, page } = this.props;
        if (!connected && !loading) {
            return (<NotConnectedToZenodo />);
        } else {
            return (
                <React.StrictMode>
                    <Grid>
                        <Grid.Column computer={13} tablet={16}>
                            <Grid.Row>
                                <Responsive maxWidth={991}>
                                    <Button
                                        as={Link}
                                        color="blue"
                                        to="/zenodo/publish/"
                                        basic
                                        fluid
                                        content="Create new upload"
                                        maxWidth={991}
                                    />
                                </Responsive>
                            </Grid.Row>
                            <Header as="h2">
                                <Header.Content className="mobile-padding">
                                    Upload progress
                                </Header.Content>
                                <Responsive as={Header.Subheader} minWidth={768}>
                                    Connected to Zenodo
                                </Responsive>
                            </Header>
                            <List
                                onRefreshClick={() => fetchPublications(page.pageNumber, page.itemsPerPage)}
                                loading={loading}
                                customEmptyPage={<Header.Subheader content="No Zenodo publications found." />}
                                pageRenderer={(page) => (
                                    <Table basic="very">
                                        <TableHeader />
                                        <Table.Body>
                                            {page.items.map((it, i) => (<PublicationRow publication={it} key={i} />))}
                                        </Table.Body>
                                    </Table>
                                )}
                                page={page}
                                onItemsPerPageChanged={(size) => fetchPublications(0, size)}
                                onPageChanged={(pageNumber) => fetchPublications(pageNumber, page.itemsPerPage)}
                            />
                        </Grid.Column>
                        <Responsive as={Grid.Column} computer={3} minWidth={992}>
                            <Button as={Link} color="blue" to="/zenodo/publish/" basic fluid content="Create new upload" />
                            <Divider />
                        </Responsive>
                    </Grid>
                </React.StrictMode >
            );
        }
    }
}

const TableHeader = () => (
    <Table.Header>
        <Table.Row>
            <Table.HeaderCell>ID</Table.HeaderCell>
            <Table.HeaderCell>Name</Table.HeaderCell>
            <Table.HeaderCell>Status</Table.HeaderCell>
            <Table.HeaderCell>Last update</Table.HeaderCell>
            <Table.HeaderCell />
        </Table.Row>
    </Table.Header>
);

const PublicationRow = ({ publication }) => {
    let actionButton = null;
    if (publication.zenodoAction) {
        actionButton = (
            <Dropdown.Item content="Finish publication at Zenodo" as="a" href={publication.zenodoAction} target="_blank" />
        )
    }
    return (
        <Table.Row>
            <Table.Cell>{publication.id}</Table.Cell>
            <Table.Cell>{publication.name}</Table.Cell>
            <Table.Cell>{toLowerCaseAndCapitalize(publication.status)}</Table.Cell>
            <Table.Cell>{dateToString(publication.modifiedAt)}</Table.Cell>
            <Table.Cell>
                <Dropdown direction="left" icon="ellipsis horizontal">
                    <Dropdown.Menu>
                        <Dropdown.Item as={Link} content="Show More" to={`/zenodo/info/${encodeURIComponent(publication.id)}`} />
                        {actionButton}
                    </Dropdown.Menu>
                </Dropdown>
            </Table.Cell>
        </Table.Row>);
}

const mapDispatchToProps = (dispatch) => ({
    fetchPublications: (pageNo, pageSize) => {
        dispatch(setZenodoLoading(true));
        dispatch(fetchPublications(pageNo, pageSize))
    },
    updatePageTitle: () => dispatch(updatePageTitle("Zenodo Overview"))
});

const mapStateToProps = (state) => state.zenodo;
export default connect(mapStateToProps, mapDispatchToProps)(ZenodoHome);
