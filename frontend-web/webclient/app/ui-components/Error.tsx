import * as React from "react";
import { Card, Flex, Box, Text, Icon } from "ui-components";

interface ErrorProps { clearError?: () => void, error?: string, width?: string | number }
const Error = (props: ErrorProps) => !!props.error ?
    <Card borderRadius="0.5em" height="auto" p="1em 1em 1em 1em" color="black" bg="lightRed" borderColor="red" width={props.width}>
        <Flex>
            <Box><Text fontSize={2} color="red">{props.error}</Text></Box>
            {props.clearError ? <Box ml="auto"><Icon name="close" color="black" onClick={props.clearError} /></Box> : null}
        </Flex>
    </Card> : null;

export default Error;