import * as React from "react";
import Box from "@/ui-components/Box";
import Card from "@/ui-components/Card";
import Flex from "@/ui-components/Flex";
import Icon from "@/ui-components/Icon";

interface ErrorProps {
    clearError?: () => void;
    error?: React.ReactNode;
    width?: string | number
}

function Error(props: ErrorProps): JSX.Element | null {
    if (!props.error) return null;

    function onClearError(e: React.MouseEvent<HTMLElement, MouseEvent>): void {
        props.clearError!();
        e.stopPropagation();
    }

    return (
        <ErrorWrapper
            bg="lightRed"
            borderColor="red"
        >
            <Flex alignItems="center">
                <div style={{whiteSpace: "pre", color: "var(--red)"}}>{props.error}</div>
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
        </ErrorWrapper>
    );
}

interface ErrorWrapperProps {
    width?: string | number;
    bg: string;
    borderColor: string;
}

export const ErrorWrapper: React.FunctionComponent<React.PropsWithChildren<ErrorWrapperProps>> = props => (
    <Card
        borderRadius="6px"
        height="auto"
        p="1em 1em 1em 1em"
        color="black"
        bg={props.bg}
        borderColor={props.borderColor}
        width={props.width}
    >
        {props.children}
    </Card>
);

export default Error;
