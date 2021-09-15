import {Avatar} from "@/AvataaarLib";
import * as React from "react";
import styled from "styled-components";
import {SpaceProps} from "styled-system";
import Flex from "@/ui-components/Flex";
import {AvatarType} from "@/UserSettings/Avataaar";

const ClippedBox = styled(Flex)`
  overflow: hidden;
`;

interface UserAvatar extends SpaceProps {
    avatar: AvatarType;
    width?: string;
    height?: string;
    mx?: string;
}

export const UserAvatar = ({avatar, width = "60px", height = "48px", mx = "8px"}: UserAvatar): JSX.Element => (
    <ClippedBox mx={mx} width={width} alignItems="center" height={height}>
        <Avatar avatarStyle="Circle" {...avatar} />
    </ClippedBox>
);

// TODO(Jonas): Delete this? Seems like something I made, based on the name.
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
