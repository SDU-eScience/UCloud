import * as React from "react";
import styled, { keyframes } from 'styled-components'
import { width, height, HeightProps, WidthProps } from "styled-system";



const CubeGrid = ({ size }: {size: number}) => {
    type SpinnerProps = WidthProps & HeightProps;

    const Spinner = styled("div")<SpinnerProps>`
        position: relative;
        margin: 20px auto;
        ${width}
        ${height}
    `
    const grid = keyframes`
        0%,
        70%,
        100% {
        transform: scale3D(1, 1, 1);
        }
        35% {
        transform: scale3D(0, 0, 1);
        }
    `
    const Cube = styled.div`
		width: 33.3333%;
		height: 33.3333%;
		background-color: ${props => props.theme.colors.spinnerColor};
		float: left;
		animation: ${grid} 1.3s infinite ease-in-out;
	`

    const Cube1 = styled(Cube)`
		animation-delay: 0.2s;
	`
    const Cube2 = styled(Cube)`
		animation-delay: 0.3s;
	`
    const Cube3 = styled(Cube)`
		animation-delay: 0.4s;
	`
    const Cube4 = styled(Cube)`
		animation-delay: 0.1s;
	`
    const Cube5 = styled(Cube)`
		animation-delay: 0.2s;
	`
    const Cube6 = styled(Cube)`
		animation-delay: 0.3s;
	`
    const Cube7 = styled(Cube)`
		animation-delay: 0s;
	`
    const Cube8 = styled(Cube)`
		animation-delay: 0.1s;
	`
    const Cube9 = styled(Cube)`
		animation-delay: 0.2s;
	`

    return (
        <Spinner width={size} height={size}>
            <Cube1 />
            <Cube2 />
            <Cube3 />
            <Cube4 />
            <Cube5 />
            <Cube6 />
            <Cube7 />
            <Cube8 />
            <Cube9 />
        </Spinner>
    )
}

export default CubeGrid

