import * as React from "react";
import { Card, Flex, Box, Text } from "ui-components";
import CloseButton from "./CloseButton";

const Error = (props) => !!props.error ?
    <Card borderRadius="0.5em" height="4em" color="black" bg="lightRed" borderColor="red">
        <Flex ml="1em" mt="1em">
            <Text fontSize={2} color="red">{props.error}</Text>
            <Box ml="auto"><CloseButton ml="-40px" onClick={props.clearError} /></Box>
        </Flex>
    </Card> : null;

export default Error;