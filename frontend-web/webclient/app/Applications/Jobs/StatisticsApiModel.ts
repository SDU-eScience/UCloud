// GENERATED CODE - DO NOT MODIFY - See StatisticsModel.msg
// GENERATED CODE - DO NOT MODIFY - See StatisticsModel.msg
// GENERATED CODE - DO NOT MODIFY - See StatisticsModel.msg

import { BinaryAllocator, UBinaryType, BinaryTypeCompanion, UText, UTextCompanion, BufferAndOffset, BinaryTypeList, BinaryTypeDictionary } from "@/UCloud/Messages";
import { ProductCategoryB, ProductCategoryBCompanion } from "@/Accounting/AccountingBinary";

// import dk.sdu.cloud.accounting.api.*

export class JobStatistics implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get categories(): BinaryTypeList<ProductCategoryB> {
        let result: BinaryTypeList<ProductCategoryB> | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<ProductCategoryB>(ProductCategoryBCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set categories(value) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
    }

    get usageByUser(): BinaryTypeList<JobUsageByUser> {
        let result: BinaryTypeList<JobUsageByUser> | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<JobUsageByUser>(JobUsageByUserCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set usageByUser(value) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
    }

    get mostUsedApplications(): BinaryTypeList<MostUsedApplications> {
        let result: BinaryTypeList<MostUsedApplications> | null = null;
        const ptr = this.buffer.buf.getInt32(8 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<MostUsedApplications>(MostUsedApplicationsCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set mostUsedApplications(value) {
        if (value === null) this.buffer.buf.setInt32(8 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(8 + this.buffer.offset, value.buffer.offset);
    }

    get jobSubmissionStatistics(): BinaryTypeList<JobSubmissionStatistics> {
        let result: BinaryTypeList<JobSubmissionStatistics> | null = null;
        const ptr = this.buffer.buf.getInt32(12 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<JobSubmissionStatistics>(JobSubmissionStatisticsCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set jobSubmissionStatistics(value) {
        if (value === null) this.buffer.buf.setInt32(12 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(12 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            categories: this.categories?.encodeToJson() ?? null,
            usageByUser: this.usageByUser?.encodeToJson() ?? null,
            mostUsedApplications: this.mostUsedApplications?.encodeToJson() ?? null,
            jobSubmissionStatistics: this.jobSubmissionStatistics?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        categories: BinaryTypeList<ProductCategoryB>,
        usageByUser: BinaryTypeList<JobUsageByUser>,
        mostUsedApplications: BinaryTypeList<MostUsedApplications>,
        jobSubmissionStatistics: BinaryTypeList<JobSubmissionStatistics>,
    ): JobStatistics {
        const result = allocator.allocate(JobStatisticsCompanion);
        result.categories = categories;
        result.usageByUser = usageByUser;
        result.mostUsedApplications = mostUsedApplications;
        result.jobSubmissionStatistics = jobSubmissionStatistics;
        return result;
    }
}
export const JobStatisticsCompanion: BinaryTypeCompanion<JobStatistics> = {
    size: 16,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let categories: BinaryTypeList<ProductCategoryB> | null = null;
        {
            const valueForJsonDecode = element['categories'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'categories' to be an array";
            categories = BinaryTypeList.create(
                ProductCategoryBCompanion,
                allocator,
                valueForJsonDecode.map(it => ProductCategoryBCompanion.decodeFromJson(allocator, it))
            );
            if (categories === null) throw "Did not expect 'categories' to be null!";
        }
        let usageByUser: BinaryTypeList<JobUsageByUser> | null = null;
        {
            const valueForJsonDecode = element['usageByUser'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'usageByUser' to be an array";
            usageByUser = BinaryTypeList.create(
                JobUsageByUserCompanion,
                allocator,
                valueForJsonDecode.map(it => JobUsageByUserCompanion.decodeFromJson(allocator, it))
            );
            if (usageByUser === null) throw "Did not expect 'usageByUser' to be null!";
        }
        let mostUsedApplications: BinaryTypeList<MostUsedApplications> | null = null;
        {
            const valueForJsonDecode = element['mostUsedApplications'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'mostUsedApplications' to be an array";
            mostUsedApplications = BinaryTypeList.create(
                MostUsedApplicationsCompanion,
                allocator,
                valueForJsonDecode.map(it => MostUsedApplicationsCompanion.decodeFromJson(allocator, it))
            );
            if (mostUsedApplications === null) throw "Did not expect 'mostUsedApplications' to be null!";
        }
        let jobSubmissionStatistics: BinaryTypeList<JobSubmissionStatistics> | null = null;
        {
            const valueForJsonDecode = element['jobSubmissionStatistics'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'jobSubmissionStatistics' to be an array";
            jobSubmissionStatistics = BinaryTypeList.create(
                JobSubmissionStatisticsCompanion,
                allocator,
                valueForJsonDecode.map(it => JobSubmissionStatisticsCompanion.decodeFromJson(allocator, it))
            );
            if (jobSubmissionStatistics === null) throw "Did not expect 'jobSubmissionStatistics' to be null!";
        }
        return JobStatistics.create(
            allocator,
            categories,
            usageByUser,
            mostUsedApplications,
            jobSubmissionStatistics,
        );
    },
    create: (buf) => new JobStatistics(buf),
};

export class JobUsageByUser implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get categoryIndex(): number {
        return this.buffer.buf.getInt32(0 + this.buffer.offset)
    }

    set categoryIndex(value: number) {
        this.buffer.buf.setInt32(0 + this.buffer.offset, value)
    }

    get dataPoints(): BinaryTypeList<JobUsageByUserDataPoint> {
        let result: BinaryTypeList<JobUsageByUserDataPoint> | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<JobUsageByUserDataPoint>(JobUsageByUserDataPointCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set dataPoints(value) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            categoryIndex: this.categoryIndex,
            dataPoints: this.dataPoints?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        categoryIndex: number,
        dataPoints: BinaryTypeList<JobUsageByUserDataPoint>,
    ): JobUsageByUser {
        const result = allocator.allocate(JobUsageByUserCompanion);
        result.categoryIndex = categoryIndex;
        result.dataPoints = dataPoints;
        return result;
    }
}
export const JobUsageByUserCompanion: BinaryTypeCompanion<JobUsageByUser> = {
    size: 8,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let categoryIndex: number | null = null;
        {
            const valueForJsonDecode = element['categoryIndex'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'categoryIndex' to be a number";
            categoryIndex = valueForJsonDecode;
            if (categoryIndex === null) throw "Did not expect 'categoryIndex' to be null!";
        }
        let dataPoints: BinaryTypeList<JobUsageByUserDataPoint> | null = null;
        {
            const valueForJsonDecode = element['dataPoints'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'dataPoints' to be an array";
            dataPoints = BinaryTypeList.create(
                JobUsageByUserDataPointCompanion,
                allocator,
                valueForJsonDecode.map(it => JobUsageByUserDataPointCompanion.decodeFromJson(allocator, it))
            );
            if (dataPoints === null) throw "Did not expect 'dataPoints' to be null!";
        }
        return JobUsageByUser.create(
            allocator,
            categoryIndex,
            dataPoints,
        );
    },
    create: (buf) => new JobUsageByUser(buf),
};

export class JobUsageByUserDataPoint implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get _username(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _username(value: UText) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
        }
    }
    get username(): string {
        return this._username?.decode() ?? null;
    }

    get usage(): bigint {
        return this.buffer.buf.getBigInt64(4 + this.buffer.offset)
    }

    set usage(value: bigint) {
        this.buffer.buf.setBigInt64(4 + this.buffer.offset, value)
    }

    encodeToJson() {
        return {
            username: this.username,
            usage: this.usage,
        };
    }

    static create(
        allocator: BinaryAllocator,
        username: string,
        usage: bigint,
    ): JobUsageByUserDataPoint {
        const result = allocator.allocate(JobUsageByUserDataPointCompanion);
        result._username = allocator.allocateText(username);
        result.usage = usage;
        return result;
    }
}
export const JobUsageByUserDataPointCompanion: BinaryTypeCompanion<JobUsageByUserDataPoint> = {
    size: 12,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let username: string | null = null;
        {
            const valueForJsonDecode = element['username'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'username' to be a string";
            username = valueForJsonDecode;
            if (username === null) throw "Did not expect 'username' to be null!";
        }
        let usage: bigint | null = null;
        {
            const valueForJsonDecode = element['usage'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'usage' to be a bigint";
            usage = valueForJsonDecode;
            if (usage === null) throw "Did not expect 'usage' to be null!";
        }
        return JobUsageByUserDataPoint.create(
            allocator,
            username,
            usage,
        );
    },
    create: (buf) => new JobUsageByUserDataPoint(buf),
};

export class MostUsedApplications implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get categoryIndex(): number {
        return this.buffer.buf.getInt32(0 + this.buffer.offset)
    }

    set categoryIndex(value: number) {
        this.buffer.buf.setInt32(0 + this.buffer.offset, value)
    }

    get dataPoints(): BinaryTypeList<MostUsedApplicationsDataPoint> {
        let result: BinaryTypeList<MostUsedApplicationsDataPoint> | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<MostUsedApplicationsDataPoint>(MostUsedApplicationsDataPointCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set dataPoints(value) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            categoryIndex: this.categoryIndex,
            dataPoints: this.dataPoints?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        categoryIndex: number,
        dataPoints: BinaryTypeList<MostUsedApplicationsDataPoint>,
    ): MostUsedApplications {
        const result = allocator.allocate(MostUsedApplicationsCompanion);
        result.categoryIndex = categoryIndex;
        result.dataPoints = dataPoints;
        return result;
    }
}
export const MostUsedApplicationsCompanion: BinaryTypeCompanion<MostUsedApplications> = {
    size: 8,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let categoryIndex: number | null = null;
        {
            const valueForJsonDecode = element['categoryIndex'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'categoryIndex' to be a number";
            categoryIndex = valueForJsonDecode;
            if (categoryIndex === null) throw "Did not expect 'categoryIndex' to be null!";
        }
        let dataPoints: BinaryTypeList<MostUsedApplicationsDataPoint> | null = null;
        {
            const valueForJsonDecode = element['dataPoints'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'dataPoints' to be an array";
            dataPoints = BinaryTypeList.create(
                MostUsedApplicationsDataPointCompanion,
                allocator,
                valueForJsonDecode.map(it => MostUsedApplicationsDataPointCompanion.decodeFromJson(allocator, it))
            );
            if (dataPoints === null) throw "Did not expect 'dataPoints' to be null!";
        }
        return MostUsedApplications.create(
            allocator,
            categoryIndex,
            dataPoints,
        );
    },
    create: (buf) => new MostUsedApplications(buf),
};

export class MostUsedApplicationsDataPoint implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get _applicationName(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _applicationName(value: UText) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
        }
    }
    get applicationName(): string {
        return this._applicationName?.decode() ?? null;
    }

    get numberOfJobs(): number {
        return this.buffer.buf.getInt32(4 + this.buffer.offset)
    }

    set numberOfJobs(value: number) {
        this.buffer.buf.setInt32(4 + this.buffer.offset, value)
    }

    encodeToJson() {
        return {
            applicationName: this.applicationName,
            numberOfJobs: this.numberOfJobs,
        };
    }

    static create(
        allocator: BinaryAllocator,
        applicationName: string,
        numberOfJobs: number,
    ): MostUsedApplicationsDataPoint {
        const result = allocator.allocate(MostUsedApplicationsDataPointCompanion);
        result._applicationName = allocator.allocateText(applicationName);
        result.numberOfJobs = numberOfJobs;
        return result;
    }
}
export const MostUsedApplicationsDataPointCompanion: BinaryTypeCompanion<MostUsedApplicationsDataPoint> = {
    size: 8,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let applicationName: string | null = null;
        {
            const valueForJsonDecode = element['applicationName'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'applicationName' to be a string";
            applicationName = valueForJsonDecode;
            if (applicationName === null) throw "Did not expect 'applicationName' to be null!";
        }
        let numberOfJobs: number | null = null;
        {
            const valueForJsonDecode = element['numberOfJobs'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'numberOfJobs' to be a number";
            numberOfJobs = valueForJsonDecode;
            if (numberOfJobs === null) throw "Did not expect 'numberOfJobs' to be null!";
        }
        return MostUsedApplicationsDataPoint.create(
            allocator,
            applicationName,
            numberOfJobs,
        );
    },
    create: (buf) => new MostUsedApplicationsDataPoint(buf),
};

export class JobSubmissionStatistics implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get categoryIndex(): number {
        return this.buffer.buf.getInt32(0 + this.buffer.offset)
    }

    set categoryIndex(value: number) {
        this.buffer.buf.setInt32(0 + this.buffer.offset, value)
    }

    get dataPoints(): BinaryTypeList<JobSubmissionStatisticsDataPoint> {
        let result: BinaryTypeList<JobSubmissionStatisticsDataPoint> | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<JobSubmissionStatisticsDataPoint>(JobSubmissionStatisticsDataPointCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set dataPoints(value) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            categoryIndex: this.categoryIndex,
            dataPoints: this.dataPoints?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        categoryIndex: number,
        dataPoints: BinaryTypeList<JobSubmissionStatisticsDataPoint>,
    ): JobSubmissionStatistics {
        const result = allocator.allocate(JobSubmissionStatisticsCompanion);
        result.categoryIndex = categoryIndex;
        result.dataPoints = dataPoints;
        return result;
    }
}
export const JobSubmissionStatisticsCompanion: BinaryTypeCompanion<JobSubmissionStatistics> = {
    size: 8,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let categoryIndex: number | null = null;
        {
            const valueForJsonDecode = element['categoryIndex'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'categoryIndex' to be a number";
            categoryIndex = valueForJsonDecode;
            if (categoryIndex === null) throw "Did not expect 'categoryIndex' to be null!";
        }
        let dataPoints: BinaryTypeList<JobSubmissionStatisticsDataPoint> | null = null;
        {
            const valueForJsonDecode = element['dataPoints'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'dataPoints' to be an array";
            dataPoints = BinaryTypeList.create(
                JobSubmissionStatisticsDataPointCompanion,
                allocator,
                valueForJsonDecode.map(it => JobSubmissionStatisticsDataPointCompanion.decodeFromJson(allocator, it))
            );
            if (dataPoints === null) throw "Did not expect 'dataPoints' to be null!";
        }
        return JobSubmissionStatistics.create(
            allocator,
            categoryIndex,
            dataPoints,
        );
    },
    create: (buf) => new JobSubmissionStatistics(buf),
};

export class JobSubmissionStatisticsDataPoint implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get day(): number {
        return this.buffer.buf.getInt8(0 + this.buffer.offset)
    }

    set day(value: number) {
        this.buffer.buf.setInt8(0 + this.buffer.offset, value)
    }

    get hourOfDayStart(): number {
        return this.buffer.buf.getInt8(1 + this.buffer.offset)
    }

    set hourOfDayStart(value: number) {
        this.buffer.buf.setInt8(1 + this.buffer.offset, value)
    }

    get hourOfDayEnd(): number {
        return this.buffer.buf.getInt8(2 + this.buffer.offset)
    }

    set hourOfDayEnd(value: number) {
        this.buffer.buf.setInt8(2 + this.buffer.offset, value)
    }

    get reserved1(): number {
        return this.buffer.buf.getInt8(3 + this.buffer.offset)
    }

    set reserved1(value: number) {
        this.buffer.buf.setInt8(3 + this.buffer.offset, value)
    }

    get numberOfJobs(): number {
        return this.buffer.buf.getInt32(4 + this.buffer.offset)
    }

    set numberOfJobs(value: number) {
        this.buffer.buf.setInt32(4 + this.buffer.offset, value)
    }

    get averageDurationInSeconds(): number {
        return this.buffer.buf.getInt32(8 + this.buffer.offset)
    }

    set averageDurationInSeconds(value: number) {
        this.buffer.buf.setInt32(8 + this.buffer.offset, value)
    }

    get averageQueueInSeconds(): number {
        return this.buffer.buf.getInt32(12 + this.buffer.offset)
    }

    set averageQueueInSeconds(value: number) {
        this.buffer.buf.setInt32(12 + this.buffer.offset, value)
    }

    encodeToJson() {
        return {
            day: this.day,
            hourOfDayStart: this.hourOfDayStart,
            hourOfDayEnd: this.hourOfDayEnd,
            reserved1: this.reserved1,
            numberOfJobs: this.numberOfJobs,
            averageDurationInSeconds: this.averageDurationInSeconds,
            averageQueueInSeconds: this.averageQueueInSeconds,
        };
    }

    static create(
        allocator: BinaryAllocator,
        day: number,
        hourOfDayStart: number,
        hourOfDayEnd: number,
        reserved1: number,
        numberOfJobs: number,
        averageDurationInSeconds: number,
        averageQueueInSeconds: number,
    ): JobSubmissionStatisticsDataPoint {
        const result = allocator.allocate(JobSubmissionStatisticsDataPointCompanion);
        result.day = day;
        result.hourOfDayStart = hourOfDayStart;
        result.hourOfDayEnd = hourOfDayEnd;
        result.reserved1 = reserved1;
        result.numberOfJobs = numberOfJobs;
        result.averageDurationInSeconds = averageDurationInSeconds;
        result.averageQueueInSeconds = averageQueueInSeconds;
        return result;
    }
}
export const JobSubmissionStatisticsDataPointCompanion: BinaryTypeCompanion<JobSubmissionStatisticsDataPoint> = {
    size: 16,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let day: number | null = null;
        {
            const valueForJsonDecode = element['day'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'day' to be a number";
            day = valueForJsonDecode;
            if (day === null) throw "Did not expect 'day' to be null!";
        }
        let hourOfDayStart: number | null = null;
        {
            const valueForJsonDecode = element['hourOfDayStart'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'hourOfDayStart' to be a number";
            hourOfDayStart = valueForJsonDecode;
            if (hourOfDayStart === null) throw "Did not expect 'hourOfDayStart' to be null!";
        }
        let hourOfDayEnd: number | null = null;
        {
            const valueForJsonDecode = element['hourOfDayEnd'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'hourOfDayEnd' to be a number";
            hourOfDayEnd = valueForJsonDecode;
            if (hourOfDayEnd === null) throw "Did not expect 'hourOfDayEnd' to be null!";
        }
        let reserved1: number | null = null;
        {
            const valueForJsonDecode = element['reserved1'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'reserved1' to be a number";
            reserved1 = valueForJsonDecode;
            if (reserved1 === null) throw "Did not expect 'reserved1' to be null!";
        }
        let numberOfJobs: number | null = null;
        {
            const valueForJsonDecode = element['numberOfJobs'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'numberOfJobs' to be a number";
            numberOfJobs = valueForJsonDecode;
            if (numberOfJobs === null) throw "Did not expect 'numberOfJobs' to be null!";
        }
        let averageDurationInSeconds: number | null = null;
        {
            const valueForJsonDecode = element['averageDurationInSeconds'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'averageDurationInSeconds' to be a number";
            averageDurationInSeconds = valueForJsonDecode;
            if (averageDurationInSeconds === null) throw "Did not expect 'averageDurationInSeconds' to be null!";
        }
        let averageQueueInSeconds: number | null = null;
        {
            const valueForJsonDecode = element['averageQueueInSeconds'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'averageQueueInSeconds' to be a number";
            averageQueueInSeconds = valueForJsonDecode;
            if (averageQueueInSeconds === null) throw "Did not expect 'averageQueueInSeconds' to be null!";
        }
        return JobSubmissionStatisticsDataPoint.create(
            allocator,
            day,
            hourOfDayStart,
            hourOfDayEnd,
            reserved1,
            numberOfJobs,
            averageDurationInSeconds,
            averageQueueInSeconds,
        );
    },
    create: (buf) => new JobSubmissionStatisticsDataPoint(buf),
};
