import * as React from "react";
import { Link, Box, Icon } from "ui-components";
import { toLowerCaseAndCapitalize } from "UtilityFunctions";
import { NotConnectedToZenodo } from "Utilities/ZenodoPublishingUtilities";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { fetchPublications, setZenodoLoading, setErrorMessage } from "./Redux/ZenodoActions";
import { SET_ZENODO_ERROR } from "./Redux/ZenodoReducer";
import { connect } from "react-redux";
import { dateToString } from "Utilities/DateUtilities";
import { List } from "Pagination/List";
import { ZenodoHomeProps, ZenodoHomeState, ZenodoOperations } from ".";
import { Dispatch } from "redux";
import { OutlineButton } from "ui-components";
import * as Heading from "ui-components/Heading";
import { MainContainer } from "MainContainer/MainContainer";
import Table, { TableHeaderCell, TableRow, TableCell, TableBody, TableHeader } from "ui-components/Table";
import ClickableDropdown from "ui-components/ClickableDropdown";

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
        const { connected, loading, fetchPublications, page, error, onErrorDismiss } = this.props;
        if (!connected && !loading) {
            return (<NotConnectedToZenodo />);
        } else {
            return (
                <MainContainer
                    header={
                        <>
                            <Heading.h2>Upload progress</Heading.h2>
                            <Heading.h5>Connected to Zenodo</Heading.h5>
                        </>
                    }
                    main={
                        <List
                            onRefresh={() => fetchPublications(page.pageNumber, page.itemsPerPage)}
                            loading={loading}
                            errorMessage={error}
                            onErrorDismiss={onErrorDismiss}
                            customEmptyPage={<Heading.h6>No Zenodo publications found.</Heading.h6>}
                            pageRenderer={(page) => (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHeaderCell textAlign="left">ID</TableHeaderCell>
                                            <TableHeaderCell textAlign="left">Name</TableHeaderCell>
                                            <TableHeaderCell textAlign="left">Status</TableHeaderCell>
                                            <TableHeaderCell textAlign="left">Last update</TableHeaderCell>
                                            <TableHeaderCell />
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {page.items.map((it, i) => (<PublicationRow publication={it} key={i} />))}
                                    </TableBody>
                                </Table>
                            )}
                            page={page}
                            onItemsPerPageChanged={size => fetchPublications(0, size)}
                            onPageChanged={pageNumber => fetchPublications(pageNumber, page.itemsPerPage)}
                        />}
                    sidebar={
                        <Link to="/zenodo/publish/">
                            <OutlineButton fullWidth color="blue">Create new upload</OutlineButton>
                        </Link>
                    }
                />
            );
        }
    }
}

const PublicationRow = ({ publication }) => {
    const actionButton = publication.zenodoAction ? (
        <a href={publication.zenodoAction} target="_blank" rel="noopener">Finish at Zenodo</a>
    ) : null;
    return (
        <TableRow>
            <TableCell>{publication.id}</TableCell>
            <TableCell>{publication.name}</TableCell>
            <TableCell>{toLowerCaseAndCapitalize(publication.status)}</TableCell>
            <TableCell>{dateToString(publication.modifiedAt)}</TableCell>
            <TableCell>
                <ClickableDropdown width="145px" trigger={<Icon name="ellipsis" />}>
                    <Box ml="-17px" mr="-17px" pl="17px">
                        <Link to={`/zenodo/info/${encodeURIComponent(publication.id)}`}>Show More</Link>
                    </Box>
                    <Box ml="-17px" mr="-17px" pl="17px">
                        {actionButton}
                    </Box>
                </ClickableDropdown>
            </TableCell>
        </TableRow>);
}

const mapDispatchToProps = (dispatch: Dispatch): ZenodoOperations => ({
    onErrorDismiss: () => dispatch(setErrorMessage(SET_ZENODO_ERROR, undefined)),
    fetchPublications: async (pageNo, pageSize) => {
        dispatch(setZenodoLoading(true));
        dispatch(await fetchPublications(pageNo, pageSize))
    },
    updatePageTitle: () => dispatch(updatePageTitle("Zenodo Overview"))
});

const mapStateToProps = (state) => state.zenodo;
export default connect(mapStateToProps, mapDispatchToProps)(ZenodoHome);