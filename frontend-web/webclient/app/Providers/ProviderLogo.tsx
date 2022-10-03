import * as React from "react";
import {Box, Image} from "@/ui-components";
import HippoLogo from "@/Assets/Providers/hippo.png";
import PuhuriLogo from "@/Assets/Providers/puhuri.png";
import SophiaLogo from "@/Assets/Providers/sophia.png";
import UCloudLogo from "@/Assets/Providers/ucloud.png";
import styled from "styled-components";

export const ProviderLogo: React.FunctionComponent<{ providerId: string; size: number; }> = ({providerId, size}) => {
    let logo: any = null;
    switch (providerId) {
        case "hippo":
            logo = HippoLogo;
            break;
        case "sophia":
            logo = SophiaLogo;
            break;
        case "puhuri":
            logo = PuhuriLogo;
            break;
        case "ucloud":
            logo = UCloudLogo;
            break;
    }

    return <LogoWrapper size={size}>
        {!logo ? (providerId[0] ?? "?").toUpperCase() : <Image src={logo} />}
    </LogoWrapper>
};

const LogoWrapper = styled.div<{size: number}>`
  --wrapper-size: ${p => p.size}px;
  --logo-padding: 5px;
  
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: calc(var(--wrapper-size) - 8px);
  
  padding: var(--logo-padding);
  border-radius: calc(var(--logo-padding) * 1.5);
  background-color: var(--blue);
  width: var(--wrapper-size);
  height: var(--wrapper-size);
  
  img {
    width: calc(var(--wrapper-size) - var(--logo-padding));
    max-height: calc(var(--wrapper-size) - var(--logo-padding));
  }
`;
