import * as React from "react";
import * as UCloud from "@/UCloud";
import {Button, Flex, Input, Label} from "@/ui-components";
import {TextP} from "@/ui-components/Text";
import {
    findRelevantMachinesForApplication, Machines, setMachineReservationFromRef, validateMachineReservation
} from "@/Applications/Jobs/Widgets/Machines";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {MandatoryField} from "@/Applications/Jobs/Widgets/index";
import {productCategoryEquals, ProductV2, ProductV2Compute} from "@/Accounting";
import {joinToString} from "@/UtilityFunctions";
import {useProjectId} from "@/Project/Api";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import {classConcat, injectStyle} from "@/Unstyled";
import * as Accounting from "@/Accounting";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {Application} from "@/Applications/AppStoreApi";
import {totalUsageExcludingRetiredIfNeeded} from "@/Accounting/Allocations";

const reservationName = "reservation-name";
const reservationHours = "reservation-hours";
const reservationReplicas = "reservation-replicas";

export function ReservationParameter({application, errors, onEstimatedCostChange}: React.PropsWithChildren<{
    application: Application;
    errors: ReservationErrors;
    onEstimatedCostChange?: (durationInMinutes: number, numberOfNodes: number, walletBalance: number, walletMaxUsable: number, product: ProductV2 | null) => void;
}>): React.ReactNode {
    // Estimated cost
    const [selectedMachine, setSelectedMachine] = useState<ProductV2Compute | null>(null);
    const [wallets, fetchWallets] = useCloudAPI<UCloud.PageV2<Accounting.WalletV2>>({noop: true}, emptyPageV2);
    const [products, fetchProducts] = useCloudAPI<UCloud.PageV2<ProductV2Compute>>({noop: true}, emptyPageV2);
    const wallet = selectedMachine ?
        wallets.data.items.find(it => productCategoryEquals(it.paysFor, selectedMachine.category)) :
        undefined;
    
    const balance = wallet ? wallet.quota - totalUsageExcludingRetiredIfNeeded(wallet) : 0;
    const maxUsable = wallet ? wallet.maxUsable : 0;

    const [machineSupport, fetchMachineSupport] = useCloudAPI<UCloud.compute.JobsRetrieveProductsResponse>(
        {noop: true},
        {productsByProvider: {}}
    );

    const projectId = useProjectId();
    useEffect(() => {
        fetchWallets(Accounting.browseWalletsV2({itemsPerPage: 250}));
        fetchProducts(UCloud.accounting.products.browse({
            filterUsable: true,
            filterProductType: "COMPUTE",
            itemsPerPage: 250,
            includeBalance: true,
            includeMaxBalance: true
        }));
    }, [projectId]);
    useEffect(() => {
        const s = new Set<string>();
        products.data.items.forEach(it => s.add(it.category.provider));

        if (s.size > 0) {
            fetchMachineSupport(UCloud.compute.jobs.retrieveProducts({
                providers: joinToString(Array.from(s), ",")
            }));
        }
    }, [products]);

    const allMachines = findRelevantMachinesForApplication(application, machineSupport.data, products.data);
    const support = useMemo(() => {
        const items: ResolvedSupport[] = [];
        let productsByProvider = machineSupport.data.productsByProvider;
        for (const provider of Object.keys(productsByProvider)) {
            const providerProducts = productsByProvider[provider];
            // TODO(Dan): We need to fix some of these types soon. We are still using a lot of the old generated stuff.
            for (const item of providerProducts) items.push((item as unknown) as ResolvedSupport);
        }
        return items;
    }, [machineSupport.data]);

    const recalculateCost = useCallback(() => {
        const {options} = validateReservation();
        if (options != null && options.timeAllocation != null) {
            if (onEstimatedCostChange) {
                onEstimatedCostChange(options.timeAllocation?.hours * 60 + options.timeAllocation?.minutes, options.replicas, balance, maxUsable, selectedMachine);
            }
        }
    }, [selectedMachine, balance, onEstimatedCostChange]);

    useEffect(() => {
        recalculateCost();
    }, [selectedMachine]);

    const toolBackend = application.invocation.tool.tool?.description?.backend ?? "DOCKER";

    const adjustHours = useCallback((ev: React.SyntheticEvent) => {
        const target = ev.target as HTMLElement;
        if (!target) return;
        const amount = parseInt(target.getAttribute("data-amount") ?? "invalid");
        if (isNaN(amount)) return;
        const hours = document.querySelector<HTMLInputElement>(`#${reservationHours}`);
        if (!hours) return;
        let existing = hours.valueAsNumber;
        if (isNaN(existing)) existing = 0;
        const hourAmount = existing + amount;
        hours.value = hourAmount.toString();
        recalculateCost();
    }, [recalculateCost]);

    useEffect(() => {
        // Chrome (and others?) have this annoying feature that if you scroll on an input field you scroll both the page
        // and the value. This has lead to a lot of people accidentally changing the resources requested. We now
        // intercept these events and simply blur the element before the value is changed.
        const stupidChromeListener = () => {
            if (document.activeElement?.["type"] === "number") {
                (document.activeElement as HTMLInputElement).blur();
            }
        };

        document.addEventListener("wheel", stupidChromeListener);
        return () => {
            document.removeEventListener("wheel", stupidChromeListener);
        };
    }, []);

    return <div>
        <Flex justifyContent="space-between" gap="15px">
            <Label>
                Job name
                <Input
                    className={classConcat(JobCreateInput, "name-kind")}
                    id={reservationName}
                    placeholder={"Example: Run with parameters XYZ"}
                />
                {errors["name"] ? <TextP color={"errorMain"}>{errors["name"]}</TextP> : null}
            </Label>
            {toolBackend === "DOCKER" || toolBackend === "NATIVE" ?
                <Flex gap={"8px"} alignItems={"end"}>
                    <Label>
                        Hours<MandatoryField/>
                        <Input
                            id={reservationHours}
                            className={classConcat(JobCreateInput, "hours-kind")}
                            type="number"
                            step={1}
                            min={1}
                            onBlur={recalculateCost}
                            defaultValue={Math.max(1, application.invocation.tool.tool?.description?.defaultTimeAllocation?.hours ?? 1)}
                            style={{minWidth: "100px"}}
                        />
                    </Label>
                    <Button width="40px" data-amount={1} onClick={adjustHours}>+1</Button>
                    <Button width="40px" data-amount={8} onClick={adjustHours}>+8</Button>
                    <Button width="40px" data-amount={24} onClick={adjustHours}>+24</Button>
                </Flex>
                : null}
        </Flex>
        {toolBackend === "VIRTUAL_MACHINE" ?
            <input type={"hidden"} id={reservationHours} value={"1"}/>
            : null}
        {errors["timeAllocation"] ? <TextP color={"errorMain"}>{errors["timeAllocation"]}</TextP> : null}

        {!application.invocation.allowMultiNode ? null : (
            <>
                <Flex pt={"20px"}>
                    <Label>
                        Number of nodes
                        <Input id={reservationReplicas} className={JobCreateInput} onBlur={recalculateCost}
                               defaultValue={"1"}/>
                    </Label>
                </Flex>
                {errors["replicas"] ? <TextP color={"errorMain"}>{errors["replicas"]}</TextP> : null}
            </>
        )}

        <div style={{paddingTop: "20px"}}>
            <Label>Machine type <MandatoryField/></Label>
            <Machines machines={allMachines} loading={machineSupport.loading} support={support}
                      onMachineChange={setSelectedMachine}/>
            {errors["product"] ? <TextP color={"errorMain"}>{errors["product"]}</TextP> : null}
        </div>
    </div>
};

