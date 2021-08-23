import * as React from "react";
import Box from "ui-components/Box";
import Flex from "ui-components/Flex";
import Card from "ui-components/Card";
import * as Heading from "ui-components/Heading";
import Icon, {IconName} from "./Icon";
import theme, {ThemeColor} from "./theme";
import Spinner from "LoadingIcon/LoadingIcon";

const HighlightedCard: React.FunctionComponent<{
    title?: React.ReactNode;
    subtitle?: React.ReactNode;
    color: ThemeColor;
    isLoading?: boolean;
    icon?: IconName,
    height?: string,
    minHeight?: string,
    width?: string,
    minWidth?: string,
    onClick?: () => void;
}> = ({
    title,
    subtitle,
    onClick,
    color,
    isLoading = false,
    icon = undefined,
    children,
    height = "auto",
    minHeight,
    width = "100%",
    minWidth
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
                {!isLoading ? children : <Spinner />}
            </Box>
        </Card>
    );

export default HighlightedCard;