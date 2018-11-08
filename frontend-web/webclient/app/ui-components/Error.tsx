import * as React from "react";
import { Card, Flex, Box, Text } from "ui-components";
import CloseButton from "./CloseButton";

interface ErrorProps { clearError?: () => void, error?: string }
const Error = (props: ErrorProps) => !!props.error ?
    <Card borderRadius="0.5em" height="4em" color="black" bg="lightRed" borderColor="red">
        <Flex ml="1em" mt="1em">
            <Text fontSize={2} color="red">{props.error}</Text>
            {props.clearError ? <Box ml="auto"><CloseButton ml="-40px" onClick={props.clearError} /></Box> : null}
        </Flex>
    </Card> : null;

export default Error;