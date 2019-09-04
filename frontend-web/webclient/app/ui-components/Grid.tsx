import * as React from "react";
import styled from "styled-components";
import {
    alignItems, AlignItemsProps, color, ColorProps,
    gridGap, GridGapProps, gridTemplateColumns,
    GridTemplateColumnsProps, gridTemplateRows,
    GridTemplateRowsProps, height, HeightProps,
    justifyItems, JustifyItemsProps, space,
    SpaceProps, width, WidthProps
} from "styled-system";

export type GridProps =
    SpaceProps &
    WidthProps &
    HeightProps &
    ColorProps &
    AlignItemsProps &
    JustifyItemsProps &
    GridGapProps &
    GridTemplateColumnsProps &
    GridTemplateRowsProps;

const Grid = styled.div<GridProps>`
    display: grid;
    ${space} ${width} ${height} ${color}
    ${alignItems} ${justifyItems} ${gridGap}
    ${gridTemplateColumns} ${gridTemplateRows}
`;

export const GridCardGroup = ({
    minmax = 400,
    gridGap = 10,
    ...props
}) => (
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
