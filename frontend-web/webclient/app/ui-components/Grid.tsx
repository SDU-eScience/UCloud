import * as React from "react";
import styled from "styled-components";
import {
    alignItems,
    AlignItemsProps,
    color,
    ColorProps,
    gridAutoFlow,
    GridAutoFlowProps,
    gridGap,
    GridGapProps,
    gridTemplateColumns,
    GridTemplateColumnsProps,
    gridTemplateRows,
    GridTemplateRowsProps,
    height,
    HeightProps,
    justifyItems,
    JustifyItemsProps,
    maxHeight,
    MaxHeightProps,
    maxWidth,
    MaxWidthProps, overflow,
    OverflowProps,
    space,
    SpaceProps,
    width,
    WidthProps
} from "styled-system";

export type GridProps =
    SpaceProps &
    WidthProps &
    HeightProps &
    MaxHeightProps &
    MaxWidthProps &
    ColorProps &
    AlignItemsProps &
    JustifyItemsProps &
    GridGapProps &
    GridTemplateColumnsProps &
    GridAutoFlowProps &
    GridTemplateRowsProps;

const Grid = styled.div<GridProps>`
    display: grid;
    ${gridAutoFlow}
    ${space} ${width} ${height} ${color}
    ${alignItems} ${justifyItems} ${gridGap}
    ${gridTemplateColumns} ${gridTemplateRows}
    ${maxHeight} ${maxWidth}
`;

export const GridCardGroup = ({
    minmax = 400,
    gridGap = 10,
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
