import * as React from "react";
import { Card } from "ui-components/Card";
import { Flex, ToggleBadge } from "ui-components";
import { Link } from "react-router-dom";

export const Tabs: React.StatelessComponent<{}> = (props) => (
    <Card>
        <Flex>
            {props.children}
        </Flex>
    </Card>
);

interface TabProps {
    selected?: boolean
    linkTo: string
}

export const Tab: React.StatelessComponent<TabProps> = (props): JSX.Element => (
    <ToggleBadge
        bg="lightGray"
        pb="12px"
        pt="10px"
        fontSize={2}
        color={"black"}
        selected={props.selected}
    >
        <Link to={props.linkTo}>{props.children}</Link>
    </ToggleBadge>
);

export const Header = (props: { selected: Pages }) => (
    <Tabs>
        <Tab linkTo="" selected={props.selected === Pages.BROWSE}>Browse</Tab>
        <Tab linkTo="" selected={props.selected === Pages.INSTALLED}>Installed</Tab>
    </Tabs>
);

export enum Pages {
    INSTALLED,
    BROWSE
}