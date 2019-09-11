import * as React from "react";
import styled from "styled-components";
import * as Heading from "ui-components/Heading"

const HeaderStyle = styled(Heading.h1)`
    > small {
        padding-left: 10px;
        font-size: 50%;
    }
`;

export const Header: React.FunctionComponent<{ name: string, version: string }> = props => (
    <HeaderStyle>
        {props.name}
        <small>v. {props.version}</small>
    </HeaderStyle>
);
