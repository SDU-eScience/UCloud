// GENERATED CODE - DO NOT MODIFY - See AccountingBinary.msg
// GENERATED CODE - DO NOT MODIFY - See AccountingBinary.msg
// GENERATED CODE - DO NOT MODIFY - See AccountingBinary.msg

import { BinaryAllocator, UBinaryType, BinaryTypeCompanion, UText, BufferAndOffset, BinaryTypeList } from "@/UCloud/Messages";

export enum ProductTypeB {
    STORAGE,
    COMPUTE,
    LICENSE,
    INGRESS,
    NETWORK_IP,
}

export const ProductTypeBCompanion = {
    name(element: ProductTypeB): string {
        switch (element) {
            case ProductTypeB.STORAGE: return "STORAGE";
            case ProductTypeB.COMPUTE: return "COMPUTE";
            case ProductTypeB.LICENSE: return "LICENSE";
            case ProductTypeB.INGRESS: return "INGRESS";
            case ProductTypeB.NETWORK_IP: return "NETWORK_IP";
        }
    },

    serialName(element: ProductTypeB): string {
        switch (element) {
            case ProductTypeB.STORAGE: return "STORAGE";
            case ProductTypeB.COMPUTE: return "COMPUTE";
            case ProductTypeB.LICENSE: return "LICENSE";
            case ProductTypeB.INGRESS: return "INGRESS";
            case ProductTypeB.NETWORK_IP: return "NETWORK_IP";
        }
    },

    encoded(element: ProductTypeB): number {
        switch (element) {
            case ProductTypeB.STORAGE: return 1;
            case ProductTypeB.COMPUTE: return 2;
            case ProductTypeB.LICENSE: return 3;
            case ProductTypeB.INGRESS: return 4;
            case ProductTypeB.NETWORK_IP: return 5;
        }
    },

    fromSerialName(name: string): ProductTypeB | null {
        switch (name) {
            case "STORAGE": return ProductTypeB.STORAGE;
            case "COMPUTE": return ProductTypeB.COMPUTE;
            case "LICENSE": return ProductTypeB.LICENSE;
            case "INGRESS": return ProductTypeB.INGRESS;
            case "NETWORK_IP": return ProductTypeB.NETWORK_IP;
            default: return null;
        }
    },

    fromEncoded(encoded: number): ProductTypeB | null {
        switch (encoded) {
            case 1: return ProductTypeB.STORAGE;
            case 2: return ProductTypeB.COMPUTE;
            case 3: return ProductTypeB.LICENSE;
            case 4: return ProductTypeB.INGRESS;
            case 5: return ProductTypeB.NETWORK_IP;
            default: return null;
        }
    },
};

export enum AccountingFrequencyB {
    ONCE,
    PERIODIC_MINUTE,
    PERIODIC_HOUR,
    PERIODIC_DAY,
}

export const AccountingFrequencyBCompanion = {
    name(element: AccountingFrequencyB): string {
        switch (element) {
            case AccountingFrequencyB.ONCE: return "ONCE";
            case AccountingFrequencyB.PERIODIC_MINUTE: return "PERIODIC_MINUTE";
            case AccountingFrequencyB.PERIODIC_HOUR: return "PERIODIC_HOUR";
            case AccountingFrequencyB.PERIODIC_DAY: return "PERIODIC_DAY";
        }
    },

    serialName(element: AccountingFrequencyB): string {
        switch (element) {
            case AccountingFrequencyB.ONCE: return "ONCE";
            case AccountingFrequencyB.PERIODIC_MINUTE: return "PERIODIC_MINUTE";
            case AccountingFrequencyB.PERIODIC_HOUR: return "PERIODIC_HOUR";
            case AccountingFrequencyB.PERIODIC_DAY: return "PERIODIC_DAY";
        }
    },

    encoded(element: AccountingFrequencyB): number {
        switch (element) {
            case AccountingFrequencyB.ONCE: return 1;
            case AccountingFrequencyB.PERIODIC_MINUTE: return 2;
            case AccountingFrequencyB.PERIODIC_HOUR: return 3;
            case AccountingFrequencyB.PERIODIC_DAY: return 4;
        }
    },

    fromSerialName(name: string): AccountingFrequencyB | null {
        switch (name) {
            case "ONCE": return AccountingFrequencyB.ONCE;
            case "PERIODIC_MINUTE": return AccountingFrequencyB.PERIODIC_MINUTE;
            case "PERIODIC_HOUR": return AccountingFrequencyB.PERIODIC_HOUR;
            case "PERIODIC_DAY": return AccountingFrequencyB.PERIODIC_DAY;
            default: return null;
        }
    },

    fromEncoded(encoded: number): AccountingFrequencyB | null {
        switch (encoded) {
            case 1: return AccountingFrequencyB.ONCE;
            case 2: return AccountingFrequencyB.PERIODIC_MINUTE;
            case 3: return AccountingFrequencyB.PERIODIC_HOUR;
            case 4: return AccountingFrequencyB.PERIODIC_DAY;
            default: return null;
        }
    },
};

