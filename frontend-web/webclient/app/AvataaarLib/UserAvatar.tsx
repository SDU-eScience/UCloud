import * as React from "react";
import Flex from "@/ui-components/Flex";
import {AvatarType} from ".";
import Avatar from "./avatar";
import {useAvatars} from "@/AvataaarLib/hook";

interface UserAvatar {
    mr?: string;
    avatar: AvatarType;
    width?: string;
    height?: string;
    mx?: string;
    avatarStyle?: "Circle" | "Transparent";
}

export function UserAvatar({avatar, avatarStyle = "Circle", width = "60px", height = "48px", mx = "8px"}: UserAvatar): React.ReactNode {
    return (
        <Flex overflow="hidden" mx={mx} width={width} alignItems="center" height={height}>
            <Avatar avatarStyle={avatarStyle} {...avatar} />
        </Flex>
    );
}

export const AvatarForUser: React.FunctionComponent<{
    width?: string;
    height?: string;
    avatarStyle?: "Circle" | "Transparent";
    username: string;
}> = props => {
    const avatars = useAvatars();
    return <UserAvatar avatar={avatars.avatar(props.username)} width={props.width} height={props.height} avatarStyle={props.avatarStyle}/>
}