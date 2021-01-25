import * as UCloud from "UCloud";

export const browsePage: UCloud.PageV2<UCloud.compute.Job> = {
    itemsPerPage: 50,
    items: [
        {
            id: "2cc8edc6-0e58-423c-93cd-bd20f241fd87",
            owner: {
                createdBy: "user",
                project: undefined
            },
            updates: [
                {
                    timestamp: 1606809728000,
                    state: "SUCCESS",
                    status: undefined
                }
            ],
            billing: {
                creditsCharged: 8,
                pricePerUnit: 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                name: undefined,
                replicas: 1,
                allowDuplicateJob: true,
                parameters: undefined,
                resources: undefined,
                timeAllocation: undefined,
                resolvedProduct: undefined,
                resolvedApplication: undefined
            },
            output: undefined,
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0,
        },
        {
            id: "a4f35d51-40bf-496f-bcd9-5813cb5f91db",
            owner: {
                createdBy: "user"
            },
            updates: [
                {
                    timestamp: 1606809944000,
                    state: "FAILURE"
                }
            ],
            billing: {
                creditsCharged: 0,
                pricePerUnit: 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                name: undefined,
                replicas: 1,
                allowDuplicateJob: true,
            },
            output: undefined,
            status: {
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "812cf11a-3a16-4a98-80ce-763613cf560b",
            owner: {
                createdBy: "user",
                project: undefined
            },
            updates: [
                {
                    timestamp: 1606809998000,
                    state: "SUCCESS",
                    status: undefined
                }
            ],
            billing: {
                creditsCharged: 2,
                pricePerUnit: 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "FAILURE"
            },
            createdAt: 0
        },
        {
            id: "a2cfcd0a-e24b-443c-bc47-a05fe748530f",
            owner: {
                createdBy: "user"
            },
            "updates": [
                {
                    "timestamp": 1606810527000,
                    "state": "FAILURE"
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "SUCCESS"
            },
            createdAt: 0
        },
        {
            id: "245cd7ba-ec9e-499f-8da0-a6d41c118387",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606810591000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                state: "IN_QUEUE"
            },
            createdAt: 0
        },
        {
            id: "51ef3ada-6578-4e3f-aff2-c5591dfe5f36",
            "owner": {
                "createdBy": "user"
            },
            "updates": [
                {
                    "timestamp": 1606811916000,
                    "state": "FAILURE"
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
            status: {
                startedAt: new Date().getTime() - 10_000,
                state: "IN_QUEUE"
            },
            createdAt: 0
        },
        {
            id: "16f0b2bc-db9b-468e-aadf-d6657cfa2494",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606811964000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "CANCELING"
            },
            createdAt: 0
        },
        {
            id: "bd562fdb-249a-43b9-909d-96279bf96d7b",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606812024000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "11d45177-c2c0-4254-9068-03f8ad2cdfeb",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606812666000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 11,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "ad88b9f5-6bc9-40b4-b6be-bde7de38708e",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606830470000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "12a2717f-dd4c-4d24-a948-fe00cadc51e6",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606830635000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "c7dccff3-ebd6-4ce8-95c8-1a85ed8b56d2",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606832894000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "2b691748-9ad1-4ed3-bbe3-2f651245a041",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606833408000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 7,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "94606ccd-af7f-43ce-b3bf-95e8858f12a1",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606834227000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 3,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "aab92994-bed7-43f9-9ef4-cb9a7f60715b",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606834506000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 3,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "9ce5332c-c52e-4b0a-b935-c5bd9a73f1d3",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606834715000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 12,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "2"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "383f7238-9e70-4a8d-90a4-db99967300ab",
            owner: {
                createdBy: "user",
            },
            updates: [
                {
                    timestamp: 1606834732000,
                    state: "SUCCESS",
                }
            ],
            billing: {
                creditsCharged: 1,
                pricePerUnit: 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "4f7f4075-1d15-42f0-8e3b-09450b514f9f",
            owner: {
                createdBy: "user"
            },
            updates: [
                {
                    timestamp: 1606898310000,
                    state: "SUCCESS"
                }
            ],
            billing: {
                creditsCharged: 24,
                pricePerUnit: 1
            },
            specification: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "127d2123-9520-4c41-813c-2d808b2746da",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606902107000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 62,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "coder",
                    version: "1.48.2"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        },
        {
            id: "83fb8069-6a0e-4d7b-9910-a9e8c63624a5",
            "owner": {
                "createdBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606902764000,
                    "state": "RUNNING",
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            specification: {
                application: {
                    name: "coder",
                    version: "1.48.2"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true
            },
            status: {
                expiresAt: new Date().getTime() + 30_000,
                startedAt: new Date().getTime() - 10_000,
                state: "RUNNING"
            },
            createdAt: 0
        }
    ],
    "next": "20-cafbbfef-265d-4995-8bad-e44212541a76"
}