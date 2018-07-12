const merge = require('webpack-merge');
const path = require('path');
const ArchivePlugin = require('webpack-archive-plugin');

module.exports = merge(require('./webpack.config.js'), {

  plugins: [
    new ArchivePlugin({
      output: path.resolve(__dirname, 'target/app'),
      format: 'tar'
    })
  ]

});