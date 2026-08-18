import * as React from "react";
import {Icon} from "@/ui-components";

export const UcxAccordion: React.FunctionComponent<React.PropsWithChildren<{
    title: React.ReactNode;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
}>> = ({title, open = false, onOpenChange, children}) => {
    const [internalOpen, setInternalOpen] = React.useState(open);
    const isControlled = onOpenChange !== undefined;
    const isOpen = isControlled ? open : internalOpen;

    React.useEffect(() => {
        if (!isControlled) setInternalOpen(open);
    }, [isControlled, open]);

    const toggle = () => {
        const next = !isOpen;
        if (isControlled) {
            onOpenChange?.(next);
        } else {
            setInternalOpen(next);
        }
    };

    return <div style={{display: "flex", flexDirection: "column", gap: 4}}>
        <button
            type="button"
            onClick={toggle}
            style={{
                all: "unset",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                cursor: "pointer",
                userSelect: "none",
                padding: "4px 0",
                borderBottom: "1px solid var(--borderColor)",
                marginBottom: "8px",
            }}
        >
            <div style={{fontWeight: 600}}>{title}</div>
            <Icon name="heroChevronDown" size={12} rotation={isOpen ? 0 : -90} />
        </button>
        {isOpen ? <div>{children}</div> : null}
    </div>;
};
