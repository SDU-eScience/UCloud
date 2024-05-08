import * as React from "react";
import Flex from "./Flex";

export default function ContainerForText({children, left}: {children?: React.ReactNode; left?: boolean}): React.ReactNode {
    return (
        <Flex alignItems={left ? "left" : "center"} flexDirection={"column"} mx={"50px"}>
            {children}
        </Flex>
    );
}
