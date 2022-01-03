import * as React from "react";
import styled from "styled-components";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import Icon from "@/ui-components/Icon";
import Text from "@/ui-components/Text";
import {ErrorWrapper} from "./Error";

interface WarningProps {clearWarning?: () => void; warning?: string; width?: string | number}
const Warning: React.FunctionComponent<WarningProps> = props => {
    if (!props.warning && !props.children) return null;

    function onClearWarning(e: React.MouseEvent<HTMLElement, MouseEvent>): void {
        props.clearWarning!();
        e.stopPropagation();
    }

    return (
        <ErrorWrapper
            bg="lightYellow"
            borderColor="yellow"
            width={props.width}
        >
            <Flex alignItems="center">
                <div><WhiteSpacedText fontSize={1} color="black"><span>{props.warning}</span>{props.children}</WhiteSpacedText></div>
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
        </ErrorWrapper>
    );
};

const WhiteSpacedText = styled(Text)`
    & > span {
        white-space: pre;
    }
`;

export default Warning;

