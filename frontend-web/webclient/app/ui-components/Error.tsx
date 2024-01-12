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
            borderColor="errorMain"
        >
            <Flex alignItems="center">
                <div style={{whiteSpace: "pre", color: "var(--errorMain)"}}>{props.error}</div>
                {!props.clearError ? null : (
                    <Box ml="auto">
                        <Icon
                            size="1em"
                            name="close"
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
    borderColor: string;
}

export const ErrorWrapper: React.FunctionComponent<React.PropsWithChildren<ErrorWrapperProps>> = props => (
    <Card
        borderRadius="6px"
        height="auto"
        p="1em 1em 1em 1em"
        my={"1em"}
        color="textPrimary"
        borderColor={props.borderColor}
        width={props.width}
    >
        {props.children}
    </Card>
);

export default Error;
