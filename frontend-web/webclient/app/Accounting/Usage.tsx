import * as React from "react";
import * as API from "./api";
import { data as MockUsage } from "./mock/usage.json";
import { Card } from "ui-components";
import * as Heading from "ui-components/Heading";

interface UsageProps {
    usage?: API.Usage
}

class Usage extends React.Component<UsageProps> {
    renderQuota(): JSX.Element | null {
        const usage = this.usage;
        if (usage.quota === null || usage.quota === undefined) return null;

        const percentage = ((usage.usage / usage.quota) * 100).toFixed(2);
        return <>({percentage}%)</>;
    }

    get usage(): API.Usage {
        return this.props.usage || MockUsage;
    }

    render() {
        const usage = this.usage;
        return <Card textAlign={"center"}>
            <Heading.h2>{API.formatDataType((usage.dataType || API.DataTypes.NUMBER), usage.usage)}</Heading.h2>
            <Heading.h4>{usage.title} {this.renderQuota()}</Heading.h4>
        </Card>;
    }
}

export default Usage;