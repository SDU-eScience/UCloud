var webpack = require('webpack');
var webpackMerge = require('webpack-merge');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var commonConfig = require('./webpack.common.js');
var path = require('path');

module.exports = webpackMerge(commonConfig, {
    devtool: '#cheap-module-eval-source-map',

    entry: {
        dev: [
            'webpack/hot/dev-server',
        ]
    },

    output: {
        path: path.join(process.cwd(), '/dist'),
        publicPath: 'http://localhost:9000/',
        filename: '[name].js',
        pathInfo: true
    },

    plugins: [
        new webpack.HotModuleReplacementPlugin(),
        new ExtractTextPlugin('[name].css')
    ],

    devServer: {
        historyApiFallback: true,
        stats: 'minimal',
        inline: true,
        hot: true,
        proxy: [{
            context: ["/auth/login", "/auth/login-redirect", "/api", "/auth/css/", "/auth/refresh"],
            target: "https://cloud.sdu.dk",
            secure: false, // FIXME HTTPS Should be secure
        }, {
            context: "/auth",
            target: "http://localhost:8080",
        } ],
    }
});
