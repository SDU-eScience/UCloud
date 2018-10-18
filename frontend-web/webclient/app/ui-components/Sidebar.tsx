import * as React from "react";
import styled from "styled-components";
import Text from './Text'
import Icon from './Icon'
import Flex from './Flex'
import Box from './Box'
import Link from "./Link";


const SideBarContainer = styled(Flex)`
    position: fixed;
    top:48px;
    //width: 190px;
    //margin-top: 48px;
    height: 100%;
    flex-flow: column;
    border-right: 1px solid ${props => props.theme.colors["gray"]}
`;

const SideBarElementContainer = styled(Flex)`
    justify-content: left;
    flex-flow: row;
    align-items: center;
    clear: none;
    :hover {
        color: ${props => props.theme.colors["blue"]};
        cursor: pointer;
        svg {
            filter: saturate(500%);
        }
    }
`

const SideBarElement = ({ icon, label, showLabel, to }) => (
    <Link to={to}>
        <SideBarElementContainer >
            <Flex mx="22px" alignItems='center'> 
                <Icon name={icon} size="24" />
            </Flex>
            {showLabel &&
                <Text fontSize={3}>
                    {label}
                </Text>
            }
        </SideBarElementContainer>
    </Link>
);

const SideBarSpacer = (props) => (
    <Box mt="20px" />
);

type MenuElement = { icon: string, label: string, to: string };
export const sideBarMenuElements: MenuElement[] = [
    { icon: "dashboard", label: "Dashboard", to: "/dashboard/" },
    { icon: "files", label: "Files", to: "/files/" },
    { icon: "shares", label: "Shares", to: "/shares/"},
    { icon: "apps", label: "Apps", to: "/applications/" },
    { icon: "publish", label: "Publish", to: "/zenodo/publish/" },
    { icon: "activity", label: "Activity", to: "/activity/" },
    { icon: "admin", label: "Admin", to: "/admin/userCreation/" },
];

const Sidebar = ({ sideBarEntries = sideBarMenuElements, showLabel = true }: { sideBarEntries?: any, showLabel?: boolean }) => (
    <SideBarContainer color='darkGray' bg='lightGray' width={["auto", "190px"]}>
        {sideBarMenuElements.map(({ icon, label, to }) => (
            <React.Fragment key={label}>
                <SideBarSpacer />
                <SideBarElement icon={icon} label={label} showLabel={showLabel} to={to} />
            </React.Fragment>
        ))}
    </SideBarContainer>
);

export default Sidebar;