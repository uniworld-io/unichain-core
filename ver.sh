#!/bin/bash
set -x
versionName='2.0.4-SNAPSHOT'
versionCode='204'

versionPath="src/main/java/org/unichain/program/Version.java"
sed -i -e "s/versionName.*$/versionName = \"$versionName\";/g;s/versionCode.*$/versionCode = \"$versionCode\";/g" $versionPath

