import * as React from "react";
import * as UCloud from "@/UCloud";
import {Box, Flex, Input, Label} from "@/ui-components";
import {TextP} from "@/ui-components/Text";
import {
    findRelevantMachinesForApplication,
    Machines,
    setMachineReservationFromRef,
    validateMachineReservation
} from "@/Applications/Jobs/Widgets/Machines";
import {useCallback, useEffect, useState} from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useProjectId} from "@/Project";
import {MandatoryField} from "@/Applications/Jobs/Widgets/index";
import {costOfDuration, Product, productCategoryEquals, ProductCompute} from "@/Accounting";
import {emptyPageV2} from "@/DefaultObjects";
import {joinToString} from "@/UtilityFunctions";

const reservationName = "reservation-name";
const reservationHours = "reservation-hours";
const reservationReplicas = "reservation-replicas";

export const ReservationParameter: React.FunctionComponent<{
    application: UCloud.compute.Application;
    errors: ReservationErrors;
    onEstimatedCostChange?: (cost: number, balance: number, product: Product | null) => void;
}> = ({application, errors, onEstimatedCostChange}) => {
    // Estimated cost
    const [selectedMachine, setSelectedMachine] = useState<ProductCompute | null>(null);
    const [wallet, fetchWallet] = useCloudAPI<UCloud.PageV2<ProductCompute>>({noop: true}, emptyPageV2);
    // TODO
    const balance = !selectedMachine ?
        0 :
        wallet.data.items.find(it => productCategoryEquals(it.category, selectedMachine.category))?.["balance"] ?? 0;

    const [machineSupport, fetchMachineSupport] = useCloudAPI<UCloud.compute.JobsRetrieveProductsResponse>(
        {noop: true},
        {productsByProvider: {}}
    );

    const projectId = useProjectId();
    useEffect(() => {
        fetchWallet(UCloud.accounting.products.browse({
            filterUsable: true,
            filterArea: "COMPUTE",
            itemsPerPage: 250,
            includeBalance: true,
            includeMaxBalance: true
        }));
    }, [projectId]);
    useEffect(() => {
        const s = new Set<string>();
        wallet.data.items.forEach(it => s.add(it.category.provider));

        if (s.size > 0) {
            fetchMachineSupport(UCloud.compute.jobs.retrieveProducts({
                providers: joinToString(Array.from(s), ",")
            }));
        }
    }, [wallet]);

    const allMachines = findRelevantMachinesForApplication(application, machineSupport.data, wallet.data);

    const recalculateCost = useCallback(() => {
        const {options} = validateReservation();
        if (options != null && options.timeAllocation != null) {
            let estimatedCost = 0;
            if (selectedMachine != null) {
                estimatedCost = costOfDuration(options.timeAllocation.hours * 60 + options.timeAllocation.minutes,
                    options.replicas, selectedMachine);
            }
            if (onEstimatedCostChange) onEstimatedCostChange(estimatedCost, balance, selectedMachine);
        }
    }, [selectedMachine, balance, onEstimatedCostChange]);

    useEffect(() => {
        recalculateCost();
    }, [selectedMachine]);

    const toolBackend = application.invocation.tool.tool?.description?.backend ?? "DOCKER";

    return <Box>
        <Flex>
            <Label mb={"4px"}>
                Job name
                <Input
                    id={reservationName}
                    placeholder={"Example: Run with parameters XYZ"}
                />
                {errors["name"] ? <TextP color={"red"}>{errors["name"]}</TextP> : null}
            </Label>
            {toolBackend === "DOCKER" || toolBackend === "NATIVE" ?
                <>
                    <Box ml="4px" />

                    <Label>
                        Hours <MandatoryField />
                        <Input
                            id={reservationHours}
                            type="number"
                            step={1}
                            min={1}
                            onBlur={recalculateCost}
                            defaultValue={Math.max(1, application.invocation.tool.tool?.description?.defaultTimeAllocation?.hours ?? 1)}
                        />
                    </Label>
                </>
                : null}
        </Flex>
        {toolBackend === "VIRTUAL_MACHINE" ?
            <input type={"hidden"} id={reservationHours} value={"1"} />
            : null}
        {errors["timeAllocation"] ? <TextP color={"red"}>{errors["timeAllocation"]}</TextP> : null}

        {!application.invocation.allowMultiNode ? null : (
            <>
                <Flex mb={"1em"}>
                    <Label>
                        Number of nodes
                        <Input id={reservationReplicas} onBlur={recalculateCost} defaultValue={"1"} />
                    </Label>
                </Flex>
                {errors["replicas"] ? <TextP color={"red"}>{errors["replicas"]}</TextP> : null}
            </>
        )}

        <div>
            <Label>Machine type <MandatoryField /></Label>
            <Machines machines={allMachines} onMachineChange={setSelectedMachine} />
            {errors["product"] ? <TextP color={"red"}>{errors["product"]}</TextP> : null}
        </div>
    </Box>
};

export type ReservationValues = Pick<UCloud.compute.JobSpecification, "name" | "timeAllocation" | "replicas" | "product">;

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
