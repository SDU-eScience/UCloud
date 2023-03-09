export function Header({children}: {children: React.ReactNode}): JSX.Element {
    return <div className="header">
        <span className="vertically-centered ucloud-title white-text"><b>UCloud</b></span>
        <div className="flex auto-margin">
            {children}
        </div>
    </div>
}