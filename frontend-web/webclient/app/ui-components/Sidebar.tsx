import * as React from "react";
import styled from "styled-components";
import Text from "./Text";
import Icon, { IconName } from "./Icon";
import Flex from "./Flex";
import Box from "./Box";
import Link from "./Link";
import Divider from "./Divider";
import { Cloud } from "Authentication/SDUCloudObject";


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
interface SidebarElementProps { icon: IconName, label: string, showLabel: boolean, to: string }
const SideBarElement = ({ icon, label, showLabel, to }: SidebarElementProps) => (
    <Link to={to}>
        <SideBarElementContainer >
            <Flex mx="22px" alignItems='center'>
                <Icon cursor="pointer" name={icon} size="24" />
            </Flex>
            {showLabel &&
                <Text cursor="pointer" fontSize={3} bold>
                    {label}
                </Text>
            }
        </SideBarElementContainer>
    </Link>
);

const SideBarSpacer = () => (
    <Box mt="20px" />
);

type MenuElement = { icon: IconName, label: string, to: string };
type SidebarMenuElements = {
    items: MenuElement[]
    predicate: () => boolean
}

export const sideBarMenuElements: { general: SidebarMenuElements, auditing: SidebarMenuElements, admin: SidebarMenuElements } = {
    general: {
        items: [
            { icon: "dashboard", label: "Dashboard", to: "/dashboard/" },
            { icon: "files", label: "Files", to: "/files/" },
            { icon: "shares", label: "Shares", to: "/shares/" },
            { icon: "apps", label: "Apps", to: "/applications/" },
            { icon: "publish", label: "Publish", to: "/zenodo/publish/" },
        ], predicate: () => true
    },
    auditing: { items: [{ icon: "activity", label: "Activity", to: "/activity/" }], predicate: () => true },
    admin: { items: [{ icon: "admin", label: "Admin", to: "/admin/userCreation/" }], predicate: () => Cloud.userIsAdmin }
};

const Sidebar = ({ sideBarEntries = sideBarMenuElements, showLabel = true }: { sideBarEntries?: any, showLabel?: boolean }) => (
    <SideBarContainer color="darkGray" bg="lightGray" width={["auto", "190px"]}>
        {Object.keys(sideBarMenuElements).map((it, iteration) =>
            <React.Fragment key={iteration}>
                {sideBarMenuElements[it].items.map(({ icon, label, to }) => (
                    <React.Fragment key={label}>
                        {iteration === 0 ? <SideBarSpacer /> : null}
                        <SideBarElement icon={icon} label={label} showLabel={showLabel} to={to} />
                    </React.Fragment>))}
                {iteration !== Object.keys(sideBarMenuElements).length - 1 ? (<Divider mt="10px" mb="10px" />) : null}
            </React.Fragment>
        )}
    </SideBarContainer>
);

export default Sidebar;