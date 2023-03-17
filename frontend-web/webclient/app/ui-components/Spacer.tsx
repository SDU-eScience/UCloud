import * as React from "react";
import {
    AlignItemsProps,
    FlexDirectionProps,
    FlexWrapProps,
    HeightProps,
    JustifyContentProps,
    SpaceProps,
    WidthProps
} from "styled-system";
import Box, {BoxProps} from "./Box";
import Flex from "./Flex";

interface SpacerProps extends BoxProps {
    left: React.ReactNode;
    right: React.ReactNode;
}

export const Spacer = ({left, right, ...props}: SpacerProps) => (
    <Flex {...props}>
        {left}
        <Box ml="auto" />
        {right}
    </Flex>
);
