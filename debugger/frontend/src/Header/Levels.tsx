import * as React from "react";
import {prettierString} from "../MainContent/MainContent";
import {MessageImportance} from "../WebSockets/Schema";

const Importances = Object.values(MessageImportance).filter(it => typeof it !== "number") as string[];

export function Levels({level, setLevel}: {level: string; setLevel(level: string): void;}): JSX.Element {
    return <Dropdown trigger={
        <div className="header-dropdown flex">
            <span className="ml-10px vertically-centered">
                Level: {level === "" ? "NONE" : level}
            </span>
        </div>
    }>
        {Importances.map(it =>
            <div key={it} onClick={() => setLevel((it === level ? "" : it))} data-row-active={it === level} className="dropdown-row">
                {prettierString(it)}
            </div>
        )}
    </Dropdown>
}

export function Dropdown({children, trigger}: {children: React.ReactNode; trigger: React.ReactNode;}) {
    const [isOpen, setOpen] = React.useState(false);
    const dropdownRef = React.useRef<HTMLDivElement>(null);

    const onOutsideClick = React.useCallback((event: any) => {
        if (dropdownRef.current && !dropdownRef.current.contains(event.target) && isOpen) {
            setOpen(false);
        }
    }, [isOpen]);

    React.useEffect(() => {
        if (isOpen) {
            document.addEventListener("mousedown", onOutsideClick);
        } else {
            document.removeEventListener("mousedown", onOutsideClick);
        }
        return () => {
            document.removeEventListener("mousedown", onOutsideClick);
        };
    }, [onOutsideClick, isOpen]);

    const close = React.useCallback(() => {
        setOpen(false);
    }, []);

    return <div className={"pointer"} onClick={() => setOpen(o => !o)} ref={dropdownRef}>
        <span>{trigger}</span>
        <div className="card dropdown-content" style={{width: "280px", display: isOpen ? "block" : undefined}} onClick={close}>
            {children}
        </div>
    </div>
}

