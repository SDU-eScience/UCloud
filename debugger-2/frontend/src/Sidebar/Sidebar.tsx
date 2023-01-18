import * as React from "react";

export function Sidebar({children}: {children: React.ReactNode}): JSX.Element {
    return <div className="sidebar">
        {children}
    </div>
}