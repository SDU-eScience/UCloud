import * as React from "react";
import { Card, Flex, Box, Text } from "ui-components";
import CloseButton from "./CloseButton";
import { TextSpan } from "./Text";

interface ErrorProps { clearError?: () => void, error?: string }
const Error = (props: ErrorProps) => !!props.error ?
    <Card borderRadius="0.5em" height="auto" p="1em 1em 1em 1em" color="black" bg="lightRed" borderColor="red">
        <Flex>
            <Box><Text fontSize={2} color="red">{props.error}</Text></Box>
            {props.clearError ? <Box ml="auto"><CloseButton color="black" onClick={props.clearError} /></Box> : null}
        </Flex>
    </Card> : null;

export default Error;