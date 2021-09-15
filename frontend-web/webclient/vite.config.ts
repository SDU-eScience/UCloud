import {defineConfig, UserConfigExport} from "vite";
import reactRefresh from "@vitejs/plugin-react-refresh";
import path from "path";
import {DEV_SITE} from "./site.config.json";

// https://vitejs.dev/config/

type Mode = "development" | "local-dev" | "production";

function targetFromConfig(mode: Mode): string {
    switch (mode) {
        case "development":
            return `https://${DEV_SITE}`;
        case "local-dev":
        case "production":
        default:
            return `localhost:8080`
    }
}

export default ({mode, ...rest}: {mode: Mode}): UserConfigExport => {
    const sharedProxySetting = {
        target: targetFromConfig(mode),
        ws: true,
        changeOrigin: true,
        headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
        }
    }

    return defineConfig({
        clearScreen: false,
        define: {
            DEVELOPMENT_ENV: mode !== "production",
        },
        mode,
        assetsInclude: "./app/Assets/",
        plugins: [reactRefresh()],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./app")
            }
        },
        server: {
            port: 9000,
            cors: {
                origin: "*",
                methods: ["GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"],
                allowedHeaders: ["X-Requested-With", "content-type", "Authorization"]
            },
            // Use regex instead? These all match.
            proxy: {
                "/auth/": sharedProxySetting,
                "/api/": sharedProxySetting,
                "/ucloud/": sharedProxySetting,
                "/assets/Assets/AppVersion.txt": sharedProxySetting
            }
        }
    });
};
