interface SessionType {
    jobId: string;
    rank: number;
    target?: string;
}

interface ShellSession extends SessionType {
    type: "shell";
    sessionIdentifier: String,
}

export interface WebSession extends SessionType {
    type: "web";
    redirectClientTo: string;
}

interface VncSession extends SessionType {
    type: "vnc";
    url: string;
    password: string | null;
}

type OpenSession = ShellSession | WebSession | VncSession;

export interface JobsOpenInteractiveSessionResponse {
    responses: {
        providerDomain: string;
        providerId: string;
        session: OpenSession;
    }[];
}
