import type {Allocation, AllocationGroup, AllocationGroupWithChild, ProductCategoryV2, ProductType, WalletV2} from "@/Accounting";
import type {ProjectInfo} from "@/Project/InfoCache";

const DAY_MS = 24 * 60 * 60 * 1000;
const NOW = Date.now();

interface DemoWorkspace {
    projectId?: string;
    title: string;
    pi: string;
}

type DemoPeriod = "active" | "expiring" | "upcoming" | "expired";

interface DemoAllocationSpec {
    quota: number;
    usage: number;
    period: DemoPeriod;
    grantId: number;
}

interface DemoChildSpec {
    workspace: DemoWorkspace;
    allocations: DemoAllocationSpec[];
}

interface DemoProductSpec {
    category: ProductCategoryV2;
    parentQuota: number;
    parentUsage: number;
    parentGrantId: number;
    children: DemoChildSpec[];
}

function shiftMonths(months: number): number {
    const date = new Date(NOW);
    date.setMonth(date.getMonth() + months);
    return date.getTime();
}

function shiftDays(days: number): number {
    return NOW + days * DAY_MS;
}

function periodFor(period: DemoPeriod): {start: number; end: number} {
    switch (period) {
        case "active":
            return {start: shiftMonths(-4), end: shiftMonths(4)};
        case "expiring":
            return {start: shiftMonths(-6), end: shiftDays(20)};
        case "upcoming":
            return {start: shiftMonths(2), end: shiftMonths(8)};
        case "expired":
            return {start: shiftMonths(-10), end: shiftMonths(-2)};
    }
}

function demoCategory(name: string, provider: string, productType: ProductType, unit: string): ProductCategoryV2 {
    return {
        name,
        provider,
        productType,
        accountingUnit: {
            name: unit,
            namePlural: unit,
            floatingPoint: false,
            displayFrequencySuffix: false,
        },
        accountingFrequency: productType === "COMPUTE" ? "PERIODIC_HOUR" : "ONCE",
        freeToUse: false,
    };
}

const K8S_PROVIDER = "k8";
const ODENSE_PROVIDER = "sdu-odense";

const ZEN5 = demoCategory("cpu-amd-zen5", K8S_PROVIDER, "COMPUTE", "CPU-h");
const B200 = demoCategory("gpu-nvidia-b200", K8S_PROVIDER, "COMPUTE", "GPU-h");
const B300 = demoCategory("gpu-nvidia-b300", K8S_PROVIDER, "COMPUTE", "GPU-h");
const K8S_STORAGE = demoCategory("storage", K8S_PROVIDER, "STORAGE", "GB");
const INFERENCE = demoCategory("inference", K8S_PROVIDER, "INFERENCE", "Credits");
const ZEN4 = demoCategory("cpu-amd-zen4", ODENSE_PROVIDER, "COMPUTE", "CPU-h");
const H100 = demoCategory("gpu-nvidia-h100", ODENSE_PROVIDER, "COMPUTE", "GPU-h");
const ODENSE_STORAGE = demoCategory("storage", ODENSE_PROVIDER, "STORAGE", "GB");

const ALPHAFOLD: DemoWorkspace = {
    projectId: "demo-alphafold",
    title: "AlphaFold Lab",
    pi: "AnnaJensen#1234",
};

const GENOME: DemoWorkspace = {
    projectId: "demo-genome",
    title: "Genome Pipeline",
    pi: "MikkelSørensen#2345",
};

const NLP: DemoWorkspace = {
    projectId: "demo-nlp",
    title: "NLP Research",
    pi: "SofiaNielsen#3456",
};

const CLIMATE: DemoWorkspace = {
    projectId: "demo-climate",
    title: "Climate Modelling",
    pi: "LarsAndersen#4567",
};

const MATERIALS: DemoWorkspace = {
    projectId: "demo-materials",
    title: "Materials Discovery",
    pi: "IngridLarsen#5678",
};

const DEV_SANDBOX: DemoWorkspace = {
    title: "Dev Sandbox",
    pi: "DevSandbox#6789",
};

