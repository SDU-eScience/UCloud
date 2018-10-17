import * as React from "react";
import styled from "styled-components";
import Text from './Text'
import Icon from './Icon'
import Flex from './Flex'
import Box from './Box'
import theme from "./theme";
import { string } from "prop-types";


const SideBarContainer = styled(Flex)`
    position: fixed;
    top: 0;
    width: 190px;
    margin-top: 48px;
    height: 100%;
    flex-flow: column;
    border-right: 1px solid ${props => props.theme.colors["gray"]}
`;

const SideBarElementContainer = styled(Flex)`
    justify-content: left;
    flex-flow: row;
    align-items: center;
    clear: none;
`

const SideBarElement =  ({ icon, label, showLabel }) => (
    <SideBarElementContainer pl="24px">
        <Icon name={icon} size="24" />
        { showLabel &&
            <Text fontSize={3} pl="20px">
                {label}
            </Text>
        }
    </SideBarElementContainer>

);

const SideBarSpacer = (props) => (
    <Box mt="20px" />
);

export const sideBarMenuElements = [{icon: "dashboard", label: "Dashboard"},
                                    {icon: "files", label: "Files"},
                                    {icon: "apps", label: "Apps"},
                                    {icon: "publish", label: "Publish"},
                                    {icon: "activity", label: "Activity"},
                                    {icon: "admin", label: "Admin"},
                                    ];

const Sidebar = ({sideBarEntries, showLabel}) => (
    <SideBarContainer color='darkGray' bg='lightGray'>
        {sideBarMenuElements.map( ({icon, label}) => (
            <>
                <SideBarSpacer />
                <SideBarElement icon={icon} label={label} showLabel={showLabel} />
            </>
        ))}
    </SideBarContainer>
);

export default Sidebar