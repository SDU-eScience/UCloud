import * as React from "react";
import styled from "styled-components";
import Box from "ui-components/Box";
import Card from "ui-components/Card";
import Flex from "ui-components/Flex";
import Icon from "ui-components/Icon";
import Text from "ui-components/Text";

interface ErrorProps {clearError?: () => void; error?: string; width?: string | number}
function Error(props: ErrorProps): JSX.Element | null {
    if (!props.error) return null;

    function onClearError(e: React.MouseEvent<HTMLElement, MouseEvent>): void {
        props.clearError!();
        e.stopPropagation();
    }

    return (
        <Card
            borderRadius="0.5em"
            height="auto"
            p="1em 1em 1em 1em"
            color="black"
            bg="lightRed"
            borderColor="red"
            width={props.width}
        >
            <Flex alignItems="center">
                <div><WhiteSpacedText fontSize={1} color="red">{props.error}</WhiteSpacedText></div>
                {!props.clearError ? null : (
                    <Box ml="auto">
                        <Icon
                            size="1em"
                            name="close"
                            color="black"
                            onClick={onClearError}
                        />
                    </Box>
                )}
            </Flex>
        </Card>
    );
}

const WhiteSpacedText = styled(Text)`
    white-space: pre;
`;

export default Error;
