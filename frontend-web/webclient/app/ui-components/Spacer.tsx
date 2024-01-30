import * as React from "react";
import Box from "./Box";
import Flex from "./Flex";
import {BoxProps} from "./Types";

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
