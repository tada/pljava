#!/usr/bin/env bash

if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    curl -o ../macports.pkg -L https://github.com/macports/macports-base/releases/download/v2.6.2/MacPorts-2.6.2-10.14-Mojave.pkg
    sudo installer -pkg ../macports.pkg -target /
    export PATH=/opt/local/bin:/opt/local/sbin:$PATH
    yes | sudo port install openssl
    export CPATH=/opt/local/include:$CPATH
    export LIBRARY_PATH=/opt/local/lib:$LIBRARY_PATH
fi
