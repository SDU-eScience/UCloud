import * as React from "react";
import Box from "./Box";
import Flex from "./Flex";

export default function ContainerForText({children}: {children?: React.ReactNode}) {
    return <Flex alignItems={"center"} flexDirection={"column"}>
        <Box width={0.7} maxWidth={1024}>
            {children}
        </Box>
    </Flex>;
}