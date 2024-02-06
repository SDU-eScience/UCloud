import * as React from "react";
import {
    GridAutoFlowProps,
    GridGapProps,
    GridTemplateColumnsProps,
    GridTemplateRowsProps,
} from "styled-system";
import {extractSize, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {BoxProps} from "./Types";

export type GridProps =
    BoxProps &
    GridGapProps &
    {gap?: string} &
    GridTemplateColumnsProps &
    GridAutoFlowProps &
    GridTemplateRowsProps &
    {
        children?: React.ReactNode;
        style?: CSSProperties;
        className?: string;
    };

const GridClass = injectStyle("grid", k => `
    ${k} {
        display: grid;
    }
`);

const Grid: React.FunctionComponent<GridProps> = props => {
    const style: CSSProperties = {...unbox(props), ...(props.style ?? {})};
    if (props.gridGap) style.gap = extractSize(props.gridGap);
    if (props.gap) style.gap = extractSize(props.gap);
    if (props.gridTemplateColumns) style.gridTemplateColumns = props.gridTemplateColumns.toString();
    if (props.gridAutoFlow) style.gridAutoFlow = props.gridAutoFlow.toString();
    if (props.gridTemplateRows) style.gridTemplateRows = props.gridTemplateRows.toString();
    return <div className={GridClass + " " + (props.className ?? "")} style={style}>{props.children}</div>;
};

export const GridCardGroup = ({
    minmax = 400,
    gridGap = 15,
    ...props
}): JSX.Element => (
    <Grid
        mt="2px"
        width="100%"
        gridTemplateColumns={`repeat(auto-fill, minmax(${minmax}px, 1fr) )`}
        gridGap={gridGap}
        {...props}
    />
);

Grid.displayName = "Grid";

export default Grid;
