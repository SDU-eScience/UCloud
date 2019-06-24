import * as React from "react";
import * as API from "./api";
import * as DataTypes from "./DataTypes";
import {Box, Link} from "ui-components";
import * as Heading from "ui-components/Heading";
import {resourceName, emptyResourceState} from "./Redux/AccountingObject";
import * as Actions from "./Redux/AccountingActions";
import {connect} from "react-redux";
import {ReduxObject} from "DefaultObjects";
import {Dispatch} from "redux";
import {LoadableContent} from "LoadableContent";
import Spinner from "LoadingIcon/LoadingIcon";
import {detailedPage} from "Accounting";

interface UsageOwnProps {
    resource: string
    subResource: string
    renderTitle?: boolean
}

interface UsageOperations {
    refresh: () => void
}

interface UsageStateProps {
    usage: LoadableContent<API.Usage>
}

type UsageProps = UsageStateProps & UsageOperations & UsageOwnProps;

const Container: React.FunctionComponent = props => (
    <Box textAlign="center">{props.children}</Box>
);

const Quota: React.FunctionComponent<{usage: API.Usage}> = props => {
    const {usage} = props;
    if (usage.quota === null || usage.quota === undefined) return null;

    const percentage = ((usage.usage / usage.quota) * 100).toFixed(2);
    return <>({percentage}%)</>;
};

const Usage: React.FunctionComponent<{
    usage: API.Usage,
    resource: string,
    subResource: string,
    renderTitle?: boolean
}> = props => {
    const {usage} = props;
    const type = (usage.dataType || DataTypes.NUMBER);
    return <>
        <Heading.h2 title={API.formatDataTypeLong(type, usage.usage)}>
            {API.formatDataType(type, usage.usage)}
        </Heading.h2>

        <Heading.h4>
            {props.renderTitle ? usage.title : null}
            {" "}
            <Quota usage={usage} />
            <Box><Link to={detailedPage(props.resource, props.subResource)}>View Chart</Link></Box>
        </Heading.h4>
    </>;
};

class UsageContainer extends React.Component<UsageProps> {
    componentDidMount() {
        if (this.props.usage.content === undefined) {
            this.props.refresh();
        }
    }

    content(): React.ReactNode {
        const usage = this.props.usage;
        const content = usage.content;
        if (!!content) {
            return <Usage
                resource={this.props.resource}
                subResource={this.props.subResource}
                usage={content}
                renderTitle={this.props.renderTitle}
            />;
        } else {
            if (!!usage.error) return usage.error.errorMessage;
            else return <Spinner size={24} />;
        }
    }

    render() {
        return <Container>{this.content()}</Container>;
    }
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type>, ownProps: UsageOwnProps): UsageOperations => ({
    refresh: async () => dispatch(await Actions.fetchUsage(ownProps.resource, ownProps.subResource))
});

const mapStateToProps = (state: ReduxObject, ownProps: UsageOwnProps): UsageStateProps => {
    const name = resourceName(ownProps.resource, ownProps.subResource);
    const resource = state.accounting.resources[name] || emptyResourceState();
    return {usage: resource.usage};
};

export default connect(mapStateToProps, mapDispatchToProps)(UsageContainer);