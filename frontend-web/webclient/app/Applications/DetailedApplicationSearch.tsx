import * as React from "react";
import { Flex, Input } from "ui-components";
import * as Heading from "ui-components/Heading";
import { ReduxObject } from "DefaultObjects";
import { DetailedApplicationSearchReduxState } from "Applications";
import { connect } from "react-redux";

class DetailedApplicationSearch extends React.Component<DetailedApplicationSearchReduxState> {
    constructor(props) {
        super(props);
    }

    searchInput: any;

    render() {
        return (
            <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                <Heading.h3>Advanced File Search</Heading.h3>
                <form>
                    <Input
                        placeholder="Search by name..."
                        ref={this.searchInput}
                        onChange={({ target: { value } }) => window.console.log(value)}
                    />
                </form>
            </Flex>)
    }
}

const mapStateToProps = ({ }: ReduxObject) => ({});

export default connect()(DetailedApplicationSearch);