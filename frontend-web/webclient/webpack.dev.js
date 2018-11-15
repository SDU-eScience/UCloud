var webpack = require("webpack");
var webpackMerge = require("webpack-merge");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
var commonConfig = require("./webpack.config.js");
var path = require("path");

module.exports = webpackMerge(commonConfig, {
    devtool: "inline-source-map",

    mode: "development",

    entry: {
        vendor: "./app/Vendor.tsx",
        app: "./app/App.tsx"
    },

    output: {
        path: path.join(process.cwd(), "/dist"),
        publicPath: "http://localhost:9000/",
        filename: "[name].js",
    },

    plugins: [
        // Enables Hot Module Replacement, otherwise known as HMR.
        // Note: HMR should never be used in production. 
        new webpack.HotModuleReplacementPlugin(),
        new MiniCSSExtractPlugin({
            filename: "[name].css",
            chunkFilename: "[id].css"
        }),
        // Copies individual files or entire directories to the build directory
        new CopyWebpackPlugin([{
            from: "mock-api",
            to: "mock-api",
            context: path.join(__dirname, "app")
        }])
    ],

    devServer: {
        historyApiFallback: true,
        stats: "minimal",
        contentBase: path.join(process.cwd(), "/dist"),
        index: "app/index.html",
        headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
        },
        hot: true,
        inline: true,
        proxy: [{
            context: ["/auth/login", "/auth/request", "/auth/login-redirect", "/api", "/auth/css/", "/auth/logout",
                "/auth/refresh", "/auth/fonts/", "/auth/sdu_plain_white.png", "/auth/wayf_logo.png",
                "/auth/saml/", "/auth/users/", "/auth/redirect.js"],
            target: "https://cloud.sdu.dk",
            secure: false,
            changeOrigin: true,
            onProxyRes(proxyRes, req, res) {
                if ("set-cookie" in proxyRes.headers) {
                    for (let i = 0; i < proxyRes.headers["set-cookie"].length; i++) {
                        proxyRes.headers["set-cookie"][i] = proxyRes.headers["set-cookie"][i].replace(/Secure;/g, "");
                    }
                }
                delete proxyRes.headers["strict-transport-security"];
            }
        }]
    }
});
