import * as React from "react";
import Box from "./Box";
import Flex from "./Flex";
import Card from "./Card";
import * as Heading from "./Heading";
import Error from "./Error";
import Icon, {IconName} from "./Icon";
import theme, {ThemeColor} from "./theme";
import Spinner from "@/LoadingIcon/LoadingIcon";

export const HighlightedCard: React.FunctionComponent<{
    title?: React.ReactNode;
    subtitle?: React.ReactNode;
    error?: string;
    color: ThemeColor;
    isLoading?: boolean;
    icon?: IconName;
    height?: string;
    minHeight?: string;
    width?: string;
    minWidth?: string;
    onClick?: () => void;
    onContextMenu?: (e: React.MouseEvent) => void;
    className?: string;
}> = ({
    title,
    subtitle,
    onClick,
    color,
    error,
    isLoading = false,
    icon = undefined,
    children,
    height = "auto",
    minHeight,
    width = "100%",
    minWidth,
    onContextMenu,
    className
}) => (
        <Card
            onClick={onClick}
            overflow="hidden"
            height={height}
            width={width}
            minWidth={minWidth}
            boxShadow="sm"
            borderWidth={0}
            borderRadius={6}
            minHeight={minHeight}
            onContextMenu={onContextMenu}
            className={className}
        >
            <Box style={{borderTop: `5px solid var(--${color}, #f00)`}} />
            <Box px={3} py={1} height={"calc(100% - 5px)"}>
                <Flex alignItems="center">
                    {icon !== undefined ? (
                        <Icon
                            name={icon}
                            m={8}
                            ml={0}
                            size="20"
                            color={theme.colors.darkGray}
                        />
                    ) : null}
                    {typeof title === "string" ? <Heading.h3>{title}</Heading.h3> : title ? title : null}
                    <Box flexGrow={1} />
                    {subtitle ? <Box color={theme.colors.gray}>{subtitle}</Box> : null}
                </Flex>
                <Error error={error} />
                {!isLoading ? children : <Spinner />}
            </Box>
        </Card>
    );

export default HighlightedCard;
