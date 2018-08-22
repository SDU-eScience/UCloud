var webpack = require("webpack");
var webpackMerge = require("webpack-merge");
var commonConfig = require("./webpack.config.js");
var path = require("path");

const ENV = process.env.NODE_ENV = process.env.ENV = "production";

module.exports = webpackMerge(commonConfig, {
    // devtool: "source-map",

    mode: "production",

    output: {
        path: path.join(process.cwd(), "/dist"),
        filename: "[name].[hash].js",
        publicPath: "/assets/"
    },

    plugins: [
        new webpack.NoEmitOnErrorsPlugin(),
        // Allows creation of global constants which can be configured at compile time. 
        new webpack.DefinePlugin({
            "process.env": {
                NODE_ENV: JSON.stringify("production")
            }
        })
    ]
});