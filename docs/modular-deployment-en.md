# How to deploy unichain-core after modularization

After modularization, unichain-core is launched via shell script instead of typing command: `java -jar FullNode.jar`.

*`java -jar FullNode.jar` still works, but will be deprecated in future*.

## Download

```
git clone git@github.com:uniworld-io/unichain-core.git
```

## Compile

Change to project directory and run:
```
./gradlew build
```
unichain-core-1.0.0.zip will be generated in unichain-core/build/distributions after compilation.

## Unzip

Unzip unichain-core-1.0.0.zip
```
cd unichain-core/build/distributions
unzip -o unichain-core-1.0.0.zip
```
After unzip, two directories will be generated in unichain-core: `bin` and `lib`, shell scripts are located in `bin`, jars are located in `lib`.

## Startup

Use the corresponding script to start unichain-core according to the OS type, use `*.bat` on Windows, Linux demo is as below:
```
# default
unichain-core-1.0.0/bin/FullNode

# using config file, there are some demo configs in unichain-core/framework/build/resources
unichain-core-1.0.0/bin/FullNode -c config.conf

# when startup with SR mode，add parameter: -w
unichain-core-1.0.0/bin/FullNode -c config.conf -w
```

## JVM configuration

JVM options can also be specified, located in `bin/unichain-core.vmoptions`:
```
# demo
-XX:+UseConcMarkSweepGC
-XX:+PrintGCDetails
-Xloggc:./gc.log
-XX:+PrintGCDateStamps
-XX:+CMSParallelRemarkEnabled
-XX:ReservedCodeCacheSize=256m
-XX:+CMSScavengeBeforeRemark
```