import * as React from "react";
import styled from "styled-components"
import {
    space, SpaceProps, width, WidthProps,
    height, HeightProps,
    color, ColorProps,
    alignItems, AlignItemsProps,
    justifyItems,
    JustifyItemsProps,
    gridGap, GridGapProps,
    gridTemplateColumns, GridTemplatesColumnsProps,
    gridTemplateRows, GridTemplatesRowsProps,
} from "styled-system";

export type GridProps =
    SpaceProps &
    WidthProps &
    HeightProps &
    ColorProps &
    AlignItemsProps &
    JustifyItemsProps &
    GridGapProps &
    GridTemplatesColumnsProps &
    GridTemplatesRowsProps

const Grid = styled.div<GridProps>`
    display: grid;
    ${space} ${width} ${height} ${color}
    ${alignItems} ${justifyItems} ${gridGap}
    ${gridTemplateColumns} ${gridTemplateRows}
`;

export const GridCardGroup = ({ minmax = 350, ...props }) => (<Grid mt="2px" width={"100%"} gridTemplateColumns={`repeat(auto-fill, minmax(${minmax}px, 1fr) )`} gridGap={10} {...props} />)

Grid.displayName = "Grid"

export default Grid