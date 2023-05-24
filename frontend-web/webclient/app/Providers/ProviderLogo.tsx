import * as React from "react";
import {Image} from "@/ui-components";
import ProviderInfo from "@/Assets/provider_info.json";
import {classConcat} from "@/Unstyled";
import {injectStyle} from "@/Unstyled";

export const ProviderLogo: React.FunctionComponent<{providerId: string; size: number; className?: string;}> = ({providerId, size, className}) => {
    const myInfo = ProviderInfo.providers.find(p => p.id === providerId);
    const style: React.CSSProperties = {};
    style["--wrapper-size"] = size + "px";
    return <div className={classConcat(LogoWrapper, className)} style={style}>
        {!myInfo ? (providerId[0] ?? "?").toUpperCase() : <Image src={`/Images/${myInfo.logo}`} />}
    </div>
};



const LogoWrapper = injectStyle("logo-wrapper", k => `
    ${k} {
        --border-radius: 8px;
        
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-size: calc(var(--wrapper-size) - 8px);
        
        border-radius: var(--border-radius);
        background-color: var(--blue);
        width: var(--wrapper-size);
        height: var(--wrapper-size);
        min-width: var(--wrapper-size);
        min-height: var(--wrapper-size);
    }
  
    ${k} > img {
        --logo-padding: calc(var(--wrapper-size) / 10);
        padding: var(--logo-padding);
        max-width: calc(var(--wrapper-size) - var(--logo-padding));
        max-height: calc(var(--wrapper-size) - var(--logo-padding));
    }
`);
