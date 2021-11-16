import * as React from "react";
import {InvokeCommand, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {useCallback, useState} from "react";
import {Box, Button, Label, Select} from "@/ui-components";
import {inDevEnvironment, onDevSite, PropType} from "@/UtilityFunctions";
import {Operation} from "@/ui-components/Operation";
import {addStandardDialog} from "@/UtilityComponents";
import {auth, BulkResponse} from "@/UCloud";
import {bulkRequestOf} from "@/DefaultObjects";
import AccessToken = auth.AccessToken;
import ResourceForm from "@/Products/CreateProduct";
import * as Types from "@/Accounting";
import {Provider} from "@/UCloud/ProvidersApi";

const productTypes: {value: PropType<Types.Product, "type">, title: string}[] = [
    {value: "compute", title: "Compute"},
    {value: "network_ip", title: "Public IP"},
    {value: "ingress", title: "Public link"},
    {value: "license", title: "Software license"},
    {value: "storage", title: "Storage"},
];

function unitNameFromType(type: PropType<Types.Product, "type">): string {
    let unitName = "";
    switch (type) {
        case "compute":
            unitName = "minute";
            break;
        case "storage":
            unitName = "GB/day";
            break;
        case "network_ip":
        case "license":
        case "ingress":
            unitName = "activation";
            break;
    }
    return unitName;
}

export const ProductCreationForm: React.FunctionComponent<{provider: Provider, onComplete: () => void}> = props => {
    const [type, setType] = useState<PropType<Types.Product, "type">>("compute");
    const onTypeChange = useCallback(e => setType(e.target.value as PropType<Types.Product, "type">), [setType]);
    const [licenseTagCount, setTagCount] = useState(1);
    const [, invokeCommand] = useCloudCommand();

    const unitName = unitNameFromType(type);

    return <Box maxWidth={"800px"} margin={"0 auto"}>
        <Label>
            Type
            <Select value={type} onChange={onTypeChange}>
                {productTypes.map(it => <option key={it.value} value={it.value}>{it.title}</option>)}
            </Select>
        </Label>

        <ResourceForm
            title="Product"
            createRequest={async (data): Promise<APICallParameters<any>> => {
                const tokens = await invokeCommand<BulkResponse<AccessToken>>(UCloud.auth.providers.refresh(
                    bulkRequestOf({refreshToken: props.provider.refreshToken}))
                );

                const accessToken = tokens?.responses[0]?.accessToken;
                let product: Types.Product;

                const shared: Omit<Types.ProductBase, "productType"> = {
                    type,
                    category: {name: data.fields.name, provider: props.provider.id},
                    pricePerUnit: data.fields.pricePerUnit * 10_000,
                    name: data.fields.name,
                    description: data.fields.description,
                    priority: data.fields.priority,
                    version: data.fields.version,
                    freeToUse: data.fields.freeToUse,
                    unitOfPrice: data.fields.unitOfPrice,
                    chargeType: data.fields.chargeType,
                    hiddenInGrantApplications: data.fields.hiddenInGrantApplications,
                };

                const tags: string[] = [];
                for (let i = 0; i < licenseTagCount; i++) {
                    const entry = data.fields[`tag-${i}`];
                    if (entry) tags.push(entry);
                }

                switch (type) {
                    case "storage":
                        product = {
                            ...shared,
                            productType: "STORAGE"
                        } as Types.ProductStorage;
                        break;
                    case "compute":
                        product = {
                            ...shared,
                            productType: "COMPUTE",
                            cpu: data.fields.cpu,
                            memoryInGigs: data.fields.memory,
                            gpu: data.fields.gpu
                        } as Types.ProductCompute;
                        break;
                    case "ingress":
                        product = {
                            ...shared,
                            productType: "INGRESS",
                        } as Types.ProductIngress;
                        break;
                    case "network_ip":
                        product = {
                            ...shared,
                            productType: "NETWORK_IP"
                        } as Types.ProductNetworkIP;
                        break;
                    case "license":
                        product = {
                            ...shared,
                            productType: "LICENSE",
                            tags
                        } as Types.ProductLicense;
                        break;
                }

                return {...UCloud.accounting.products.createProduct(bulkRequestOf(product as any)), accessTokenOverride: accessToken};
            }}
        >
            <ResourceForm.Text required id="name" placeholder="Name..." label="Name (e.g. u1-standard-1)" styling={{}} />
            <ResourceForm.Number required id="pricePerUnit" placeholder="Price..." rightLabel="DKK" label={`Price per ${unitName}`} step="0.01" min={0} styling={{}} />
            <ResourceForm.TextArea required id="description" placeholder="Description..." label="Description" rows={10} styling={{}} />
            <ResourceForm.Number required id="priority" placeholder="Priority..." label="Priority" styling={{}} />
            <ResourceForm.Number required id="version" placeholder="Version..." label="Version" min={0} styling={{}} />
            <ResourceForm.Checkbox id="freeToUse" defaultChecked={false} label="Free to use" styling={{}} />
            <ResourceForm.Select id="unitOfPrice" label="Unit of Price" required options={[
                {value: "PER_UNIT", text: "Per Unit"},
                {value: "CREDITS_PER_MINUTE", text: "Credits Per Minute"},
                {value: "CREDITS_PER_HOUR", text: "Credits Per Hour"},
                {value: "CREDITS_PER_DAY", text: "Credits Per Day"},
                {value: "UNITS_PER_MINUTE", text: "Units Per Minute"},
                {value: "UNITS_PER_HOUR", text: "Units Per Hour"},
                {value: "UNITS_PER_DAY", text: "Units Per Day"}
            ]} styling={{}} />
            <ResourceForm.Select id="chargeType" label="Chargetype" required options={[
                {value: "ABSOLUTE", text: "Absolute"},
                {value: "DIFFERENTIAL_QUOTA", text: "Differential Quota"}
            ]} styling={{}} />
            <ResourceForm.Checkbox id="hiddenInGrantApplications" label="Hidden in Grant Applications" defaultChecked={false} styling={{}} />

            {type !== "compute" ? null : (
                <>
                    <ResourceForm.Number id="cpu" placeholder="vCPU..." label="vCPU" required styling={{}} />
                    <ResourceForm.Number id="memory" placeholder="Memory..." label="Memory in GB" required styling={{}} />
                    <ResourceForm.Number id="gpus" placeholder="GPUs..." label="Number of GPUs" required styling={{}} />
                </>
            )}
            {type !== "license" ? null : (
                <>
                    {[...Array(licenseTagCount).keys()].map(id =>
                        <ResourceForm.Text key={id} id={`tag-${id}`} label={`Tag ${id + 1}`} styling={{}} />
                    )}
                    <div>
                        <Button fullWidth type="button" onClick={() => setTagCount(t => t + 1)} mt="6px">Add tag</Button>
                    </div>
                </>
            )}
        </ResourceForm>
    </Box>;
};

interface OpCallbacks {
    provider: string;
    invokeCommand: InvokeCommand;
    reload: () => void;
    startProductCreation: () => void;
    stopProductCreation: () => void;
    isCreatingProduct: boolean;
}

const operations: Operation<UCloud.provider.Provider, OpCallbacks>[] = [
    {
        enabled: (_, cb) => !cb.isCreatingProduct,
        onClick: (_, cb) => {
            cb.startProductCreation();
        },
        text: "Create product",
        operationType: () => Button,
    },
    {
        enabled: selected => selected.length > 0,
        onClick: (selected, cb) =>
            addStandardDialog({
                title: "WARNING!",
                message: <>
                    <p>Are you sure you want to renew the provider token?</p>
                    <p>
                        This will invalidate every current security token. Your provider <i>must</i> be reconfigured
                        to use the new tokens.
                    </p>
                </>,
                confirmText: "Confirm",
                cancelButtonColor: "blue",
                confirmButtonColor: "red",
                cancelText: "Cancel",
                onConfirm: async () => {
                    await cb.invokeCommand(UCloud.provider.providers.renewToken(
                        {type: "bulk", items: selected.map(it => ({id: it.id}))}
                    ));

                    cb.reload();
                }
            }),
        text: "Renew token",
        color: "red",
        icon: "trash",
        operationType: () => Button,
    },
    {
        enabled: selected => selected.length === 1 && (inDevEnvironment() || onDevSite()),
        onClick: async (selected, cb) => {
            await cb.invokeCommand(
                UCloud.accounting.wallets.grantProviderCredits({provider: cb.provider})
            );
            cb.reload();
        },
        text: "Grant credits",
        operationType: () => Button
    }
];
