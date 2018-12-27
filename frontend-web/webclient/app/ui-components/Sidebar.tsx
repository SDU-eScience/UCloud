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
import { Button, ExternalLink } from "ui-components";
import { successNotification, failureNotification } from "UtilityFunctions";
import Relative from "./Relative";
import TextArea from "./TextArea";
import { KeyCode } from "DefaultObjects";

const SidebarContainer = styled(Flex)`
    position: fixed;
    z-index: 100;
    top: 48px;
    height: calc(100% - 48px);
    flex-flow: column;
    border-right: 1px solid ${props => props.theme.colors.borderGray};
`;

const SidebarElementContainer = styled(Flex)`
    justify-content: left;
    flex-flow: row;
    align-items: center;
    &:hover {
        svg {
            filter: saturate(500%);
        }
    }
`;

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
    margin: 18px;
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
type SupportBoxProps = { visible: boolean }
const SupportBox = styled.div<SupportBoxProps>`
    display: ${props => props.visible ? "block" : "none"};
    position: absolute;
    left: 150px;
    top: -320px;
    border: 1px solid ${props => props.theme.colors.borderGray};
    background-color: ${props => props.theme.colors.white};

    &&&&&&&&&&& {
        width: 600px;
        height: 350px;
    }

    &:before {
        display: block;
        width: 16px;
        height: 16px;
        content: '';
        transform: rotate(45deg);
        position: relative;
        top: 300px;
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
    private supportBox = React.createRef<HTMLDivElement>();

    constructor(props) {
        super(props);

        this.state = {
            visible: false,
            loading: false
        };
        document.addEventListener("keydown", this.handleESC);
        document.addEventListener("mousedown", this.handleClickOutside);
    }

    componentWillUnmount = () => {
        document.removeEventListener("keydown", this.handleESC);
        document.removeEventListener("mousedown", this.handleClickOutside);
    }

    private handleESC = (e) => {
        if (e.keyCode == KeyCode.ESC) this.setState(() => ({ visible: false }))
    }

    onSupportClick(event: React.SyntheticEvent) {
        event.preventDefault();
        this.setState(() => ({ visible: !this.state.visible }));
    }

    private handleClickOutside = event => {
        if (this.supportBox.current && !this.supportBox.current.contains(event.target) && this.state.visible)
            this.setState(() => ({ visible: false }));
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
            <Link to="#support" onClick={e => this.onSupportClick(e)}><Text fontSize={1}><Icon name={"chat"} size="1em" color2="lightGray" /> Support</Text></Link>
            <Relative>
                <SupportBox ref={this.supportBox} visible={this.state.visible}>
                    <Box>
                        <Heading.h3>Support Form</Heading.h3>
                        <p>Describe your problem below and we will investigate it.</p>
                        <form onSubmit={e => this.onSubmit(e)}>
                            <TextArea ref={this.textArea} rows={6} />
                            <Button fullWidth type="submit" disabled={this.state.loading}>Submit</Button>
                        </form>
                    </Box>
                </SupportBox>
            </Relative>
        </div>;
    }
}

export const sideBarMenuElements: { general: SidebarMenuElements, dev: SidebarMenuElements, auditing: SidebarMenuElements, admin: SidebarMenuElements } = {
    general: {
        items: [
            { icon: "dashboard", label: "Dashboard", to: "/dashboard/" },
            { icon: "files", label: "Files", to: fileTablePage(Cloud.homeFolder) },
            { icon: "share", label: "Shares", to: "/shares/" },
            { icon: "apps", label: "My Apps", to: "/applications/installed/" },
            { icon: "appStore", label: "App Store", to: "/applications/" },
            { icon: "results", label: "My Results", to: "/applications/results/" }
        ], predicate: () => true
    },
    dev: { items: [{ icon: "publish", label: "Publish", to: "/zenodo/publish/" }], predicate: () => process.env.NODE_ENV === "development" },
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
                <Text fontSize={1}><Icon name={"id"} size="1em" /> {Cloud.username}</Text>
                {/* <SidebarSpacer /> */}
                <Support />
                <div>
                    <ExternalLink href="https://www.sdu.dk/en/om_sdu/om_dette_websted/databeskyttelse">
                        <Text fontSize={1}><Icon name="verified" size="1em" color2="lightGray" /> SDU Data Protection</Text>
                    </ExternalLink>
                </div>
            </SidebarInfoBox>
            <PP visible={false} />

        </SidebarContainer>
    );
};

export default Sidebar;