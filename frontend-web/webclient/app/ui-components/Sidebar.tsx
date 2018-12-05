import * as React from "react";
import styled from "styled-components";
import Text from "./Text";
import Icon, { IconName } from "./Icon";
import Flex from "./Flex";
import Box from "./Box";
import Link from "./Link";
import Divider from "./Divider";
import { Cloud } from "Authentication/SDUCloudObject";
import { PP } from "UtilityComponents";
import { fileTablePage } from "Utilities/FileUtilities";
import * as Heading from "ui-components/Heading";
import { Button } from "ui-components";
import { successNotification, failureNotification } from "UtilityFunctions";
import Relative from "./Relative";
import TextArea from "./TextArea";

const SidebarContainer = styled(Flex)`
    position: fixed;
    top:48px;
    height: calc(100% - 48px);
    flex-flow: column;
    border-right: 1px solid ${props => props.theme.colors.borderGray}
`;

const SidebarElementContainer = styled(Flex)`
    justify-content: left;
    flex-flow: row;
    align-items: center;
    :hover {
        svg {
            filter: saturate(500%);
        }
    }
`
interface SidebarElementProps { icon: IconName, label: string, showLabel: boolean, to: string }
const SidebarElement = ({ icon, label, showLabel, to }: SidebarElementProps) => (
    <Link to={to}>
        <SidebarElementContainer >
            <Flex mx="22px" alignItems='center'>
                <Icon cursor="pointer" name={icon} color="iconColor" color2="iconColor2" size="24" />
            </Flex>
            {showLabel &&
                <Text cursor="pointer" fontSize={3} >
                    {label}
                </Text>
            }
        </SidebarElementContainer>
    </Link>
);

const SidebarSpacer = () => (
    <Box mt="20px" />
);

const SidebarPushToBottom = styled.div`
    flex-grow: 1;
`;

const SidebarInfoBox = styled.div`
    flex-shrink: 0;
    margin: 22px;
    color: ${props => props.theme.colors.iconColor};

    & div {
        width: 100%;
    }

    & a {
        color: ${props => props.theme.colors.iconColor};
    }

    & a:hover {
        color: ${props => props.theme.colors.blue};
    }
`;

type MenuElement = { icon: IconName, label: string, to: string };
type SidebarMenuElements = {
    items: MenuElement[]
    predicate: () => boolean
}


// FIXME, move to own file
const SupportBox = styled.div<{ visible: boolean }>`
    display: ${props => props.visible ? "block" : "none"}
    position: absolute;
    left: 150px;
    top: -282px;
    border: 1px solid ${props => props.theme.colors.borderGray};
    background-color: ${props => props.theme.colors.white};

    &&&&&&&&&&& {
        width: 600px;
        height: 300px;
    }

    &:before {
        display: block;
        width: 16px;
        height: 16px;
        content: '';
        transform: rotate(45deg);
        position: relative;
        top: 265px;
        left: -9px;
        background: ${props => props.theme.colors.white};
        border-left: 1px solid ${props => props.theme.colors.borderGray};
        border-bottom: 1px solid ${props => props.theme.colors.borderGray};
    }

    & ${TextArea} {
        width: 100%;
        border: 1px solid ${props => props.theme.colors.borderGray};
    }

    & ${Box} {
        margin: 16px;
        overflow-y: auto;
        height: calc(100% - 32px);
        width: calc(100% - 32px);
    }
`;

interface SupportState {
    visible: boolean
    loading: boolean
}

class Support extends React.Component<{}, SupportState> {
    private textArea = React.createRef<HTMLTextAreaElement>();

    constructor(props) {
        super(props);

        this.state = {
            visible: false,
            loading: false
        };
    }

    onSupportClick(event: React.SyntheticEvent) {
        event.preventDefault();
        this.setState(() => ({ visible: !this.state.visible }));
    }

    onSubmit(event: React.FormEvent) {
        event.preventDefault();
        const text = this.textArea.current;
        if (!!text) {
            this.setState(() => ({ loading: true }));
            Cloud.post("/support/ticket", { message: text.value }).then(e => {
                text.value = "";
                this.setState(({ visible: false, loading: false }));
                successNotification("Support ticket submitted!");
            }).catch(e => {
                if (!!e.response.why) {
                    failureNotification(e.response.why);
                } else {
                    failureNotification("An error occured");
                }
            });
        }
    }

    render() {
        return <div>
            <a href="#support" onClick={e => this.onSupportClick(e)}><Text fontSize={1}>Support</Text></a>
            <Relative>
                <SupportBox visible={this.state.visible}>
                    <Box>
                        <Heading.h3>Support Form</Heading.h3>
                        <p>Describe your problem below and we will investigate it.</p>
                        <form onSubmit={e => this.onSubmit(e)}>
                            <TextArea ref={this.textArea} rows={6}/>
                            <Button fullWidth type="submit" disabled={this.state.loading}>Submit</Button>
                        </form>
                    </Box>
                </SupportBox>
            </Relative>
        </div>;
    }
}

export const sideBarMenuElements: { general: SidebarMenuElements, auditing: SidebarMenuElements, admin: SidebarMenuElements } = {
    general: {
        items: [
            { icon: "dashboard", label: "Dashboard", to: "/dashboard/" },
            { icon: "files", label: "Files", to: fileTablePage(Cloud.homeFolder) },
            { icon: "share", label: "Shares", to: "/shares/" },
            { icon: "apps", label: "Apps", to: "/applications/" },
            { icon: "information", label: "App Results", to: "/applications/results/" },
            { icon: "publish", label: "Publish", to: "/zenodo/publish/" },
        ], predicate: () => true
    },
    auditing: { items: [{ icon: "activity", label: "Activity", to: "/activity/" }], predicate: () => true },
    admin: { items: [{ icon: "admin", label: "Admin", to: "/admin/userCreation/" }], predicate: () => Cloud.userIsAdmin }
};

const Sidebar = ({ sideBarEntries = sideBarMenuElements, showLabel = true }: { sideBarEntries?: any, showLabel?: boolean }) => {
    let sidebar = Object.keys(sideBarEntries)
        .map(key => sideBarEntries[key])
        .filter(it => it.predicate());
    return (
        <SidebarContainer color="text" bg="lightGray" width={190}>
            {sidebar.map((category, categoryIdx) =>
                <React.Fragment key={categoryIdx}>
                    {category.items.map(({ icon, label, to }: MenuElement) => (
                        <React.Fragment key={label}>
                            {categoryIdx === 0 ? <SidebarSpacer /> : null}
                            <SidebarElement icon={icon} label={label} showLabel={showLabel} to={to} />
                        </React.Fragment>))}
                    {categoryIdx !== sidebar.length - 1 ? (<Divider mt="10px" mb="10px" />) : null}
                </React.Fragment>
            )}
            <SidebarPushToBottom />

            <SidebarInfoBox>
                <Text fontSize={1}>ID: {Cloud.username}</Text>
                <SidebarSpacer />
                <Support />
                <div>
                    <a href="https://www.sdu.dk/en/om_sdu/om_dette_websted/databeskyttelse" target="_blank" rel="noopener"><Text fontSize={1}>Data Protection at SDU</Text></a>
                </div>
            </SidebarInfoBox>
            <PP visible={false} />

        </SidebarContainer>
    );
};

export default Sidebar;