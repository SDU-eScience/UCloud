import * as React from "react";
import Card from "./Card";
import * as Heading from "./Heading";
import Error from "./Error";
import Icon, {IconName} from "./Icon";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {classConcat, injectStyle} from "@/Unstyled";

const style = injectStyle("highlightedCard", k => `
    ${k} .title-row {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 20px;
        color: var(--textPrimary);
    }
    
    ${k} .title-row a {
        color: var(--textPrimary);
    }
    
    ${k} .title-row a:hover {
        color: var(--primaryMain);
    }
    
    ${k} {
        background-color: var(--backgroundCard);
    }
    
    ${k} .subtitle {
        color: var(--textSecondary);
        flex-grow: 1;
        display: flex;
        justify-content: end;
    }
`);

export const TitledCard: React.FunctionComponent<React.PropsWithChildren<{
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
    overflow?: string;
}>> = ({
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
                        <Icon name={icon} size="20" color={"textPrimary"} />
                    ) : null}
                    {subtitle ? <div className={"subtitle"}>{subtitle}</div> : null}
                </div> : null
            }

            <Error error={error} />
            {!isLoading ? children : <Spinner />}
        </Card>
    );

export default TitledCard;
