import * as React from "react";
import { Card } from "semantic-ui-react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { emptyPage } from "DefaultObjects";

class Projects extends React.Component<any, any> {

    componentDidMount() {
        //fetch projects
    }

    render() {
        return (
            <React.StrictMode>
                <Pagination.List
                    page={emptyPage}
                    pageRenderer={(page) => (
                        <Card.Group>
                            <Card content="Empty" />
                        </Card.Group>
                    )}
                    loading={this.props.loading}
                    onPageChanged={(pageNumber) => null}
                    onItemsPerPageChanged={(itemsPerPage: number) => null}
                />
            </React.StrictMode>
        )
    }
}

const mapStateToProps = (state) => null;
const mapDispatchToProps = (dispatch) => null;

export default connect()(Projects);