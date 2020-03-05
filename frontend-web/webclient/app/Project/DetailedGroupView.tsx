import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Input, Box, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {defaultAvatar} from "UserSettings/Avataaar";
import {Page} from "Types";

interface DetailedGroupViewProps {
    members: Page<string>;
    name: string;
}

function DetailedGroupView({members, name}: DetailedGroupViewProps): JSX.Element {
    const [avatars, setAvatars] = React.useState([]);
    return <MainContainer
        main={
            <>
                <Text
                    style={{wordBreak: "break-word"}}
                    fontSize="25px"
                    width="100%"
                >{name}</Text>
                <Heading.h5>Members: {members.itemsInTotal}</Heading.h5>
                <Input type="text" />
                <div>
                    <Box overflowY="scroll">
                        {members.items.map(member =>
                            <Flex key={member}>
                                <UserAvatar avatar={defaultAvatar} /> <Text mt="10px">{member}</Text>
                            </Flex>
                        )}
                    </Box>
                </div>
            </>
        }
    />;
}

export default DetailedGroupView;
