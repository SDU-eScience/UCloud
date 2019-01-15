import * as React from "react";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import { Spacer } from "ui-components/Spacer";
import { Box, Button, Select, Flex } from "ui-components";
import { successNotification } from "UtilityFunctions";
import { connect } from "react-redux";
import { UserAvatar } from "Navigation/Header";
import { defaultAvatar } from "UserSettings/Avataaar";
import { Cloud } from "Authentication/SDUCloudObject";
import { viewProjectMembers } from "Utilities/ProjectUtilities";
import styled from "styled-components";
import { Dispatch } from "redux";
import { fetchProjectMembers } from "./Redux/ManagementActions";

class Management extends React.Component<{}, { projectName: string, memberCount: number, admins: string[], datastewards: string[],
    users: string[], project: any }> {
    constructor(props) {
        super(props);
        this.state = {
            project: undefined,
            projectName: "projectName",
            memberCount: 5,
            admins: ["Foo", "Bar"],
            datastewards: ["Foo", "Bar"],
            users: ["Foo", "Bar"]
        }
        Cloud.get(viewProjectMembers("17"));
    }

    render() {
        const { ...state } = this.state;
        const header = (<Heading.h3>Project Management: <b>{state.projectName}</b></Heading.h3>)
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
                    {except === "ADMIN" ? null : <option onClick={() => setRole("ADMIN")}>ADMIN</option>}
                    {except === "DATA_STEWARD" ? null : <option onClick={() => setRole("DATA_STEWARD")}>DATA_STEWARD</option>}
                    {except === "USER" ? null : <option onClick={() => setRole("USER")}>USER</option>}
                </MemberSelect>
                <Button ml="30px" color="red">Remove</Button>
            </Flex>}
        />))}
    </Box>);


const MemberSelect = styled(Select)`
    min-width: 200px;
`;

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchProjectMembers: async id => dispatch(await fetchProjectMembers(id))
});

export default connect()(Management);