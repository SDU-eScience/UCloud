import * as React from "react";
import Flex from "./Flex";
import Box from "./Box";

export default class ContainerForText extends React.Component {
    render() {
        return <Flex alignItems={"center"} flexDirection={"column"}>
            <Box width={0.7}>
                {this.props.children}
            </Box>
        </Flex>;
    }
}