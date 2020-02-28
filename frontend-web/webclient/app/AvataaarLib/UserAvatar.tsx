import {Client} from "Authentication/HttpClientInstance";
import {Avatar} from "AvataaarLib";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import styled from "styled-components";
import {SpaceProps} from "styled-system";
import Flex from "ui-components/Flex";
import {AvatarType} from "UserSettings/Avataaar";

const ClippedBox = styled(Flex)`
    overflow: hidden;
`;

interface UserAvatar extends SpaceProps {
    avatar: AvatarType;
    width?: string;
}

export const UserAvatar = ({avatar, width = "60px"}: UserAvatar): JSX.Element => (
    <ClippedBox mx="8px" width={width} alignItems="center" height="48px">
        <Avatar avatarStyle="Circle" {...avatar} />
    </ClippedBox>
);

export function ACLAvatars(props: {members: string[]}): JSX.Element | null {
    const [avatars, setAvatars] = React.useState<AvatarType[]>([]);
    const promises = usePromiseKeeper();
    React.useEffect(() => {
        if (props.members.length === 0) return;
        promises.makeCancelable(
            Client.post<{avatars: {[key: string]: AvatarType}}>("/avatar/bulk", {usernames: props.members})
        ).promise.then(it =>
            setAvatars(Object.values(it.response.avatars))
        ).catch(it => console.warn(it));
    }, [props.members]);
    if (props.members.length === 0) return null;
    return (<Flex><AvatarList avatars={avatars} /></Flex>);
}

function AvatarList(props: {avatars: AvatarType[]}): JSX.Element {
    return (
        <WrapperWrapper>
            {props.avatars.map((a, i) => (
                <Flex
                    height="38px"
                    zIndex={props.avatars.length - i}
                    alignItems="center"
                    key={i}
                >
                    <Avatar avatarStyle="Circle" {...a} />
                </Flex>
            ))}
        </WrapperWrapper>
    );
}

const WrapperWrapper = styled(Flex)`
    & > ${Flex} > svg {
        height: 34px;
        width: 34px;
        margin-right: -17px;
    }

    & > ${Flex}:last-child > svg {
        margin-right: 0px;
    }
`;
