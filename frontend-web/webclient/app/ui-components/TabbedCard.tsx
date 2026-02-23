import * as React from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import {injectStyle, makeClassName} from "@/Unstyled";
import {CSSProperties, useCallback, useLayoutEffect, useRef, useState} from "react";
import Card from "@/ui-components/Card";

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
        max-width: calc(100% + 40px);
        display: flex;
        flex-direction: row;
        justify-content: space-between;
        margin-bottom: 8px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} [data-tab-list] {
        display: flex;
        flex-direction: row;
        gap: 4px;
        overflow-x: auto;
        min-width: 0;
    }

    ${k} [data-tab-controls] {
        display: flex;
        align-items: center;
        flex-shrink: 0;
    }
    
    ${k} [data-tab-list] > div {
        padding: 12px;
        user-select: none;
        -webkit-user-select: none;
        flex-shrink: 0;
    }

    ${k} [data-tab-list] > div:first-child {
        padding: 12px 20px;
    }

    ${k} [data-tab-list] > div:not(:only-child):hover {
        cursor: pointer;
        border-bottom: 2px solid var(--borderColor);
    }

    ${k} [data-tab-list] > div:not(:only-child)[data-active=true] {
        border-bottom: 2px solid var(--secondaryDark);
    }
    
`);

const TabClass = makeClassName("tabbed-card-tab");

const TabbedCard: React.FunctionComponent<{
    style?: CSSProperties;
    children: React.ReactNode;
    rightControls?: React.ReactNode;
    rightControlsPaddingRight?: string;
}> = ({style, children, rightControls, rightControlsPaddingRight}) => {
    const [tabs, setTabs] = useState<Tab[]>([]);
    const [visible, setVisible] = useState(0);
    const rootDiv = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
        const div = rootDiv.current;
        if (!div) return;
        const tabs = div.querySelectorAll<HTMLElement>(TabClass.dot);

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

            newTabs.push({icon: tabIcon as IconName, name: tabName});
        });

        setTabs(newTabs);
    }, [children, visible]);

    const onTabClick = useCallback((ev: React.SyntheticEvent) => {
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

    return <Card style={style} className={tabs.length === 0 ? HideClass : undefined}>
        <div ref={rootDiv} className={ContainerClass} data-hidden={tabs.length === 0}>
            <nav>
                <div data-tab-list>
                    {tabs.map((it, idx) =>
                        <div
                            onClick={onTabClick}
                            data-tab-idx={idx}
                            data-active={idx === visible}
                            key={it.name}
                        >
                            <Icon name={it.icon} /> {it.name}
                        </div>
                    )}
                </div>
                {!rightControls ? null : (
                    <div data-tab-controls style={{paddingRight: rightControlsPaddingRight ?? "12px"}}>
                        {rightControls}
                    </div>
                )}
            </nav>

            {children}
        </div>
    </Card>;
}

const HideClass = injectStyle("hide", k => `
    ${k} {
        display: none;
    }
`);

export const TabbedCardTab: React.FunctionComponent<Tab & {children: React.ReactNode}> = ({name, icon, children}) => {
    return <div className={TabClass.class} data-tab-name={name} data-tab-icon={icon}>{children}</div>;
};

export default TabbedCard;
