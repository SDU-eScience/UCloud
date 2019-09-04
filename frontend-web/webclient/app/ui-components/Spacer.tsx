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
import Box from "./Box";
import Flex from "./Flex";

interface SpacerProps extends
    SpaceProps,
    WidthProps,
    AlignItemsProps,
    JustifyContentProps,
    FlexWrapProps,
    FlexDirectionProps,
    HeightProps {
    left: React.ReactChild | null;
    right: React.ReactChild | null;
}

export const Spacer = ({left, right, ...props}: SpacerProps) => (
    <Flex {...props}>
        {left}
        <Box ml="auto" />
        {right}
    </Flex>
);
