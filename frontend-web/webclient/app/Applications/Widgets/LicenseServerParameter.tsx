import * as Types from "Applications";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as React from "react";
import {Box, Select} from "ui-components";
import {LicenseServerId} from "Applications";
import {Client} from "Authentication/HttpClientInstance";

interface LicenseServerParameterProps extends ParameterProps {
    parameter: Types.LicenseServerParameter;
    parameterRef: React.RefObject<HTMLSelectElement>;
    initialSubmit: boolean;
}

export const LicenseServerParameter = (props: LicenseServerParameterProps) => {

    const [availableLicenseServers, setAvailableLicenseServers] = React.useState<LicenseServerId[]>([]);

    async function fetchAvailableLicenseServers(tags: string[]): Promise<LicenseServerId[]> {
        const {response} = await Client.post<LicenseServerId[]>(`/app/license/list`, {tags: tags});
        return response;
    }

    async function loadAndSetAvailableLicenseServers() {
        setAvailableLicenseServers(await fetchAvailableLicenseServers(props.parameter.tagged));
    }

    React.useEffect(() => {
        loadAndSetAvailableLicenseServers();
    }, []);

    return (
        <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <Select
                id="select"
                selectRef={props.parameterRef}
                key={props.parameter.name}
                required={!props.parameter.optional}
                defaultValue="none"
            >
                <option value="none" disabled>
                    No license server selected
                </option>
                { availableLicenseServers.map( server => (
                    <option key={server.id} value={server.id}>
                        {server.name}
                    </option>
                ))}
            </Select>
        </BaseParameter>
    )
};
