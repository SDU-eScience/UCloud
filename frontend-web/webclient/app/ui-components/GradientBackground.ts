import {injectStyleSimple} from "@/Unstyled";
import bgImage from "@/Assets/Images/background_polygons.png";

const Gradient = injectStyleSimple("gradient", `
    background: linear-gradient(var(--gradientStart), var(--gradientEnd));
    background-size: 100%;
	background-repeat: no-repeat;
    min-height: 100vh;
`);

export const GradientWithPolygons = injectStyleSimple("polygon-background", `
    background-image: url(${bgImage}), linear-gradient(var(--gradientStart), var(--gradientEnd));
    background-position: 0% 35%;
    background-repeat: repeat;
    padding-bottom: 75px;
    min-height: calc(100vh + 12px);
`);
