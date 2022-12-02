import * as React from "react";
import {Image} from "@/ui-components";
import styled from "styled-components";
import ProviderInfo from "@/Assets/provider_info.json";

export const ProviderLogo: React.FunctionComponent<{providerId: string; size: number; className?: string; }> = ({providerId, size, className}) => {
    const myInfo = ProviderInfo.providers.find(p => p.id === providerId);
    return <LogoWrapper size={size} className={className}>
        {!myInfo ? (providerId[0] ?? "?").toUpperCase() : <Image src={`/Images/${myInfo.logo}`} />}
    </LogoWrapper>
};

const LogoWrapper = styled.div<{size: number}>`
  --wrapper-size: ${p => p.size}px;
  --border-radius: 8px;
  --logo-padding: calc(var(--wrapper-size) / 10);
  
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: calc(var(--wrapper-size) - 8px);
  
  border-radius: var(--border-radius);
  background-color: var(--blue);
  width: var(--wrapper-size);
  height: var(--wrapper-size);
  
  img {
    padding: var(--logo-padding);
    max-width: calc(var(--wrapper-size) - var(--logo-padding));
    max-height: calc(var(--wrapper-size) - var(--logo-padding));
  }
`;
