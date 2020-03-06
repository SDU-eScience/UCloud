import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Input, Box, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {defaultAvatar} from "UserSettings/Avataaar";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import {Page} from "Types";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {listGroupMembersRequest} from "./api";


interface DetailedGroupViewProps {
    name: string;
}

function DetailedGroupView({name}: DetailedGroupViewProps): JSX.Element {
    const [activeGroup, fetchActiveGroup, params] = useCloudAPI<Page<string>>(
        listGroupMembersRequest({group: name ?? ""}),
        emptyPage
    );

    React.useEffect(() => {
        if (name)
            fetchActiveGroup(listGroupMembersRequest({group: name ?? ""}));
    }, [name]);

    if (activeGroup.loading) return <LoadingSpinner size={24} />;
    if (activeGroup.error) return <MainContainer main={
        <Text fontSize={"24px"}>Could not fetch &apos;{name}&apos;.</Text>
    } />;

    return <MainContainer
        main={
            <>
                <Text
                    style={{wordBreak: "break-word"}}
                    fontSize="25px"
                    width="100%"
                >{name}</Text>
                <Heading.h5>Members: {activeGroup.data.itemsInTotal}</Heading.h5>
                <Input type="text" />
                <div>
                    <Box overflowY="scroll">
                        {activeGroup.data.items.map(member =>
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
