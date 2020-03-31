import * as React from "react";
import styled from "styled-components";
import Box from "ui-components/Box";
import Card from "ui-components/Card";
import Flex from "ui-components/Flex";
import Icon from "ui-components/Icon";
import Text from "ui-components/Text";

interface WarningProps {clearWarning?: () => void; warning?: string; width?: string | number}
function Warning(props: WarningProps): JSX.Element | null {
    if (!props.warning) return null;

    function onClearWarning(e: React.MouseEvent<HTMLElement, MouseEvent>): void {
        props.clearWarning!();
        e.stopPropagation();
    }

    return (
        <Card
            borderRadius="0.5em"
            height="auto"
            p="1em 1em 1em 1em"
            color="black"
            bg="lightYellow"
            borderColor="yellow"
            width={props.width}
        >
            <Flex alignItems="center">
                <div><WhiteSpacedText fontSize={1} color="black">{props.warning}</WhiteSpacedText></div>
                {!props.clearWarning ? null : (
                    <Box ml="auto">
                        <Icon
                            size="1em"
                            name="close"
                            color="black"
                            onClick={onClearWarning}
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

export default Warning;

