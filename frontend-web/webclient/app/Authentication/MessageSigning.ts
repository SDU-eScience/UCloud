import {CallParameters} from "@/Authentication/CallParameters";
import {estimateRpcName} from "@/Authentication/RpcNameTable";
import {timestampUnixMs} from "@/UtilityFunctions";
import {KEYUTIL, KJUR} from "jsrsasign";
import {Client} from "@/Authentication/HttpClientInstance";
import {LocalStorageCache} from "@/Utilities/LocalStorageCache";

const SIGNING_ALGORITHM = "RS256";
const SIGNING_HEADER = {typ: "JWT", alg: SIGNING_ALGORITHM};

const SIGNING_LOCALSTORAGE_KEY_PREFIX = "signing-key-";
const privateKeyCache = new LocalStorageCache<string>(SIGNING_LOCALSTORAGE_KEY_PREFIX + "private");
const publicKeyCache = new LocalStorageCache<string>(SIGNING_LOCALSTORAGE_KEY_PREFIX + "public");
const keyUploadedToCache = new LocalStorageCache<string[]>(SIGNING_LOCALSTORAGE_KEY_PREFIX + "active");

function retrievePrivateKey(): jsrsasign.RSAKey | null {
    const privateKeyPem = privateKeyCache.retrieve();
    if (!privateKeyPem) return null;
    return KEYUTIL.getKey(privateKeyPem) as jsrsasign.RSAKey;
}

export function retrieveOrInitializePublicSigningKey(): string {
    const publicKey = publicKeyCache.retrieve();
    const privateKey = publicKeyCache.retrieve();
    if (!publicKey || !privateKey) {
        // NOTE(Dan): We are choosing not to password protect this key at the moment. Password protecting the key would
        // make it theoretically harder to steal by malware on the machine. However, the main threat actor to protect
        // against is a malicious UCloud frontend. Password protecting won't do much since it is in the perfect position
        // to impersonate UCloud (it is UCloud). As a result, it could easily just retrieve the password from the user.
        //
        // Retrieving a password from the user is also an inconvenience and most likely confusing to the end-user.

        const keypair = KEYUTIL.generateKeypair("RSA", 2048);
        const privateKey = KEYUTIL.getPEM(keypair.prvKeyObj, "PKCS8PRV");
        const publicKey = KEYUTIL.getPEM(keypair.pubKeyObj);

        publicKeyCache.update(publicKey);
        privateKeyCache.update(privateKey);

        return publicKey;
    }

    return publicKey;
}

export function clearSigningKey() {
    publicKeyCache.clear();
    privateKeyCache.clear();
    keyUploadedToCache.clear();
}

export function hasUploadedSigningKeyToProvider(provider: string): boolean {
    const uploadedTo = keyUploadedToCache.retrieve();
    if (!uploadedTo) return false;
    return uploadedTo.some(it => it === provider);
}

export function markSigningKeyAsUploadedToProvider(provider: string) {
    const current = keyUploadedToCache.retrieve() ?? [];
    current.push(provider);
    keyUploadedToCache.update(current);
}

export function signIntentToCall(parameters: CallParameters | string): string | null {
    const privateKey = retrievePrivateKey();
    if (privateKey == null) return null;

    const rpcName = typeof parameters === "string" ? parameters : estimateRpcName(parameters);
    if (rpcName == null) return null;

    const now = (timestampUnixMs() / 1000) | 0;
    const intentPayload = {
        iat: now,
        exp: now + 30,
        call: rpcName,
        username: Client.username,
        project: Client.projectId ?? null,
    };

    return KJUR.jws.JWS.sign(SIGNING_ALGORITHM, SIGNING_HEADER, intentPayload, privateKey);
}