export type ReservationValues = Pick<UCloud.compute.JobSpecification, "name" | "timeAllocation" | "replicas" | "product">;

export const JobCreateInput = injectStyle("job-or-hours-input", k => `
    ${k}::placeholder {
        color: var(--textSecondary);
    }
`);

interface ValidationAnswer {
    options?: ReservationValues;
    errors: ReservationErrors;
}

export type ReservationErrors = {
    [P in keyof ReservationValues]?: string;
}

export function validateReservation(): ValidationAnswer {
    const name = document.getElementById(reservationName) as HTMLInputElement | null;
    const hours = document.getElementById(reservationHours) as HTMLInputElement | null;
    const replicas = document.getElementById(reservationReplicas) as HTMLInputElement | null;

    if (name === null || hours === null) throw "Reservation component not mounted";

    const values: Partial<ReservationValues> = {};
    const errors: ReservationErrors = {};
    if (hours.value === "") {
        errors["timeAllocation"] = "Missing value supplied for hours";
    } else if (!/^\d+$/.test(hours.value)) {
        errors["timeAllocation"] = "Invalid value supplied for hours. Example: 1";
    }

    if (!errors["timeAllocation"]) {
        const parsedHours = parseInt(hours.value, 10);

        values["timeAllocation"] = {
            hours: parsedHours,
            minutes: 0,
            seconds: 0
        };
    }

    values["name"] = name.value === "" ? undefined : name.value;

    if (replicas != null) {
        if (!/^\d+$/.test(replicas.value)) {
            errors["replicas"] = "Invalid value supplied for nodes. Example: 1";
        }

        values["replicas"] = parseInt(replicas.value, 10);
    } else {
        values["replicas"] = 1;
    }

    const machineReservation = validateMachineReservation();
    if (machineReservation === null) {
        errors["product"] = "No machine type selected";
    } else {
        values["product"] = machineReservation;
    }

    return {
        options: Object.keys(errors).length > 0 ? undefined : values as ReservationValues,
        errors
    };
}

export function setReservation(values: Partial<ReservationValues>): void {
    const name = document.getElementById(reservationName) as HTMLInputElement | null;
    const hours = document.getElementById(reservationHours) as HTMLInputElement | null;
    const replicas = document.getElementById(reservationReplicas) as HTMLInputElement | null;

    if (name === null || hours === null) throw "Reservation component not mounted";

    name.value = values.name ?? "";
    hours.value = values.timeAllocation?.hours?.toString(10) ?? "";

    if (replicas != null && values.replicas !== undefined) replicas.value = values.replicas.toString(10)

    if (values.product !== undefined) setMachineReservationFromRef(values.product);
}
