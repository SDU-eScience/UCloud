import { Client } from "@/Authentication/HttpClientInstance";
import {Maintenance, MaintenanceAvailability } from "@/UCloud/ResourceApi";
import {dateToString} from "@/Utilities/DateUtilities";

export function maintenanceIconColor(maintenance: Maintenance | undefined | null): string | undefined {
    let iconColor: string | undefined;
    switch (maintenance?.availability) {
        case MaintenanceAvailability.MAJOR_DISRUPTION:
            iconColor = "orange";
            break;
        case MaintenanceAvailability.MINOR_DISRUPTION:
            iconColor = undefined;
            break;
        case MaintenanceAvailability.NO_SERVICE:
            iconColor = "red";
            break;
    }
    return iconColor;
}

export function shouldAllowMaintenanceAccess(): boolean {
    return localStorage.getItem("NO_MAINTENANCE_BLOCK") != null ||
        Client.userRole === "ADMIN";
}


export function explainMaintenance(maintenance: Maintenance): string {
    let message: string;
    switch (maintenance.availability) {
        case MaintenanceAvailability.MINOR_DISRUPTION:
            message = "Ongoing maintenance, minor disruption of service is expected.";
            break;
        case MaintenanceAvailability.MAJOR_DISRUPTION:
            message = "Ongoing maintenance, significant disruption of service is expected.";
            break;
        case MaintenanceAvailability.NO_SERVICE:
            message = "Unavailable due to maintenance.";
            break;
    }
    message += " ";

    let expectedDuration: string = "";
    if (maintenance.estimatedEndsAt) {
        expectedDuration = `The maintenance is expected to end at ${dateToString(maintenance.estimatedEndsAt)}. `;
    }

    return message + expectedDuration + maintenance.description;
}
