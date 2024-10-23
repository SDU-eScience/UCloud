import * as React from "react";
import { useEffect, useRef, useState, ReactNode } from "react";
import {injectStyle} from "@/Unstyled";

interface ScrollableBoxProps {
    children: ReactNode;
    maxHeight: string;
}

const ScrollableBoxClass = injectStyle("scrollable-box", k => `
    ${k} {
        position: relative;
        overflow-y: auto;
    }

    ${k} .shadow {
        position: sticky;
        left: 0;
        right: 0;
        height: 5px;
        pointer-events: none;
        z-index: 1;
    }

    ${k} .top-shadow {
        top: 0;
        box-shadow: 0px -5px 10px rgba(0, 0, 0, 1);
        background: var(--backgroundDefault);
    }

    ${k} .bottom-shadow {
        bottom: 0;
        box-shadow: 0px 5px 10px rgba(0, 0, 0, 1);
        background: var(--backgroundDefault);
    }
`);

const ScrollableBox: React.FunctionComponent<ScrollableBoxProps> = ({ children, maxHeight }) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const [showTopShadow, setShowTopShadow] = useState(false);
    const [showBottomShadow, setShowBottomShadow] = useState(false);

    const handleScroll = () => {
        const container = containerRef.current;
        if (!container) return;

        const { scrollTop, scrollHeight, clientHeight } = container;
        if (scrollHeight <= clientHeight) {
            setShowTopShadow(false);
            setShowBottomShadow(false);
        } else {
            setShowTopShadow(scrollTop > 0);
            setShowBottomShadow(scrollTop + clientHeight + 20 <= scrollHeight);
        }
    };

    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        handleScroll();

        container.addEventListener("scroll", handleScroll);
        return () => {
            container.removeEventListener("scroll", handleScroll);
        };
    }, [children]);

    return (
        <div
            ref={containerRef}
            className={ScrollableBoxClass}
            style={{ maxHeight, overflowY: "auto" }}
        >
            {showTopShadow && <div className="shadow top-shadow" />}
            {children}
            {showBottomShadow && <div className="shadow bottom-shadow" />}
        </div>
    );
};

export default ScrollableBox;
