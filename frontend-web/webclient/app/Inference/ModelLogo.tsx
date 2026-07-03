import * as React from "react";

import {Flex} from "@/ui-components";
import LogoDeepseek from "@/Assets/Images/inference/deepseek.png";
import LogoGoogle from "@/Assets/Images/inference/google.png";
import LogoMeta from "@/Assets/Images/inference/meta.png";
import LogoMinimax from "@/Assets/Images/inference/minimax.png";
import LogoMistral from "@/Assets/Images/inference/mistral.png";
import LogoMoonshot from "@/Assets/Images/inference/moonshot.png";
import LogoMoonshotWhite from "@/Assets/Images/inference/moonshot-white.png";
import LogoOpenAI from "@/Assets/Images/inference/oai.png";
import LogoOpenAIWhite from "@/Assets/Images/inference/oai-white.png";
import LogoQwen from "@/Assets/Images/inference/qwen.png";
import LogoZai from "@/Assets/Images/inference/zai.png";
import LogoNvidia from "@/Assets/Images/inference/nvidia-black.png"
import LogoNvidiaWhite from "@/Assets/Images/inference/nvidia-white.png"
import {useIsLightThemeStored} from "@/ui-components/theme";

export default function ModelInferenceLogo({modelName, size = 24}: {modelName: string; size?: number}): React.ReactNode {
    const isLight = useIsLightThemeStored();
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
        if (!isLight) img = LogoMoonshotWhite;
    } else if (norm.includes("nvidia")) {
        img = LogoNvidia;
        if (!isLight) img= LogoNvidiaWhite;
    } else if (norm.includes("gpt")) {
        img = LogoOpenAI;
        if (!isLight) img = LogoOpenAIWhite;
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
        style={{aspectRatio: "1 / 1"}}
    >
        <img src={img} alt={`${modelName} logo`} style={{maxHeight: Math.round(size * 0.7), maxWidth: Math.round(size * 0.7)}} />
    </Flex>;
}

export function modelProviderName(modelName: string): string {
    const norm = modelName.toLowerCase();
    if (norm.includes("deepseek")) return "DeepSeek";
    if (norm.includes("llama")) return "Meta";
    if (norm.includes("qwen")) return "Qwen";
    if (norm.includes("minimax")) return "Minimax";
    if (norm.includes("glm")) return "Z.ai";
    if (norm.includes("mistral")) return "Mistral";
    if (norm.includes("google") || norm.includes("gemma")) return "Google";
    if (norm.includes("kimi") || norm.includes("k2.")) return "Moonshot AI";
    if (norm.includes("nvidia")) return "NVIDIA";
    if (norm.includes("gpt")) return "OpenAI";
    return "Unknown";
}
