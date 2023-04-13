import {Avatar} from "@/AvataaarLib";
import * as React from "react";
import Flex from "@/ui-components/Flex";
import {AvatarType} from "@/UserSettings/Avataaar";

interface UserAvatar {
    mr?: string;
    avatar: AvatarType;
    width?: string;
    height?: string;
    mx?: string;
    avatarStyle?: string;
}

export const UserAvatar = ({avatar, avatarStyle = "Circle", width = "60px", height = "48px", mx = "8px"}: UserAvatar): JSX.Element => (
    <Flex overflow="hidden" mx={mx} width={width} alignItems="center" height={height}>
        <Avatar avatarStyle={avatarStyle} {...avatar} />
    </Flex>
);
