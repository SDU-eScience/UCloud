import {defineConfig, UserConfigExport} from "vite";
import reactRefresh from "@vitejs/plugin-react-refresh";
import tsconfigPaths from "vite-tsconfig-paths";
import CONF from "./site.config.json";

// https://vitejs.dev/config/
export default ({mode, ...rest}: {mode: "development" | "local-dev" | "production"}): UserConfigExport => {

    const target = mode === "local-dev" ? "http://localhost:8080" : `https://${CONF.DEV_SITE}`;

    const sharedProxySetting = {
        target,
        ws: true,
        changeOrigin: true,
        headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
        }
    }

    return defineConfig({
        clearScreen: true,
        define: {
            DEVELOPMENT_ENV: mode !== "production",
        },
        assetsInclude: "./app/Assets/",
        plugins: [tsconfigPaths(), reactRefresh()],
        server: mode === "production" ? {port: 9000} : {
            port: 9000,
            cors: {
                origin: "*",
                methods: ["GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"],
                allowedHeaders: ["X-Requested-With", "content-type", "Authorization"]
            },
            /* Use regex instead? These all match. */
            proxy: {
                "/auth/": sharedProxySetting,
                "/api/": sharedProxySetting,
                "/ucloud/": sharedProxySetting
            }
        }
    });
}