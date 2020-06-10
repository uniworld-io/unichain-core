#!/bin/bash
# TODO: config version from git
set -x 
versionName='unichain-1.0.1'
versionCode='101'

versionPath="src/main/java/org/unichain/program/Version.java"
sed -i -e "s/versionName.*$/versionName = \"$versionName\";/g;s/versionCode.*$/versionCode = \"$versionCode\";/g" $versionPath

