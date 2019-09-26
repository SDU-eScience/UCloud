import * as React from "react";
import styled from "styled-components";
import Flex from "ui-components/Flex";
import {SpaceProps} from "styled-system";
import {AvatarType} from "UserSettings/Avataaar";
import {Avatar} from "AvataaarLib";

const ClippedBox = styled(Flex)`
    overflow: hidden;
`;

interface UserAvatar extends SpaceProps {
    avatar: AvatarType;
    width?: string;
}

export const UserAvatar = ({avatar, width = "60px"}: UserAvatar) => (
    <ClippedBox mx="8px" width={width} alignItems="center" height="48px">
        <Avatar
            avatarStyle="Circle"
            topType={avatar.top}
            accessoriesType={avatar.topAccessory}
            hairColor={avatar.hairColor}
            facialHairType={avatar.facialHair}
            facialHairColor={avatar.facialHairColor}
            clotheType={avatar.clothes}
            clotheColor={avatar.colorFabric}
            graphicType={avatar.clothesGraphic}
            eyeType={avatar.eyes}
            eyebrowType={avatar.eyebrows}
            mouthType={avatar.mouthTypes}
            skinColor={avatar.skinColors}
        />
    </ClippedBox>);