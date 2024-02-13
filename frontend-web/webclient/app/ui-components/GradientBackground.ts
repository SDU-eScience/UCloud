import {injectStyle, injectStyleSimple} from "@/Unstyled";
import bgImage from "@/Assets/Images/background_polygons.png";
import bgImageInverted from "@/Assets/Images/background_polygons_inv.png";

export const GradientWithPolygons = injectStyle("polygon-background", k => `
    ${k} {
        background-image: url(${bgImage});
        background-repeat: repeat;
        background-size: 816px 1028px;
        color: var(--textPrimary);
        min-height: 100vh;
    }
    
    html.dark ${k} {
        background-image: url(${bgImageInverted});
    }
    
    html.dark ${k} #search-icon,
    html.dark ${k} #refresh-icon {
        color: var(--textPrimary) !important;
    }
`);

export const Gradient = injectStyle("just-the-gradient", k => `
    ${k} {
        background-image: linear-gradient(var(--gradientStart), var(--gradientEnd));
        background-position: 0% 35%;
        background-repeat: repeat;
        color: var(--textPrimary);
        min-height: 100vh;
    }
`);
