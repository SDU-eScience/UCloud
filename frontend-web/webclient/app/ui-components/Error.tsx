import * as React from "react";
import Flex from "ui-components/Flex"
import Box from "ui-components/Box"
import Text from "ui-components/Text"
import Icon from "ui-components/Icon"
import Card from "ui-components/Card";

interface ErrorProps { clearError?: () => void, error?: string, width?: string | number }
const Error = (props: ErrorProps) => !!props.error ?
    <Card borderRadius="0.5em" height="auto" p="1em 1em 1em 1em" color="black" bg="lightRed" borderColor="red" width={props.width}>
        <Flex alignItems="center">
            <Box><Text fontSize={1} color="red">{props.error}</Text></Box>
            {props.clearError ? <Box ml="auto"><Icon size="1em" name="close" color="black" onClick={e => (props.clearError!(), e.stopPropagation())} /></Box> : null}
        </Flex>
    </Card> : null;

export default Error;