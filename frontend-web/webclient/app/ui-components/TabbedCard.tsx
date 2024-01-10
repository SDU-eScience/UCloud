import * as React from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import {injectStyle} from "@/Unstyled";
import {CSSProperties, useCallback, useLayoutEffect, useRef, useState} from "react";
import Card from "@/ui-components/Card";
import {Absolute} from "@/ui-components/index";

interface Tab {
    icon: IconName;
    name: string;
}

const ContainerClass = injectStyle("tabbed-card", k => `
    ${k}[data-hidden=true] {
        display: none;
    }
    
    ${k} nav {
        margin-top: -20px;
        margin-left: -20px;
        width: calc(100% + 40px);
        display: flex;
        flex-direction: row;
        gap: 4px;
        margin-bottom: 8px;
        overflow-x: auto;
        max-width: calc(100% + 40px);
    }
    
    ${k} nav > div {
        padding: 12px;
        user-select: none;
        font-weight: bold;
        flex-shrink: 0;
    }
    
    ${k} nav > div:first-child {
        padding: 12px 20px;
    }
    
    ${k} nav > div:hover {
        cursor: pointer;
        border-bottom: 2px solid var(--midGray);
    }
    
    ${k} nav > div[data-active=true] {
        border-bottom: 2px solid var(--primary);
    }
`);

const TabClass = injectStyle("tabbed-card-tab", k => `
    ${k} {
    }
`);

const TabbedCard: React.FunctionComponent<{ style?: CSSProperties; children: React.ReactNode }> = ({style, children}) => {
    const [tabs, setTabs] = useState<Tab[]>([]);
    const [visible, setVisible] = useState(0);
    const rootDiv = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
        const div = rootDiv.current;
        if (!div) return;
        const tabs = div.querySelectorAll<HTMLElement>(`.${TabClass}`);

        const newTabs: Tab[] = [];
        tabs.forEach((tab, idx) => {
            const tabName = tab.getAttribute("data-tab-name");
            const tabIcon = tab.getAttribute("data-tab-icon");
            if (!tabName || !tabIcon) return;

            if (idx !== visible) {
                tab.style.display = "none";
            } else {
                tab.style.display = "block";
            }

            newTabs.push({ icon: tabIcon as IconName, name: tabName });
        });

        setTabs(newTabs);
    }, [children, visible]);

    useLayoutEffect(() => {
        const nav = rootDiv.current?.querySelector("nav");
        if (!nav) return;
    }, [tabs]);

    const onTabCLick = useCallback((ev: React.SyntheticEvent) => {
        function findAttr(element: HTMLElement | null | undefined, attr: string): string | null {
            if (!element) return null;
            const value = element.getAttribute(attr);
            if (value) return value;
            return findAttr(element.parentElement, attr);
        }

        const target = ev.target as HTMLElement;
        const tabIdx = parseInt(findAttr(target, "data-tab-idx") ?? "invalid");
        if (isNaN(tabIdx)) return;
        setVisible(tabIdx);
    }, []);

    return <Card style={style}>
        <div ref={rootDiv} className={ContainerClass} data-hidden={tabs.length === 0}>
            <nav>
                {tabs.map((it, idx) =>
                    <div
                        onClick={onTabCLick}
                        data-tab-idx={idx}
                        data-active={idx === visible}
                        key={it.name}
                    >
                        <Icon name={it.icon}/> {it.name}
                    </div>
                )}
            </nav>

            {children}
        </div>
    </Card>;
}

export const TabbedCardTab: React.FunctionComponent<Tab & { children: React.ReactNode }> = ({name, icon, children}) => {
    return <div className={TabClass} data-tab-name={name} data-tab-icon={icon}>{children}</div>;
};

export default TabbedCard;