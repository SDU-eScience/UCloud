import * as React from "react";
import Box from "./Box";
import Flex from "./Flex";

export default function ContainerForText({children, left}: {children?: React.ReactNode, left?: boolean}) {
    return <Flex alignItems={left ? "left" : "center"} flexDirection={"column"} mx={["0px","0px","0px","0px","50px"]}>
        {children}
    </Flex>;
}