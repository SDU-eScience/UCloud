import * as React from "react";
import Flex from "./Flex";

export default function ContainerForText({children, left}: {children?: React.ReactNode; left?: boolean}): JSX.Element {
    return (
        <Flex alignItems={left ? "left" : "center"} flexDirection={"column"} mx={["0px", "0px", "0px", "0px", "0px", "50px"]}>
            {children}
        </Flex>
    );
}