const DEMO_PRODUCTS: DemoProductSpec[] = [
    {
        category: ZEN5,
        parentQuota: 124_000,
        parentUsage: 86_200,
        parentGrantId: 3101,
        children: [
            {
                workspace: ALPHAFOLD,
                allocations: [{quota: 24_000, usage: 18_200, period: "active", grantId: 4101}],
            },
            {
                workspace: GENOME,
                allocations: [{quota: 16_000, usage: 12_400, period: "active", grantId: 4102}],
            },
            {
                workspace: CLIMATE,
                allocations: [{quota: 8_000, usage: 7_600, period: "expiring", grantId: 4103}],
            },
            {
                workspace: DEV_SANDBOX,
                allocations: [{quota: 200, usage: 40, period: "active", grantId: 4104}],
            },
        ],
    },
    {
        category: B200,
        parentQuota: 42_800,
        parentUsage: 31_200,
        parentGrantId: 3201,
        children: [
            {
                workspace: ALPHAFOLD,
                allocations: [{quota: 2_400, usage: 1_820, period: "active", grantId: 4201}],
            },
            {
                workspace: GENOME,
                allocations: [
                    {quota: 1_200, usage: 1_150, period: "expiring", grantId: 4202},
                    {quota: 500, usage: 0, period: "upcoming", grantId: 4203},
                ],
            },
            {
                workspace: NLP,
                allocations: [{quota: 800, usage: 520, period: "active", grantId: 4204}],
            },
        ],
    },
    {
        category: B300,
        parentQuota: 18_400,
        parentUsage: 12_900,
        parentGrantId: 3301,
        children: [
            {
                workspace: MATERIALS,
                allocations: [{quota: 900, usage: 950, period: "active", grantId: 4301}],
            },
            {
                workspace: ALPHAFOLD,
                allocations: [{quota: 1_500, usage: 1_200, period: "active", grantId: 4302}],
            },
            {
                workspace: CLIMATE,
                allocations: [{quota: 600, usage: 0, period: "upcoming", grantId: 4303}],
            },
        ],
    },
    {
        category: K8S_STORAGE,
        parentQuota: 860_000,
        parentUsage: 612_000,
        parentGrantId: 3401,
        children: [
            {
                workspace: ALPHAFOLD,
                allocations: [{quota: 5_000, usage: 3_400, period: "active", grantId: 4401}],
            },
            {
                workspace: GENOME,
                allocations: [{quota: 12_000, usage: 9_800, period: "expiring", grantId: 4402}],
            },
            {
                workspace: NLP,
                allocations: [{quota: 2_000, usage: 900, period: "active", grantId: 4403}],
            },
            {
                workspace: MATERIALS,
                allocations: [{quota: 8_000, usage: 7_200, period: "active", grantId: 4404}],
            },
        ],
    },
    {
        category: INFERENCE,
        parentQuota: 12_000,
        parentUsage: 8_450,
        parentGrantId: 3501,
        children: [
            {
                workspace: MATERIALS,
                allocations: [{quota: 500, usage: 120, period: "active", grantId: 4501}],
            },
            {
                workspace: NLP,
                allocations: [{quota: 300, usage: 300, period: "active", grantId: 4502}],
            },
            {
                workspace: GENOME,
                allocations: [{quota: 1_000, usage: 750, period: "active", grantId: 4503}],
            },
        ],
    },
    {
        category: ZEN4,
        parentQuota: 76_000,
        parentUsage: 48_300,
        parentGrantId: 3601,
        children: [
            {
                workspace: CLIMATE,
                allocations: [{quota: 24_000, usage: 18_000, period: "active", grantId: 4601}],
            },
            {
                workspace: NLP,
                allocations: [{quota: 8_000, usage: 6_800, period: "expiring", grantId: 4602}],
            },
            {
                workspace: MATERIALS,
                allocations: [{quota: 4_000, usage: 4_000, period: "expired", grantId: 4603}],
            },
        ],
    },
    {
        category: H100,
        parentQuota: 24_600,
        parentUsage: 19_800,
        parentGrantId: 3701,
        children: [
            {
                workspace: NLP,
                allocations: [{quota: 800, usage: 700, period: "active", grantId: 4701}],
            },
            {
                workspace: CLIMATE,
                allocations: [{quota: 1_600, usage: 1_450, period: "active", grantId: 4702}],
            },
            {
                workspace: GENOME,
                allocations: [{quota: 600, usage: 600, period: "expired", grantId: 4703}],
            },
        ],
    },
    {
        category: ODENSE_STORAGE,
        parentQuota: 420_000,
        parentUsage: 301_000,
        parentGrantId: 3801,
        children: [
            {
                workspace: CLIMATE,
                allocations: [{quota: 40_000, usage: 22_000, period: "active", grantId: 4801}],
            },
            {
                workspace: MATERIALS,
                allocations: [{quota: 12_000, usage: 9_500, period: "active", grantId: 4802}],
            },
            {
                workspace: NLP,
                allocations: [{quota: 6_000, usage: 5_700, period: "expiring", grantId: 4803}],
            },
            {
                workspace: DEV_SANDBOX,
                allocations: [{quota: 500, usage: 120, period: "active", grantId: 4804}],
            },
        ],
    },
];

