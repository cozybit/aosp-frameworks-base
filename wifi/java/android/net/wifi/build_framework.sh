#!/bin/bash

pushd ~/dev/aosp_mesh/
. build/envsetup.sh
popd

mm -j4 || { echo "Something wrong happened when building"; exit 1;}
adb sync
adb shell stop
sleep 2
adb shell start
