import * as React from "react";
import * as UCloud from "UCloud";
import {Box, Flex, Input, Label} from "ui-components";
import {MandatoryField} from "Applications/Widgets/BaseParameter";
import {TextP} from "ui-components/Text";
import {
    Machines,
    setMachineReservationFromRef,
    validateMachineReservation
} from "Applications/Jobs/Widgets/Machines";

const reservationName = "reservation-name";
const reservationHours = "reservation-hours";
const reservationMinutes = "reservation-minutes";
const reservationReplicas = "reservation-replicas";

export const ReservationParameter: React.FunctionComponent<{
    application: UCloud.compute.Application;
    errors: ReservationErrors;
}> = ({application, errors}) => {
    return <Box>
        <Label mb={"4px"} mt={"4px"}>
            Job name
            <Input id={reservationName} placeholder={"Example: Run with parameters XYZ"}/>
            {errors["name"] ? <TextP color={"red"}>{errors["name"]}</TextP> : null}
        </Label>

        <Flex mb={"1em"}>
            <Label>
                Hours <MandatoryField/>
                <Input id={reservationHours}/>
            </Label>
            <Box ml="4px"/>
            <Label>
                Minutes <MandatoryField/>
                <Input id={reservationMinutes}/>
            </Label>
        </Flex>
        {errors["timeAllocation"] ? <TextP color={"red"}>{errors["timeAllocation"]}</TextP> : null}

        {!application.invocation.allowMultiNode ? null : (
            <>
                <Flex mb={"1em"}>
                    <Label>
                        Number of replicas
                        <Input id={reservationReplicas}/>
                    </Label>
                </Flex>
                {errors["replicas"] ? <TextP color={"red"}>{errors["replicas"]}</TextP> : null}
            </>
        )}

        <div>
            <Label>Machine type <MandatoryField/></Label>
            <Machines />
            {errors["product"] ? <TextP color={"red"}>{errors["product"]}</TextP> : null}
        </div>
    </Box>
};

export type ReservationValues = Pick<UCloud.compute.JobParameters, "name" | "timeAllocation" | "replicas" | "product">;

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
    const minutes = document.getElementById(reservationMinutes) as HTMLInputElement | null;
    const replicas = document.getElementById(reservationReplicas) as HTMLInputElement | null;

    if (name === null || hours === null || minutes === null) throw "Reservation component not mounted";

    const values: Partial<ReservationValues> = {};
    const errors: ReservationErrors = {};
    if (hours.value === "") {
        errors["timeAllocation"] = "Missing value supplied for hours";
    } else if (minutes.value === "") {
        errors["timeAllocation"] = "Missing value supplied for minutes";
    } else if (!/^\d+$/.test(hours.value)) {
        errors["timeAllocation"] = "Invalid value supplied for hours. Example: 1";
    } else if (!/^\d+$/.test(minutes.value)) {
        errors["timeAllocation"] = "Invalid value supplied for minutes. Example: 0";
    }

    if (!errors["timeAllocation"]) {
        const parsedHours = parseInt(hours.value, 10);
        const parsedMinutes = parseInt(minutes.value, 10);

        values["timeAllocation"] = {
            hours: parsedHours,
            minutes: parsedMinutes,
            seconds: 0
        };
    }

    values["name"] = name.value === "" ? undefined : name.value;

    if (replicas != null) {
        if (!/^\d+$/.test(replicas.value)) {
            errors["replicas"] = "Invalid value supplied for replicas. Example: 1";
        }

        values["replicas"] = parseInt(replicas.value, 10);
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

export function setReservation(values: ReservationValues): void {
    const name = document.getElementById(reservationName) as HTMLInputElement | null;
    const hours = document.getElementById(reservationHours) as HTMLInputElement | null;
    const minutes = document.getElementById(reservationMinutes) as HTMLInputElement | null;
    const replicas = document.getElementById(reservationReplicas) as HTMLInputElement | null;

    if (name === null || hours === null || minutes === null) throw "Reservation component not mounted";

    name.value = values.name ?? "";
    hours.value = values.timeAllocation?.hours?.toString(10) ?? "";
    minutes.value = values.timeAllocation?.minutes?.toString(10) ?? "";
    if (replicas != null) replicas.value = values.replicas.toString(10)

    setMachineReservationFromRef(values.product);
}