export const demoProjectInfo: Record<string, ProjectInfo | null> = {
    "demo-alphafold": {id: "demo-alphafold", title: ALPHAFOLD.title, piUsername: ALPHAFOLD.pi},
    "demo-genome": {id: "demo-genome", title: GENOME.title, piUsername: GENOME.pi},
    "demo-nlp": {id: "demo-nlp", title: NLP.title, piUsername: NLP.pi},
    "demo-climate": {id: "demo-climate", title: CLIMATE.title, piUsername: CLIMATE.pi},
    "demo-materials": {id: "demo-materials", title: MATERIALS.title, piUsername: MATERIALS.pi},
};

export function buildDemoWallets(): WalletV2[] {
    let allocationId = 900_000;
    let parentAllocationId = 800_000;

    return DEMO_PRODUCTS.map(spec => {
        const children: AllocationGroupWithChild[] = spec.children.map(childSpec => {
            const built = childSpec.allocations.map(allocation => {
                const period = periodFor(allocation.period);
                const row: Allocation = {
                    id: allocationId++,
                    startDate: period.start,
                    endDate: period.end,
                    quota: allocation.quota,
                    grantedIn: allocation.grantId,
                    retiredUsage: 0,
                };
                return {spec: allocation, period, row};
            });

            const active = built.filter(it => it.period.start <= NOW && it.period.end >= NOW);
            const totalQuota = built.reduce((sum, it) => sum + it.spec.quota, 0);
            const totalUsage = built.reduce((sum, it) => sum + it.spec.usage, 0);
            const activeQuota = active.reduce((sum, it) => sum + it.spec.quota, 0);
            const activeUsage = active.reduce((sum, it) => sum + it.spec.usage, 0);

            const group: AllocationGroup = {
                usage: totalUsage,
                quota: totalQuota,
                activeQuota,
                activeUsage,
                allocations: built.map(it => it.row),
            };

            return {
                child: {
                    projectId: childSpec.workspace.projectId,
                    projectTitle: childSpec.workspace.title,
                    pi: childSpec.workspace.pi,
                },
                group,
            };
        });

        const parentGroup: AllocationGroup = {
            usage: spec.parentUsage,
            quota: spec.parentQuota,
            activeQuota: spec.parentQuota,
            activeUsage: spec.parentUsage,
            allocations: [{
                id: parentAllocationId++,
                startDate: shiftMonths(-6),
                endDate: shiftMonths(6),
                quota: spec.parentQuota,
                grantedIn: undefined,
                retiredUsage: 0,
            }],
        };

        return {
            owner: {type: "project", projectId: "demo-project"},
            paysFor: spec.category,
            allocationGroups: [{group: parentGroup}],
            children,
            totalUsage: spec.parentUsage,
            localUsage: spec.parentUsage,
            maxUsable: Math.max(0, spec.parentQuota - spec.parentUsage),
            quota: spec.parentQuota,
            totalAllocated: children.reduce((sum, child) => sum + (child.group.activeQuota ?? 0), 0),
            activeUsage: spec.parentUsage,
            activeQuota: spec.parentQuota,
        };
    });
}
