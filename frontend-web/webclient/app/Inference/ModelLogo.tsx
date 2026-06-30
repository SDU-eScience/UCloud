import * as React from "react";

import {Flex} from "@/ui-components";
import LogoDeepseek from "@/Assets/Images/inference/deepseek.png";
import LogoGoogle from "@/Assets/Images/inference/google.png";
import LogoMeta from "@/Assets/Images/inference/meta.png";
import LogoMinimax from "@/Assets/Images/inference/minimax.png";
import LogoMistral from "@/Assets/Images/inference/mistral.png";
import LogoMoonshot from "@/Assets/Images/inference/moonshot.png";
import LogoOpenAI from "@/Assets/Images/inference/oai.png";
import LogoQwen from "@/Assets/Images/inference/qwen.png";
import LogoZai from "@/Assets/Images/inference/zai.png";

export default function ModelInferenceLogo({modelName, size = 24}: {modelName: string; size?: number}): React.ReactNode {
    const norm = modelName.toLowerCase();

    let img = "";
    if (norm.includes("deepseek")) {
        img = LogoDeepseek;
    } else if (norm.includes("llama")) {
        img = LogoMeta;
    } else if (norm.includes("qwen")) {
        img = LogoQwen;
    } else if (norm.includes("minimax")) {
        img = LogoMinimax;
    } else if (norm.includes("glm")) {
        img = LogoZai;
    } else if (norm.includes("mistral")) {
        img = LogoMistral;
    } else if (norm.includes("google") || norm.includes("gemma")) {
        img = LogoGoogle;
    } else if (norm.includes("kimi") || norm.includes("k2.")) {
        img = LogoMoonshot;
    } else if (norm.includes("gpt")) {
        img = LogoOpenAI;
    }

    if (img === "") {
        return <span
            title={modelName}
            aria-hidden="true"
            style={{
                width: size,
                height: size,
                borderRadius: "50%",
                background: "var(--primaryMain)",
                display: "inline-block",
                flexShrink: 0,
            }}
        />;
    }

    return <Flex
        background={"var(--playground-logo-bg, var(--secondaryMain))"}
        border={"1px solid var(--playground-border, var(--borderColor))"}
        borderRadius={size >= 96 ? "28px" : "8px"}
        height={size}
        width={size}
        alignItems={"center"}
        justifyContent={"center"}
    >
        <img src={img} alt={`${modelName} logo`} style={{maxHeight: Math.round(size * 0.7), maxWidth: Math.round(size * 0.7)}} />
    </Flex>;
}
