// FIXME START: Options for dashboard. Should be retrieved from DB

const DashboardOption = {
    name: "Dashboard",
    icon: "nav-icon",
    href: "/dashboard",
    children: null,
};

const FilesOptions = {
    name: "Files",
    icon: "nav-icon",
    href: "/files/",
    children: null
};

const AppsApplicationsOption = {
    name: "Applications",
    icon: "",
    href: "/applications",
    children: null
};

const AppsWorkflowsOption = {
    name: "Workflows",
    icon: "",
    href: "/workflows",
    children: null,
};

const AppsAnalysesOption = {
    name: "Analyses",
    icon: "",
    href: "/analyses",
    children: null,
};

const AppsOptions = {
    name: "Apps",
    icon: "",
    href: "",
    children: [AppsApplicationsOption, AppsWorkflowsOption, AppsAnalysesOption]
};

const ActivityNotificationsOption = {
    name: "Notifications",
    icon: "",
    href: "/notifications",
    children: null,
};

const ActivityOptions = {
    name: "Activity",
    icon: "",
    href: "",
    children: [ActivityNotificationsOption]
};

const SidebarOptionsList = [
    DashboardOption, FilesOptions, AppsOptions, ActivityOptions
];
// FIXME END

// FIXME START GET ACTUAL EVENTS (NOT THE ACTUAL EVENTS)

/*
    User FOO browsed files in x folders.
    At tsFrom:
    Including:
 */

let i = 0;
const EventTypes = {
    upload: i++,
    download: i++,
    file_listing: i++,
    hpc_job: i++,
};

class ActivityCardExample {
    constructor(eventType) {
        this.tsFrom = Math.floor(Math.random() * 1000 * Math.random());
        this.eventtype = eventType;
        this.jobIds = [];
        this.cachedJobs = [];
    }

    addJobId(jobId) { // For debugging purposes
        this.jobIds.push(jobId)
    }

    saveJob(job) {
        this.cachedJobs.push(job);
    }

    getJobFromId(jobId) {
        //Cloud.get()

    }
}

class EventExample {
    constructor(jobId, query, statusCode) {
        this.jobId = jobId;
        this.ts = {
            start: new Date() - Math.floor(Math.random() * 1000),
            duration: new Date() + Math.floor(Math.random() * 1000),
        };
        this.query = query;
        this.statusCode = statusCode;
    }
}

const ActivityCardExample1 = new ActivityCardExample(EventTypes.file_listing);
const EventExample1 = new EventExample("4121-HIGH-1242-MAXX", "/files/path?=/home/", 200);
const EventExample2 = new EventExample("4121-HIGH-HAWK-MAXX", "/files/path?=/home/jonas@hinchely.dk", 200);
const EventExample3 = new EventExample("4121-HIGH-HAWK-IDDQ", "/files/path?=/home/jonashinchely.dk", 500);
const EventExample4 = new EventExample("4121-HIGH-HAWK-IDDQ", "/fies/path?=/home/jonashinchely.dk", 404);
ActivityCardExample1.addJobId(EventExample1.jobId);
ActivityCardExample1.addJobId(EventExample2.jobId);
ActivityCardExample1.addJobId(EventExample3.jobId);
ActivityCardExample1.addJobId(EventExample4.jobId);

const auditingExample = {
    "ts": 0,
    "events": [
        {
            "jobId": "1111-XXXX-1234",
            "type": "upload",
            "size": 12345,
            "offset": 1200,
            "fileName": "foobar.txt",
            "sensitive": false,
            "target": "/home/WAYF-user/1234",
            "ts": {
                "start": 1000,
                "end": 11000
            }
        },
        {
            "jobId": "1111-XXXX-4123",
            "type": "file_listing",
            "at": "/home/WAYF-user/44",
            "ts": {
                "start": 2000,
                "end": 2050
            }
        },
        {
            "jobId": "1111-XXXX-YYYY",
            "type": "file_listing",
            "at": "/home/project42/",
            "ts": {
                "start": 3000,
                "end": 3050
            }
        },
        {
            "jobId": "1111-XXXX-1234",
            "type": "hpc_job",
            "status": "completed",
            "ts": {
                "start": 10000,
                "end": 70000
            }
        }
    ]
};

// FIXME END GET ACTUAL EVENTS

export {SidebarOptionsList, ActivityCardExample1}