const merge = require('webpack-merge');
const path = require('path');

module.exports = merge(require('./webpack.config.js'), {

  output: {
    path: path.resolve(__dirname, './app/webapp')
  }

});