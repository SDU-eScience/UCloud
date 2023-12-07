import * as React from "react";
import Card from "./Card";
import * as Heading from "./Heading";
import Error from "./Error";
import Icon, {IconName} from "./Icon";
import theme from "./theme";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {classConcat, injectStyle} from "@/Unstyled";

const style = injectStyle("highlightedCard", k => `
    ${k} .title-row {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 25px;
        margin-top: 10px;
        color: var(--black);
    }
    
    ${k} .title-row a {
        color: var(--black);
    }
    
    ${k} .title-row a:hover {
        color: var(--primary);
    }
    
    ${k} {
        background-color: var(--white);
    }
    
    ${k} .subtitle {
        color: var(--gray);
        flex-grow: 1;
        display: flex;
        justify-content: end;
    }
`);

export const HighlightedCard: React.FunctionComponent<{
    title?: React.ReactNode;
    subtitle?: React.ReactNode;
    error?: string;
    isLoading?: boolean;
    icon?: IconName;
    height?: string;
    minHeight?: string;
    maxHeight?: string;
    width?: string;
    minWidth?: string;
    maxWidth?: string;
    onClick?: () => void;
    onContextMenu?: (e: React.MouseEvent) => void;
    className?: string;
    children?: React.ReactNode;
    overflow?: string;
}> = ({
    title,
    subtitle,
    error,
    isLoading = false,
    icon = undefined,
    height = "auto",
    minHeight,
    maxHeight,
    width = "100%",
    minWidth,
    maxWidth,
    onClick,
    onContextMenu,
    className,
    children,
    overflow,
}) => (
        <Card
            onClick={onClick}
            overflow={overflow ?? "hidden"}
            height={height}
            width={width}
            minWidth={minWidth}
            minHeight={minHeight}
            maxHeight={maxHeight}
            maxWidth={maxWidth}
            onContextMenu={onContextMenu}
            className={classConcat(style, className)}
        >
            {title || icon || subtitle ?
                <div className="title-row">
                    {typeof title === "string" ? <Heading.h3>{title}</Heading.h3> : title ? title : null}
                    {icon !== undefined ? (
                        <Icon name={icon} size="20" color={theme.colors.darkGray}/>
                    ) : null}
                    {subtitle ? <div className={"subtitle"}>{subtitle}</div> : null}
                </div> : null
            }

            <Error error={error} />
            {!isLoading ? children : <Spinner />}
        </Card>
    );

export default HighlightedCard;
