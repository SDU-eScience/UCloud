export interface AllocationSlice {
    allocationId?: number;
    start: number;
    end: number;
    quota: number;
}

function allocationCalendarTime(timestamp: number): number {
    const date = new Date(timestamp);
    return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds(), date.getMilliseconds());
}

function allocationMonthShare(start: number, end: number): number {
    let share = 0;
    let cursor = start;
    while (cursor < end) {
        const date = new Date(cursor);
        const monthStart = new Date(date.getFullYear(), date.getMonth(), 1).getTime();
        const monthEnd = new Date(date.getFullYear(), date.getMonth() + 1, 1).getTime();
        const segmentEnd = Math.min(end, monthEnd);
        share += (allocationCalendarTime(segmentEnd) - allocationCalendarTime(cursor))
            / (allocationCalendarTime(monthEnd) - allocationCalendarTime(monthStart));
        cursor = segmentEnd;
    }
    return share;
}

export function allocationSlices(start: number, end: number, months: number, quota: number): AllocationSlice[] {
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return [];
    if (!Number.isInteger(months) || months <= 0) return [{start, end, quota}];

    const first = new Date(start);
    const last = new Date(end - 1);
    const coveredMonths = (last.getFullYear() - first.getFullYear()) * 12 + last.getMonth() - first.getMonth() + 1;
    if (months >= coveredMonths) return [{start, end, quota}];
    const periods: {start: number; end: number; share: number}[] = [];
    let cursor = start;
    let offset = months;
    while (cursor < end) {
        const boundary = new Date(first.getFullYear(), first.getMonth() + offset, 1).getTime();
        const sliceEnd = Math.min(end, boundary);
        periods.push({start: cursor, end: sliceEnd, share: allocationMonthShare(cursor, sliceEnd)});
        cursor = sliceEnd;
        offset += months;
    }

    const totalShare = periods.reduce((sum, slice) => sum + slice.share, 0);
    let allocated = 0;
    let cumulativeShare = 0;
    return periods.map((period, index) => {
        cumulativeShare += period.share;
        const cumulativeQuota = index === periods.length - 1 ? quota : Math.round(quota * cumulativeShare / totalShare);
        const sliceQuota = cumulativeQuota - allocated;
        allocated = cumulativeQuota;
        return {start: period.start, end: period.end, quota: sliceQuota};
    });
}
