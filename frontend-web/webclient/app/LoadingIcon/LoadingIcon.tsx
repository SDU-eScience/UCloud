import * as React from "react";
import styled, {css, keyframes} from "styled-components";
import {height, HeightProps, width, WidthProps} from "styled-system";


interface HexSpinProps {
    size?: number;
}

type SpinnerProps = WidthProps & HeightProps;

const hexColors = ["#0057B8", "#82A", "#266D7F", "#F8A527", "#F11E4A"];
const nColors = hexColors.length;
const delay = 0.04;
const pathN = 18;

function createKF() {
    let kf = ``;
    for (let i = 0; i < nColors; i += 1) {
        kf += `${i * 100 / nColors}% { fill: ${hexColors[i]}; }`;
    }
    kf += `100% { fill: ${hexColors[0]}; }`;
    return css`${kf}`;
}

const spinColor = keyframes`
    ${createKF()}
`;

const HexSpinner = styled.div<SpinnerProps>`
    ${width} ${height}
    margin: 20px auto;
    & > svg {
        ${createCSS()};
    }
    animation-name: ${spinColor};
`;


function createCSS() {
    let style = ``;
    for (let i = 1; i <= pathN; i += 1) {
        style += `
            path:nth-child(${i}) {
                animation: ${spinColor.getName()} ${delay * pathN}s linear infinite;
                animation-delay: -${i * delay}s;
            }
        `;
    }
    return css`${style}`;
}

const HexSpin = ({size = 32}: HexSpinProps): JSX.Element => (
    <HexSpinner width={size} height={size} >
        <svg
            xmlns="http://www.w3.org/2000/svg"
            xmlnsXlink="http://www.w3.org/1999/xlink"
            version="1.1"
            id="svg"
            width={size}
            height={size}
            viewBox="0 0 220 220"
        >
            <path d="M80.296,0.12l14.673,54.761l40.088,-40.088l-54.761,-14.673Z" />
            <path d="M149.73,69.554l-14.673,-54.761l-40.088,40.088l54.761,14.673Z" />
            <path d="M189.818,29.466l-54.761,-14.673l14.673,54.761l40.088,-40.088Z" />
            <path d="M204.491,84.228l-14.673,-54.762l-40.088,40.088l54.761,14.674Z" />
            <path d="M164.403,124.316l40.088,-40.088l-54.761,-14.674l14.673,54.762Z" />
            <path d="M219.165,138.989l-14.674,-54.761l-40.088,40.088l54.762,14.673Z" />
            <path d="M179.077,179.077l-14.674,-54.761l54.762,14.673l-40.088,40.088Z" />
            <path d="M124.316,164.403l54.761,14.674l-14.674,-54.761l-40.087,40.087Z" />
            <path d="M138.989,219.165l40.088,-40.088l-54.761,-14.674l14.673,54.762Z" />
            <path d="M84.228,204.491l54.761,14.674l-14.673,-54.762l-40.088,40.088Z" />
            <path d="M69.554,149.73l14.674,54.761l40.088,-40.088l-54.762,-14.673Z" />
            <path d="M29.466,189.818l54.762,14.673l-14.674,-54.761l-40.088,40.088Z" />
            <path d="M14.793,135.057l14.673,54.761l40.088,-40.088l-54.761,-14.673Z" />
            <path d="M54.881,94.969l-40.088,40.088l54.761,14.673l-14.673,-54.761Z" />
            <path d="M0.12,80.296l14.673,54.761l40.088,-40.088l-54.761,-14.673Z" />
            <path d="M40.208,40.208l-40.088,40.088l54.761,14.673l-14.673,-54.761Z" />
            <path d="M94.969,54.881l-54.761,-14.673l14.673,54.761l40.088,-40.088Z" />
            <path d="M80.296,0.12l-40.088,40.088l54.761,14.673l-14.673,-54.761Z" />
        </svg>
    </HexSpinner>
);

export default HexSpin;
