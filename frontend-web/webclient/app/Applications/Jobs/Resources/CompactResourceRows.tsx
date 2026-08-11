import * as React from "react";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";
import {FieldGroup, findElement, Widget} from "@/Applications/Jobs/Widgets";
import {doNothing} from "@/UtilityFunctions";

export function CompactResourceRows({
    singularLabel,
    ...props
}: React.ComponentProps<typeof CompactResourceRowsContent> & {singularLabel: string}) {
    return <FieldGroup>
        <CompactResourceRowsContent singularLabel={singularLabel} {...props} />
    </FieldGroup>;
}

export function CompactResourceRowsContent({
    application,
    params,
    errors,
    setErrors,
    onAdd,
    onRemove,
    provider,
    bindLinkToPort,
    setWarning,
    singularLabel,
    firstRowDescription,
}: {
    application: Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    setErrors: React.Dispatch<React.SetStateAction<Record<string, string>>>;
    onAdd: () => void;
    onRemove: (id: string) => void;
    provider?: string;
    bindLinkToPort?: boolean;
    setWarning?: (warning: string) => void;
    singularLabel: string;
    firstRowDescription?: string;
}) {
    React.useEffect(() => {
        if (params.length === 0) onAdd();
    }, [params.length, onAdd]);

    return <>
        {params.map((entry, index) => {
            const isPlaceholder = index === params.length - 1;
            const selectNextPlaceholder = () => {
                const group = findElement(entry)?.closest<HTMLElement>("[data-field-group]");
                onAdd();
                window.setTimeout(() => {
                    const rows = group?.querySelectorAll<HTMLElement>(`[data-field-row][data-param-type="${entry.type}"]`);
                    const placeholder = rows && rows[rows.length - 1];
                    placeholder?.querySelector<HTMLElement>(
                        "input:not([type='hidden']), select, textarea, [role='switch'], [data-field-activator]"
                    )?.focus();
                }, 0);
            };
            return <Widget
                key={entry.name}
                compact
                selected={!isPlaceholder}
                displayTitle={`${singularLabel} #${index + 1}`}
                parameter={index === 0 && firstRowDescription ? {...entry, description: firstRowDescription} : entry}
                errors={errors}
                setErrors={setErrors}
                setWarning={setWarning}
                provider={provider}
                bindLinkToPort={bindLinkToPort}
                application={application}
                injectWorkflowParameters={doNothing}
                onValueChange={isPlaceholder ? selectNextPlaceholder : undefined}
                onRemove={isPlaceholder ? undefined : () => onRemove(entry.name)}
            />;
        })}
    </>;
}