export class AccountingUnitB implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get _name(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _name(value: UText) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
        }
    }
    get name(): string {
        return this._name?.decode() ?? null;
    }

    get _namePlural(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _namePlural(value: UText) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
        }
    }
    get namePlural(): string {
        return this._namePlural?.decode() ?? null;
    }

    get floatingPoint(): boolean {
        return this.buffer.buf.getInt8(8 + this.buffer.offset) == 1
    }

    set floatingPoint(value: boolean) {
        this.buffer.buf.setInt8(8 + this.buffer.offset, value ? 1 : 0)
    }

    get displayFrequencySuffix(): boolean {
        return this.buffer.buf.getInt8(9 + this.buffer.offset) == 1
    }

    set displayFrequencySuffix(value: boolean) {
        this.buffer.buf.setInt8(9 + this.buffer.offset, value ? 1 : 0)
    }

    encodeToJson() {
        return {
            name: this.name,
            namePlural: this.namePlural,
            floatingPoint: this.floatingPoint,
            displayFrequencySuffix: this.displayFrequencySuffix,
        };
    }

    static create(
        allocator: BinaryAllocator,
        name: string,
        namePlural: string,
        floatingPoint: boolean,
        displayFrequencySuffix: boolean,
    ): AccountingUnitB {
        const result = allocator.allocate(AccountingUnitBCompanion);
        result._name = allocator.allocateText(name);
        result._namePlural = allocator.allocateText(namePlural);
        result.floatingPoint = floatingPoint;
        result.displayFrequencySuffix = displayFrequencySuffix;
        return result;
    }
}
export const AccountingUnitBCompanion: BinaryTypeCompanion<AccountingUnitB> = {
    size: 10,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let name: string | null = null;
        {
            const valueForJsonDecode = element['name'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'name' to be a string";
            name = valueForJsonDecode;
            if (name === null) throw "Did not expect 'name' to be null!";
        }
        let namePlural: string | null = null;
        {
            const valueForJsonDecode = element['namePlural'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'namePlural' to be a string";
            namePlural = valueForJsonDecode;
            if (namePlural === null) throw "Did not expect 'namePlural' to be null!";
        }
        let floatingPoint: boolean | null = null;
        {
            const valueForJsonDecode = element['floatingPoint'];
            if (typeof valueForJsonDecode !== 'boolean') throw "Expected 'floatingPoint' to be a boolean";
            floatingPoint = valueForJsonDecode;
            if (floatingPoint === null) throw "Did not expect 'floatingPoint' to be null!";
        }
        let displayFrequencySuffix: boolean | null = null;
        {
            const valueForJsonDecode = element['displayFrequencySuffix'];
            if (typeof valueForJsonDecode !== 'boolean') throw "Expected 'displayFrequencySuffix' to be a boolean";
            displayFrequencySuffix = valueForJsonDecode;
            if (displayFrequencySuffix === null) throw "Did not expect 'displayFrequencySuffix' to be null!";
        }
        return AccountingUnitB.create(
            allocator,
            name,
            namePlural,
            floatingPoint,
            displayFrequencySuffix,
        );
    },
    create: (buf) => new AccountingUnitB(buf),
};

export class ProductCategoryB implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get _name(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _name(value: UText) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
        }
    }
    get name(): string {
        return this._name?.decode() ?? null;
    }

    get _provider(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _provider(value: UText) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
        }
    }
    get provider(): string {
        return this._provider?.decode() ?? null;
    }

    get productType(): ProductTypeB {
        return ProductTypeBCompanion.fromEncoded(this.buffer.buf.getInt16(8 + this.buffer.offset))!;
    }
    set productType(value: ProductTypeB) {
        this.buffer.buf.setInt16(8 + this.buffer.offset!, ProductTypeBCompanion.encoded(value));
    }


    get accountingUnit(): AccountingUnitB {
        let result: AccountingUnitB | null = null;
        const ptr = this.buffer.buf.getInt32(10 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new AccountingUnitB(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set accountingUnit(value: AccountingUnitB) {
        if (value === null) this.buffer.buf.setInt32(10 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(10 + this.buffer.offset, value.buffer.offset);
        }
    }

    get accountingFrequency(): AccountingFrequencyB {
        return AccountingFrequencyBCompanion.fromEncoded(this.buffer.buf.getInt16(14 + this.buffer.offset))!;
    }
    set accountingFrequency(value: AccountingFrequencyB) {
        this.buffer.buf.setInt16(14 + this.buffer.offset!, AccountingFrequencyBCompanion.encoded(value));
    }


    get freeToUse(): boolean {
        return this.buffer.buf.getInt8(16 + this.buffer.offset) == 1
    }

    set freeToUse(value: boolean) {
        this.buffer.buf.setInt8(16 + this.buffer.offset, value ? 1 : 0)
    }

    encodeToJson() {
        return {
            name: this.name,
            provider: this.provider,
            productType: this.productType != null ? ProductTypeBCompanion.encoded(this.productType) : null,
            accountingUnit: this.accountingUnit?.encodeToJson() ?? null,
            accountingFrequency: this.accountingFrequency != null ? AccountingFrequencyBCompanion.encoded(this.accountingFrequency) : null,
            freeToUse: this.freeToUse,
        };
    }

    static create(
        allocator: BinaryAllocator,
        name: string,
        provider: string,
        productType: ProductTypeB,
        accountingUnit: AccountingUnitB,
        accountingFrequency: AccountingFrequencyB,
        freeToUse: boolean,
    ): ProductCategoryB {
        const result = allocator.allocate(ProductCategoryBCompanion);
        result._name = allocator.allocateText(name);
        result._provider = allocator.allocateText(provider);
        result.productType = productType;
        result.accountingUnit = accountingUnit;
        result.accountingFrequency = accountingFrequency;
        result.freeToUse = freeToUse;
        return result;
    }
}
export const ProductCategoryBCompanion: BinaryTypeCompanion<ProductCategoryB> = {
    size: 17,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let name: string | null = null;
        {
            const valueForJsonDecode = element['name'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'name' to be a string";
            name = valueForJsonDecode;
            if (name === null) throw "Did not expect 'name' to be null!";
        }
        let provider: string | null = null;
        {
            const valueForJsonDecode = element['provider'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'provider' to be a string";
            provider = valueForJsonDecode;
            if (provider === null) throw "Did not expect 'provider' to be null!";
        }
        let productType: ProductTypeB | null = null;
        {
            const valueForJsonDecode = element['productType'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'productType' to be a string";
            productType = ProductTypeBCompanion.fromSerialName(valueForJsonDecode);
            if (productType === null) throw "Did not expect 'productType' to be null!";
        }
        let accountingUnit: AccountingUnitB | null = null;
        {
            const valueForJsonDecode = element['accountingUnit'];
            if (typeof valueForJsonDecode !== 'object') throw "Expected 'accountingUnit' to be an object";
            accountingUnit = AccountingUnitBCompanion.decodeFromJson(allocator, valueForJsonDecode);
            if (accountingUnit === null) throw "Did not expect 'accountingUnit' to be null!";
        }
        let accountingFrequency: AccountingFrequencyB | null = null;
        {
            const valueForJsonDecode = element['accountingFrequency'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'accountingFrequency' to be a string";
            accountingFrequency = AccountingFrequencyBCompanion.fromSerialName(valueForJsonDecode);
            if (accountingFrequency === null) throw "Did not expect 'accountingFrequency' to be null!";
        }
        let freeToUse: boolean | null = null;
        {
            const valueForJsonDecode = element['freeToUse'];
            if (typeof valueForJsonDecode !== 'boolean') throw "Expected 'freeToUse' to be a boolean";
            freeToUse = valueForJsonDecode;
            if (freeToUse === null) throw "Did not expect 'freeToUse' to be null!";
        }
        return ProductCategoryB.create(
            allocator,
            name,
            provider,
            productType,
            accountingUnit,
            accountingFrequency,
            freeToUse,
        );
    },
    create: (buf) => new ProductCategoryB(buf),
};

export class WalletAllocationB implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get id(): bigint {
        return this.buffer.buf.getBigInt64(0 + this.buffer.offset)
    }

    set id(value: bigint) {
        this.buffer.buf.setBigInt64(0 + this.buffer.offset, value)
    }

    get usage(): bigint {
        return this.buffer.buf.getBigInt64(8 + this.buffer.offset)
    }

    set usage(value: bigint) {
        this.buffer.buf.setBigInt64(8 + this.buffer.offset, value)
    }

    get localUsage(): bigint {
        return this.buffer.buf.getBigInt64(16 + this.buffer.offset)
    }

    set localUsage(value: bigint) {
        this.buffer.buf.setBigInt64(16 + this.buffer.offset, value)
    }

    get quota(): bigint {
        return this.buffer.buf.getBigInt64(24 + this.buffer.offset)
    }

    set quota(value: bigint) {
        this.buffer.buf.setBigInt64(24 + this.buffer.offset, value)
    }

    get startDate(): bigint {
        return this.buffer.buf.getBigInt64(32 + this.buffer.offset)
    }

    set startDate(value: bigint) {
        this.buffer.buf.setBigInt64(32 + this.buffer.offset, value)
    }

    get endDate(): bigint {
        return this.buffer.buf.getBigInt64(40 + this.buffer.offset)
    }

    set endDate(value: bigint) {
        this.buffer.buf.setBigInt64(40 + this.buffer.offset, value)
    }

    get categoryIndex(): number {
        return this.buffer.buf.getInt32(48 + this.buffer.offset)
    }

    set categoryIndex(value: number) {
        this.buffer.buf.setInt32(48 + this.buffer.offset, value)
    }

    encodeToJson() {
        return {
            id: this.id,
            usage: this.usage,
            localUsage: this.localUsage,
            quota: this.quota,
            startDate: this.startDate,
            endDate: this.endDate,
            categoryIndex: this.categoryIndex,
        };
    }

    static create(
        allocator: BinaryAllocator,
        id: bigint,
        usage: bigint,
        localUsage: bigint,
        quota: bigint,
        startDate: bigint,
        endDate: bigint,
        categoryIndex: number,
    ): WalletAllocationB {
        const result = allocator.allocate(WalletAllocationBCompanion);
        result.id = id;
        result.usage = usage;
        result.localUsage = localUsage;
        result.quota = quota;
        result.startDate = startDate;
        result.endDate = endDate;
        result.categoryIndex = categoryIndex;
        return result;
    }
}
export const WalletAllocationBCompanion: BinaryTypeCompanion<WalletAllocationB> = {
    size: 52,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let id: bigint | null = null;
        {
            const valueForJsonDecode = element['id'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'id' to be a bigint";
            id = valueForJsonDecode;
            if (id === null) throw "Did not expect 'id' to be null!";
        }
        let usage: bigint | null = null;
        {
            const valueForJsonDecode = element['usage'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'usage' to be a bigint";
            usage = valueForJsonDecode;
            if (usage === null) throw "Did not expect 'usage' to be null!";
        }
        let localUsage: bigint | null = null;
        {
            const valueForJsonDecode = element['localUsage'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'localUsage' to be a bigint";
            localUsage = valueForJsonDecode;
            if (localUsage === null) throw "Did not expect 'localUsage' to be null!";
        }
        let quota: bigint | null = null;
        {
            const valueForJsonDecode = element['quota'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'quota' to be a bigint";
            quota = valueForJsonDecode;
            if (quota === null) throw "Did not expect 'quota' to be null!";
        }
        let startDate: bigint | null = null;
        {
            const valueForJsonDecode = element['startDate'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'startDate' to be a bigint";
            startDate = valueForJsonDecode;
            if (startDate === null) throw "Did not expect 'startDate' to be null!";
        }
        let endDate: bigint | null = null;
        {
            const valueForJsonDecode = element['endDate'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'endDate' to be a bigint";
            endDate = valueForJsonDecode;
            if (endDate === null) throw "Did not expect 'endDate' to be null!";
        }
        let categoryIndex: number | null = null;
        {
            const valueForJsonDecode = element['categoryIndex'];
            if (typeof valueForJsonDecode !== 'number') throw "Expected 'categoryIndex' to be a number";
            categoryIndex = valueForJsonDecode;
            if (categoryIndex === null) throw "Did not expect 'categoryIndex' to be null!";
        }
        return WalletAllocationB.create(
            allocator,
            id,
            usage,
            localUsage,
            quota,
            startDate,
            endDate,
            categoryIndex,
        );
    },
    create: (buf) => new WalletAllocationB(buf),
};

export class UsageOverTimeDataPoint implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get usage(): bigint {
        return this.buffer.buf.getBigInt64(0 + this.buffer.offset)
    }

    set usage(value: bigint) {
        this.buffer.buf.setBigInt64(0 + this.buffer.offset, value)
    }

    get quota(): bigint {
        return this.buffer.buf.getBigInt64(8 + this.buffer.offset)
    }

    set quota(value: bigint) {
        this.buffer.buf.setBigInt64(8 + this.buffer.offset, value)
    }

    get timestamp(): bigint {
        return this.buffer.buf.getBigInt64(16 + this.buffer.offset)
    }

    set timestamp(value: bigint) {
        this.buffer.buf.setBigInt64(16 + this.buffer.offset, value)
    }

    encodeToJson() {
        return {
            usage: this.usage,
            quota: this.quota,
            timestamp: this.timestamp,
        };
    }

    static create(
        allocator: BinaryAllocator,
        usage: bigint,
        quota: bigint,
        timestamp: bigint,
    ): UsageOverTimeDataPoint {
        const result = allocator.allocate(UsageOverTimeDataPointCompanion);
        result.usage = usage;
        result.quota = quota;
        result.timestamp = timestamp;
        return result;
    }
}
export const UsageOverTimeDataPointCompanion: BinaryTypeCompanion<UsageOverTimeDataPoint> = {
    size: 24,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let usage: bigint | null = null;
        {
            const valueForJsonDecode = element['usage'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'usage' to be a bigint";
            usage = valueForJsonDecode;
            if (usage === null) throw "Did not expect 'usage' to be null!";
        }
        let quota: bigint | null = null;
        {
            const valueForJsonDecode = element['quota'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'quota' to be a bigint";
            quota = valueForJsonDecode;
            if (quota === null) throw "Did not expect 'quota' to be null!";
        }
        let timestamp: bigint | null = null;
        {
            const valueForJsonDecode = element['timestamp'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'timestamp' to be a bigint";
            timestamp = valueForJsonDecode;
            if (timestamp === null) throw "Did not expect 'timestamp' to be null!";
        }
        return UsageOverTimeDataPoint.create(
            allocator,
            usage,
            quota,
            timestamp,
        );
    },
    create: (buf) => new UsageOverTimeDataPoint(buf),
};

export class UsageOverTime implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get data(): BinaryTypeList<UsageOverTimeDataPoint> {
        let result: BinaryTypeList<UsageOverTimeDataPoint> | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<UsageOverTimeDataPoint>(UsageOverTimeDataPointCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set data(value) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            data: this.data?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        data: BinaryTypeList<UsageOverTimeDataPoint>,
    ): UsageOverTime {
        const result = allocator.allocate(UsageOverTimeCompanion);
        result.data = data;
        return result;
    }
}
export const UsageOverTimeCompanion: BinaryTypeCompanion<UsageOverTime> = {
    size: 4,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let data: BinaryTypeList<UsageOverTimeDataPoint> | null = null;
        {
            const valueForJsonDecode = element['data'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'data' to be an array";
            data = BinaryTypeList.create(
                UsageOverTimeDataPointCompanion,
                allocator,
                valueForJsonDecode.map(it => UsageOverTimeDataPointCompanion.decodeFromJson(allocator, it))
            );
            if (data === null) throw "Did not expect 'data' to be null!";
        }
        return UsageOverTime.create(
            allocator,
            data,
        );
    },
    create: (buf) => new UsageOverTime(buf),
};

export class BreakdownByProjectPoint implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get _title(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _title(value: UText) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
        }
    }
    get title(): string {
        return this._title?.decode() ?? null;
    }

    get _projectId(): UText | null {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result;
    }
    set _projectId(value: UText | null) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
        }
    }
    get projectId(): string | null {
        return this._projectId?.decode() ?? null;
    }

    get usage(): bigint {
        return this.buffer.buf.getBigInt64(8 + this.buffer.offset)
    }

    set usage(value: bigint) {
        this.buffer.buf.setBigInt64(8 + this.buffer.offset, value)
    }

    encodeToJson() {
        return {
            title: this.title,
            projectId: this.projectId,
            usage: this.usage,
        };
    }

    static create(
        allocator: BinaryAllocator,
        title: string,
        projectId: string | null,
        usage: bigint,
    ): BreakdownByProjectPoint {
        const result = allocator.allocate(BreakdownByProjectPointCompanion);
        result._title = allocator.allocateText(title);
        if (projectId === null) result._projectId = null;
        else result._projectId = allocator.allocateText(projectId);
        result.usage = usage;
        return result;
    }
}
export const BreakdownByProjectPointCompanion: BinaryTypeCompanion<BreakdownByProjectPoint> = {
    size: 16,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let title: string | null = null;
        {
            const valueForJsonDecode = element['title'];
            if (typeof valueForJsonDecode !== 'string') throw "Expected 'title' to be a string";
            title = valueForJsonDecode;
            if (title === null) throw "Did not expect 'title' to be null!";
        }
        let projectId: string | null = null;
        {
            const valueForJsonDecode = element['projectId'];
            if (valueForJsonDecode === null) projectId = null;
            else {
                if (typeof valueForJsonDecode !== 'string') throw "Expected 'projectId' to be a string";
                projectId = valueForJsonDecode;
            }
        }
        let usage: bigint | null = null;
        {
            const valueForJsonDecode = element['usage'];
            if (typeof valueForJsonDecode !== 'bigint') throw "Expected 'usage' to be a bigint";
            usage = valueForJsonDecode;
            if (usage === null) throw "Did not expect 'usage' to be null!";
        }
        return BreakdownByProjectPoint.create(
            allocator,
            title,
            projectId,
            usage,
        );
    },
    create: (buf) => new BreakdownByProjectPoint(buf),
};

export class BreakdownByProject implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get data(): BinaryTypeList<BreakdownByProjectPoint> {
        let result: BinaryTypeList<BreakdownByProjectPoint> | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<BreakdownByProjectPoint>(BreakdownByProjectPointCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set data(value) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            data: this.data?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        data: BinaryTypeList<BreakdownByProjectPoint>,
    ): BreakdownByProject {
        const result = allocator.allocate(BreakdownByProjectCompanion);
        result.data = data;
        return result;
    }
}
export const BreakdownByProjectCompanion: BinaryTypeCompanion<BreakdownByProject> = {
    size: 4,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let data: BinaryTypeList<BreakdownByProjectPoint> | null = null;
        {
            const valueForJsonDecode = element['data'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'data' to be an array";
            data = BinaryTypeList.create(
                BreakdownByProjectPointCompanion,
                allocator,
                valueForJsonDecode.map(it => BreakdownByProjectPointCompanion.decodeFromJson(allocator, it))
            );
            if (data === null) throw "Did not expect 'data' to be null!";
        }
        return BreakdownByProject.create(
            allocator,
            data,
        );
    },
    create: (buf) => new BreakdownByProject(buf),
};

export class Charts implements UBinaryType {
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

    get allocations(): BinaryTypeList<WalletAllocationB> {
        let result: BinaryTypeList<WalletAllocationB> | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<WalletAllocationB>(WalletAllocationBCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set allocations(value) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
    }

    get charts(): BinaryTypeList<ChartsForCategory> {
        let result: BinaryTypeList<ChartsForCategory> | null = null;
        const ptr = this.buffer.buf.getInt32(8 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<ChartsForCategory>(ChartsForCategoryCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set charts(value) {
        if (value === null) this.buffer.buf.setInt32(8 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(8 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            categories: this.categories?.encodeToJson() ?? null,
            allocations: this.allocations?.encodeToJson() ?? null,
            charts: this.charts?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        categories: BinaryTypeList<ProductCategoryB>,
        allocations: BinaryTypeList<WalletAllocationB>,
        charts: BinaryTypeList<ChartsForCategory>,
    ): Charts {
        const result = allocator.allocate(ChartsCompanion);
        result.categories = categories;
        result.allocations = allocations;
        result.charts = charts;
        return result;
    }
}
export const ChartsCompanion: BinaryTypeCompanion<Charts> = {
    size: 12,
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
        let allocations: BinaryTypeList<WalletAllocationB> | null = null;
        {
            const valueForJsonDecode = element['allocations'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'allocations' to be an array";
            allocations = BinaryTypeList.create(
                WalletAllocationBCompanion,
                allocator,
                valueForJsonDecode.map(it => WalletAllocationBCompanion.decodeFromJson(allocator, it))
            );
            if (allocations === null) throw "Did not expect 'allocations' to be null!";
        }
        let charts: BinaryTypeList<ChartsForCategory> | null = null;
        {
            const valueForJsonDecode = element['charts'];
            if (!Array.isArray(valueForJsonDecode)) throw "Expected 'charts' to be an array";
            charts = BinaryTypeList.create(
                ChartsForCategoryCompanion,
                allocator,
                valueForJsonDecode.map(it => ChartsForCategoryCompanion.decodeFromJson(allocator, it))
            );
            if (charts === null) throw "Did not expect 'charts' to be null!";
        }
        return Charts.create(
            allocator,
            categories,
            allocations,
            charts,
        );
    },
    create: (buf) => new Charts(buf),
};

export class ChartsForCategory implements UBinaryType {
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

    get overTime(): UsageOverTime {
        let result: UsageOverTime | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UsageOverTime(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set overTime(value: UsageOverTime) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
        }
    }

    get breakdownByProject(): BreakdownByProject {
        let result: BreakdownByProject | null = null;
        const ptr = this.buffer.buf.getInt32(8 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BreakdownByProject(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set breakdownByProject(value: BreakdownByProject) {
        if (value === null) this.buffer.buf.setInt32(8 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(8 + this.buffer.offset, value.buffer.offset);
        }
    }

    encodeToJson() {
        return {
            categoryIndex: this.categoryIndex,
            overTime: this.overTime?.encodeToJson() ?? null,
            breakdownByProject: this.breakdownByProject?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        categoryIndex: number,
        overTime: UsageOverTime,
        breakdownByProject: BreakdownByProject,
    ): ChartsForCategory {
        const result = allocator.allocate(ChartsForCategoryCompanion);
        result.categoryIndex = categoryIndex;
        result.overTime = overTime;
        result.breakdownByProject = breakdownByProject;
        return result;
    }
}
export const ChartsForCategoryCompanion: BinaryTypeCompanion<ChartsForCategory> = {
    size: 12,
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
        let overTime: UsageOverTime | null = null;
        {
            const valueForJsonDecode = element['overTime'];
            if (typeof valueForJsonDecode !== 'object') throw "Expected 'overTime' to be an object";
            overTime = UsageOverTimeCompanion.decodeFromJson(allocator, valueForJsonDecode);
            if (overTime === null) throw "Did not expect 'overTime' to be null!";
        }
        let breakdownByProject: BreakdownByProject | null = null;
        {
            const valueForJsonDecode = element['breakdownByProject'];
            if (typeof valueForJsonDecode !== 'object') throw "Expected 'breakdownByProject' to be an object";
            breakdownByProject = BreakdownByProjectCompanion.decodeFromJson(allocator, valueForJsonDecode);
            if (breakdownByProject === null) throw "Did not expect 'breakdownByProject' to be null!";
        }
        return ChartsForCategory.create(
            allocator,
            categoryIndex,
            overTime,
            breakdownByProject,
        );
    },
    create: (buf) => new ChartsForCategory(buf),
};
