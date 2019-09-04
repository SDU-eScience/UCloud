import {Cloud} from "Authentication/SDUCloudObject";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {RouteComponentProps} from "react-router";
import * as Heading from "ui-components/Heading";

const Tool: React.FunctionComponent<RouteComponentProps> = props => {
    // tslint:disable-next-line
    const name = props.match.params["name"];
    if (Cloud.userRole !== "ADMIN") return null;

    return <MainContainer
        header={<Heading.h1>Hello!</Heading.h1>}
        main={
            <>
                Hi, {name}!
            </>
        }
    />;
};

export default Tool;
