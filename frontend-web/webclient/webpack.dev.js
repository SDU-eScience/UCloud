var webpack = require('webpack');
var webpackMerge = require('webpack-merge');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
var commonConfig = require('./webpack.config.js');
var path = require('path');

console.warn("Running Development")

module.exports = webpackMerge(commonConfig, {
    devtool: "inline-source-map",

    mode: "development",

    entry: {
        //vendor: './app/Vendor.tsx',
        app: './app/App.tsx'
    },

    output: {
        path: path.join(process.cwd(), '/dist'),
        publicPath: 'http://localhost:9000/',
        filename: '[name].js',
    },

    plugins: [
        new webpack.HotModuleReplacementPlugin(),
        new MiniCSSExtractPlugin({
            // Options similar to the same options in webpackOptions.output
            // both options are optional
            filename: "[name].css",
            chunkFilename: "[id].css"
        }),
        new CopyWebpackPlugin([{
            from: 'mock-api',
            to: 'mock-api',
            context: path.join(__dirname, 'app')
        }])
    ],

    devServer: {
        historyApiFallback: true,
        stats: 'minimal',
        index: "app/index.html",
        headers: {
            'Access-Control-Allow-Origin': '*'
        },
        inline: true,
        proxy: [{
            context: ["/auth/login", "/auth/request", "/auth/login-redirect", "/api", "/auth/css/", "/auth/logout",
                "/auth/refresh", "/auth/fonts/", "/auth/sdu_plain_white.png", "/auth/wayf_logo.png",
                "/auth/saml/"],
            target: "https://cloud.sdu.dk",
            secure: false,
        }, {
            context: "/auth",
            target: "http://localhost:8080",
        }],
    }
});
