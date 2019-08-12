import * as React from "react";
import Flex from "./Flex";
import Box from "./Box";
import { SpaceProps, WidthProps, AlignItemsProps, JustifyContentProps, FlexWrapProps, FlexDirectionProps, HeightProps } from "styled-system";

interface SpacerProps extends SpaceProps, WidthProps, AlignItemsProps, JustifyContentProps, FlexWrapProps, FlexDirectionProps, HeightProps {
    left: React.ReactChild | null
    right: React.ReactChild | null
}

export const Spacer = ({ left, right, ...props }: SpacerProps) => (
    <Flex {...props}>
        {left}
        <Box ml="auto" />
        {right}
    </Flex>
);