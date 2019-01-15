import * as React from "react";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import { Spacer } from "ui-components/Spacer";
import { Box, Button, Select, Flex } from "ui-components";
import { successNotification, prettierString } from "UtilityFunctions";
import { connect } from "react-redux";
import { UserAvatar } from "Navigation/Header";
import { defaultAvatar } from "UserSettings/Avataaar";
import styled from "styled-components";
import { Dispatch } from "redux";
import { fetchProjectMembers, setError } from "./Redux/ManagementActions";
import { ReduxObject } from "DefaultObjects";

enum ProjectRole {
    PI = "PI",
    ADMIN = "ADMIN",
    DATA_STEWARD = "DATA_STEWARD",
    USER = "USER"
}

class Management extends React.Component<ManagementOperations, {
    projectName: string, memberCount: number, admins: string[], datastewards: string[],
    users: string[], project: any
}> {
    constructor(props) {
        super(props);
        this.state = {
            project: undefined,
            projectName: "projectName",
            memberCount: 5,
            admins: ["Foo", "Bar"],
            datastewards: ["Foo", "Bar"],
            users: ["Foo", "Bar"]
        };
        console.log((this.props as any).location)
        this.props.fetchProjectMembers("17");
    }

    render() {
        const { ...state } = this.state;
        const header = (<>
            <Heading.h3>Project Management: <b>{state.projectName}</b></Heading.h3>

        </>)
        const main = (<>
            <Spacer
                left={<Box>{state.memberCount} Members</Box>}
                right={<Button onClick={() => successNotification("Wouldn't it be great if this button worked?")}>Invite member</Button>}
            />
            <MemberList members={state.admins} title="Admins" except="ADMIN" setRole={(b) => console.log(b)} />
            <MemberList members={state.datastewards} title="Data Stewards" except="DATA_STEWARD" setRole={(b) => console.log(b)} />
            <MemberList members={state.users} title="Users" except="USER" setRole={(b) => console.log(b)} />
        </>);


        return (
            <MainContainer
                main={main}
                header={header}
            />
        )
    }
}

interface Admins { members: string[], title: string, except: string, setRole: (role: string) => void }
const MemberList = ({ members, setRole, title, except }: Admins) => (
    <Box mb="1.5em">
        <Heading.h3>{title}</Heading.h3>
        {members.map(it => (<Spacer
            left={<>
                <UserAvatar avatar={defaultAvatar} />
                {it}
            </>}
            right={<Flex>
                {/* Check if member role is equal to select, if so, render button  */}
                <MemberSelect>
                    {Object.keys(ProjectRole).filter(it => it !== "PI").map(r =>
                        except !== r ? <option onClick={() => setRole(r)}>{prettierString(r)}</option> : null
                    )}
                </MemberSelect>
                <Button ml="30px" color="red">Remove</Button>
            </Flex>}
        />))}
    </Box>);


const MemberSelect = styled(Select)`
    min-width: 200px;
`;


interface ManagementOperations {
    fetchProjectMembers: (id: string) => void
    clearError: () => void
}

// TODO
const mapStateToProps = (state: ReduxObject) => state;

const mapDispatchToProps = (dispatch: Dispatch): ManagementOperations => ({
    fetchProjectMembers: async id => dispatch(await fetchProjectMembers(id)),
    clearError: () => dispatch(setError())
});

export default connect<void, ManagementOperations>(undefined, mapDispatchToProps)(Management);