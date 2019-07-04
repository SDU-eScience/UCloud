import * as React from "react";
import Flex from "./Flex";
import Box from "./Box";

export default function ContainerForText({children}: {children?: React.ReactNode}) {
    return <Flex alignItems={"center"} flexDirection={"column"}>
        <Box width={0.7} maxWidth={1024}>
            {children}
        </Box>
    </Flex>;
}