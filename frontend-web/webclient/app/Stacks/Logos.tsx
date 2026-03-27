import * as React from "react";
import kubernetesLogo from "@/Assets/Images/k8s-logo.svg";
import Image from "@/ui-components/Image";
import Icon from "@/ui-components/Icon";

export function stackLogoUrl(type: string): string | null {
    switch (type) {
        case "Kubernetes":
            return kubernetesLogo;
        default:
            return null;
    }
}

export const StackLogo: React.FunctionComponent<{
    type: string;
    size: number;
}> = ({type, size}) => {
    const logoUrl = stackLogoUrl(type);
    return logoUrl
        ? <Image src={logoUrl} width={`${size}px`} alt={`${type} logo`} />
        : <Icon name={"heroServerStack"} size={size} />;
}
