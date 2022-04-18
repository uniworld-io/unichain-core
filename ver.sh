#!/bin/bash
set -x
versionName='2.0.6'
versionCode='205'

versionPath="src/main/java/org/unichain/program/Version.java"
sed -i -e "s/versionName.*$/versionName = \"$versionName\";/g;s/versionCode.*$/versionCode = \"$versionCode\";/g" $versionPath

