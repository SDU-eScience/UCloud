import * as React from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Box, Button, Grid, Label, Select} from "@/ui-components";
import {auth, BulkResponse} from "@/UCloud";
import {bulkRequestOf} from "@/DefaultObjects";
import AccessToken = auth.AccessToken;
import ResourceForm, {DataType} from "@/Products/CreateProduct";
import * as Types from "@/Accounting";
import {Provider} from "@/UCloud/ProvidersApi";
import {
    explainAllocation,
    productTypes as allProductTypes,
    ProductType,
    productTypeToTitle,
    productTypeToJsonType, normalizeBalanceForBackend
} from "@/Accounting";

const productTypesWithoutSync: {value: ProductType, title: string}[] =
    allProductTypes.filter(it => it !== "SYNCHRONIZATION").map(value => ({value, title: productTypeToTitle(value)}));

type ProductTypeWithoutSync = Exclude<ProductType, "SYNCHRONIZATION">;

export const ProductCreationForm: React.FunctionComponent<{provider: Provider, onComplete: () => void}> = props => {
    const [type, setType] = useState<ProductTypeWithoutSync>("COMPUTE");
    const typeHolder = useRef<ProductTypeWithoutSync>(type);
    const onTypeChange = useCallback(e => setType(e.target.value as ProductTypeWithoutSync), [setType]);
    const [licenseTagCount, setTagCount] = useState(1);
    const [, invokeCommand] = useCloudCommand();
    useEffect(() => {
        typeHolder.current = type
    }, [type]);

    const priceDependencies = useMemo(() => ["unitOfPrice", "chargeType", "type"], []);
    const unitEvaluator = useCallback((t: DataType) => {
        const productType = typeHolder.current;
        const unitOfPrice = t.fields["unitOfPrice"];
        const chargeType = t.fields["chargeType"];

        if (!unitOfPrice || !chargeType) return "DKK";
        const res = explainAllocation(productType, chargeType, unitOfPrice);
        return res;
    }, [type]);

    return <Box maxWidth={"800px"} margin={"0 auto"}>
        <Label>
            Type
            <Select id={"type"} value={type} onChange={onTypeChange}>
                {productTypesWithoutSync.map(it => <option key={it.value} value={it.value}>{it.title}</option>)}
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

                const normalizedPricePerUnit = normalizeBalanceForBackend(data.fields.pricePerUnit, type, data.fields.chargeType, data.fields.unitOfPrice);
                const pricePerUnit = normalizedPricePerUnit === 0 ? 1 : normalizedPricePerUnit;

                const shared: Types.ProductBase = {
                    type: productTypeToJsonType(type),
                    productType: type,
                    category: {name: data.fields.category, provider: props.provider.specification.id},
                    pricePerUnit,
                    name: data.fields.name,
                    description: data.fields.description,
                    priority: data.fields.priority,
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
                    case "STORAGE":
                        product = {
                            ...shared,
                        } as Types.ProductStorage;
                        break;
                    case "COMPUTE":
                        product = {
                            ...shared,
                            cpu: data.fields.cpu,
                            memoryInGigs: data.fields.memory,
                            gpu: data.fields.gpu
                        } as Types.ProductCompute;
                        break;
                    case "INGRESS":
                        product = {
                            ...shared,
                        } as Types.ProductIngress;
                        break;
                    case "NETWORK_IP":
                        product = {
                            ...shared,
                        } as Types.ProductNetworkIP;
                        break;
                    case "LICENSE":
                        product = {
                            ...shared,
                            tags
                        } as Types.ProductLicense;
                        break;
                }

                return {
                    ...UCloud.accounting.products.createProduct(bulkRequestOf(product as any)),
                    accessTokenOverride: accessToken
                };
            }}
        >
            <Grid gridTemplateColumns={"1fr"} gridGap={"32px"}>
                <div>
                    <ResourceForm.Text required id="name" placeholder="Name..." label="Name (e.g. u1-standard-1)"
                        styling={{}} />
                    <ResourceForm.Text required id="category" placeholder="Name..." label="Category (e.g. u1-standard)"
                        styling={{}} />
                    <ResourceForm.Text required id="description" placeholder="Description..." label="Description"
                        styling={{}} />
                    <ResourceForm.Number required id="priority" placeholder="Priority..." label="Priority"
                        styling={{}} />
                    <ResourceForm.Checkbox id="hiddenInGrantApplications" label="Hidden in Grant Applications"
                        defaultChecked={false} styling={{}} />
                </div>
                <div>
                    <ResourceForm.Select id="chargeType" label="Payment Model" required options={[
                        {value: "ABSOLUTE", text: "Absolute"},
                        {value: "DIFFERENTIAL_QUOTA", text: "Differential (Quota)"}
                    ]} styling={{}} />

                    <ResourceForm.Select id="unitOfPrice" label="Unit of Price" required options={[
                        {value: "CREDITS_PER_MINUTE", text: "Credits Per Minute"},
                        {value: "CREDITS_PER_HOUR", text: "Credits Per Hour"},
                        {value: "CREDITS_PER_DAY", text: "Credits Per Day"},
                        {value: "PER_UNIT", text: "Per Unit"},
                        {value: "UNITS_PER_MINUTE", text: "Units Per Minute"},
                        {value: "UNITS_PER_HOUR", text: "Units Per Hour"},
                        {value: "UNITS_PER_DAY", text: "Units Per Day"}
                    ]} styling={{}} />

                    <ResourceForm.Number required id="pricePerUnit" placeholder="Price..." rightLabel={unitEvaluator}
                        label={`Price`} step="0.01" min={0} styling={{}}
                        dependencies={priceDependencies} />

                    <ResourceForm.Checkbox id="freeToUse" defaultChecked={false} label="Free to use" styling={{}} />

                </div>

                {type !== "COMPUTE" ? null : (
                    <div>
                        <ResourceForm.Number id="cpu" placeholder="vCPU..." label="vCPU" required styling={{}} />
                        <ResourceForm.Number id="memory" placeholder="Memory..." label="Memory in GB" required
                            styling={{}} />
                        <ResourceForm.Number id="gpus" placeholder="GPUs..." label="Number of GPUs" required
                            styling={{}} />
                    </div>
                )}
                {type !== "LICENSE" ? null : (
                    <div>
                        {[...Array(licenseTagCount).keys()].map(id =>
                            <ResourceForm.Text key={id} id={`tag-${id}`} label={`Tag ${id + 1}`} styling={{}} />
                        )}
                        <div>
                            <Button fullWidth type="button" onClick={() => setTagCount(t => t + 1)} mt="6px">Add
                                tag</Button>
                        </div>
                    </div>
                )}
            </Grid>
        </ResourceForm>
    </Box>;
};
