import {injectStyle} from "@/Unstyled";
import bgImage from "@/Assets/Images/background_polygons.png";
import bgImageInverted from "@/Assets/Images/background_polygons_inv.png";

export const GradientWithPolygons = injectStyle("polygon-background", k => `
    ${k} {
        background-image: url(${bgImageInverted});
        background-repeat: repeat;
        background-size: 816px 1028px;
        color: var(--textPrimary);
        min-height: 100vh;
        --defaultShadow: rgba(0, 0, 0, 0.16) 0px 3px 6px, rgba(0, 0, 0, 0.10) 0px -3px 12px, rgba(0, 0, 0, 0.23) 0px 3px 6px;
        --defaultCardBorder: 0;
    }
    
    html.light ${k}:not(.dark) {
        background-image: url(${bgImage});
    }
    
    html ${k} #search-icon,
    html ${k} #refresh-icon {
        color: var(--textPrimary) !important;
    }
    
    html ${k} {
        --backgroundCard: var(--backgroundDefault);
    }
`);

export const Gradient = injectStyle("just-the-gradient", k => `
    ${k} {
        background-image: linear-gradient(var(--gradientStart), var(--gradientEnd));
        background-position: 0% 35%;
        background-repeat: repeat;
        color: var(--textPrimary);
        min-height: 100vh;
        --defaultShadow: rgba(0, 0, 0, 0.16) 0px 3px 6px, rgba(0, 0, 0, 0.10) 0px -3px 12px, rgba(0, 0, 0, 0.23) 0px 3px 6px;
        --defaultCardBorder: 0;
    }
`);
