var webpack = require('webpack');
var webpackMerge = require('webpack-merge');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var commonConfig = require('./webpack.config.js');
var path = require('path');
const UglifyJSPlugin = require('uglifyjs-webpack-plugin');

const ENV = process.env.NODE_ENV = process.env.ENV = 'production';

module.exports = webpackMerge(commonConfig, {
    // devtool: 'source-map',

    output: {
        path: path.join(process.cwd(), '/dist'),
        filename: '[name].[hash].js'
    },

    plugins: [
        new webpack.NoEmitOnErrorsPlugin(),
        new ExtractTextPlugin('[name].[hash].css'),
        new UglifyJSPlugin({
            //uglifyOptions: {
            //    keep_fnames: true,
            //    except: ['$super']
            //}
            uglifyOptions: { 
                warnings: false,
                keep_fnames: true
            }
        }),
        new webpack.DefinePlugin({
            'process.env': {
                NODE_ENV: JSON.stringify('production')
            }
        })
    ]
});