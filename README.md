<h1 align="center">
  <br>
  <img width=20% src="https://github.com/uniworld-io/wiki/blob/master/images/unichain-core.jpg?raw=true">
  <br>
  unichain-core
  <br>
</h1>

<h4 align="center">
  Java implementation of the <a href="https://unichain.world">Unichain Protocol</a>
</h4>


<p align="center">
  <a href="https://gitter.im/uniworld-io/allcoredev">
    <img src="https://camo.githubusercontent.com/da2edb525cde1455a622c58c0effc3a90b9a181c/68747470733a2f2f6261646765732e6769747465722e696d2f4a6f696e253230436861742e737667">
  </a>

  <a href="https://travis-ci.org/uniworld-io/unichain-core">
    <img src="https://travis-ci.org/uniworld-io/unichain-core.svg?branch=develop">
  </a>

  <a href="https://codecov.io/gh/uniworld-io/unichain-core">
    <img src="https://codecov.io/gh/uniworld-io/unichain-core/branch/develop/graph/badge.svg" />
  </a>

  <a href="https://github.com/uniworld-io/unichain-core/issues">
    <img src="https://img.shields.io/github/issues/uniworld-io/unichain-core.svg">
  </a>

  <a href="https://github.com/uniworld-io/unichain-core/pulls">
    <img src="https://img.shields.io/github/issues-pr/uniworld-io/unichain-core.svg">
  </a>

  <a href="https://github.com/uniworld-io/unichain-core/graphs/contributors">
    <img src="https://img.shields.io/github/contributors/uniworld-io/unichain-core.svg">
  </a>

  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/uniworld-io/unichain-core.svg">
  </a>
</p>

## Table of Contents
- [What’s Unichain?](#What’s-Unichain)
- [Building the Source Code](#Building-the-source)
  - [Getting the Source Code](#Getting-the-Source-Code)
  - [Build](#Build)
- [Running unichain-core](#Running-unichain-core)
- [Community](#Community)
- [Contribution](#Contribution)
- [Resources](#Resources)
- [License](#License)

## What's Unichain?

Uni is a project dedicated to building the infrastructure for a truly decentralized Internet.

* Unichain Protocol, one of the largest blockchain-based operating systems in the world, offers scalable, high-availability and high-throughput support that underlies all the decentralized applications in the Unichain ecosystem.

* Unichain Virtual Machine (UVM) allows anyone to develop decentralized applications (DAPPs) for themselves or their communities with smart contracts thereby making decentralized crowdfunding and token issuance easier than ever.

Unichain enables large-scale development and engagement. With over 2000 transactions per second (TPS), high concurrency, low latency, and massive data transmission. It is ideal for building decentralized entertainment applications. Free features and incentive systems allow developers to create premium app experiences for users.

# Building the source
Building unichain-core requires `git` and `Oracle JDK 1.8` to be installed, other JDK versions are not supported yet. It is recommended to operate on `Linux` and `OSX` operating systems.

## Getting the Source Code

  ```bash
  $ git clone https://github.com/uniworld-io/unichain-core.git
  $ git checkout -t origin/master
  ```

## Build

```bash
$ cd unichain-core
$ ./gradlew clean build -x test
```

The `FullNode.jar` file can be found in `unichain-core/build/libs/FullNode.jar` after build successful.

# Running unichain-core

Get the mainnet configurate file: [main_net_config.conf](https://github.com/uniworld-io/Unichain-deployment/blob/master/main_net_config.conf), other network configuration files can be find [here](https://github.com/uniworld-io/Unichain-deployment).


* **Running a full node for mainnet**  
  Full node has full historical data, it is the entry point into the Unichain network , it can be used by other processes as a gateway into the Unichain network via HTTP and GRPC endpoints. You can interact with the Unichain network through full node：transfer assets, deploy contracts, interact with contracts and so on. `-c ` parameter specifies a configuration file to run a full node:
   ```bash
   $ java -jar FullNode.jar -c main_net_config.conf
   ```
* **Running a super representative node for mainnet**  
  Adding the `--witness` parameter to the startup command, full node will run as a super representative node. The super representative node supports all the functions of the full node and also supports block production. Before running, make sure you have a super representative account and get votes from others，once the number of obtained votes ranks in the top 27, your super representative node will participate in block production.

  Fill in the private key of super representative address into the `localwitness` list in the `main_net_config.conf`, here is an example:
   ```
    localwitness = [
        650950B193DDDDB35B6E48912DD28F7AB0E7140C1BFDEFD493348F02295BD812
    ]
   ```

  then run the following command to start the node:
    ```bash
    $ java -jar FullNode.jar --witness -c main_net_config.conf
    ```

## Quick Start Tool
An easier way to build and run unichain-core is to use `start.sh`, `start.sh` is a quick start script written in shell language, you can use it to build and run unichain-core quickly and easily.

Here are some common use cases of the scripting tool
* Use `start.sh` to start a full node with the downloaded `FullNode.jar`
* Use `start.sh` to download the latest `FullNode.jar` and start a full node.
* Use `start.sh` to download the latest source code and compile a `FullNode.jar` and then start a full node.

For more details, please refer to the tool [guide](./shell.md).

## Run inside Docker container 

One of the quickest ways to get `unichain-core` up and running on your machine is by using Docker:
```shell
$ docker run -d --name="unichain-core" \
              -v /your_path/output-directory:/unichain-core/output-directory \
              -v /your_path/logs:/unichain-core/logs \
              -p 8090:8090 -p 18888:18888 -p 50051:50051 \
              uniworld-io/unichain-core \
              -c /unichain-core/config/main_net_config.conf
```

This will mount the `output-directory` and `logs` directories on the host, the docker.sh tool can also be used to simplify the use of docker, see more [here](docker/docker.md).

# Community
[Unichain Developers & SRs](https://discord.gg/hqKvyAM) is Unichain's official Discord channel. Feel free to join this channel if you have any questions.

[Core Devs Community](https://t.me/Unichaincoredevscommunity) is the Telegram channel for unichain-core community developers. If you want to contribute to unichain-core, please join this channel.

[uniworld-io/allcoredev](https://gitter.im/uniworld-io/allcoredev) is the official Gitter channel for developers.

# Contribution
If you'd like to contribute to unichain-core, please read the following instructions.

- [Contribution](./CONTRIBUTING.md)

# Resources
* [Medium](https://medium.com/@coredevs) unichain-core's official technical articles are published there.
* [Documentation](https://uniworld-io.github.io/documentation-en/introduction/) unichain-core's official technical documentation website.
* [Test network](http://nileex.io/) A stable test network of Unichain contributed by Unichain community.
* [Uniscan](https://uniscan.org/#/) Unichain network blockchain browser.
* [Wallet-cli](https://github.com/uniworld-io/wallet-cli) Unichain network wallet using command line.
* [TIP](https://github.com/uniworld-io/tips) Unichain Improvement Proposal (TIP) describes standards for the Unichain network.
* [TP](https://github.com/uniworld-io/tips/tree/master/tp) Unichain Protocol (TP) describes standards already implemented in Unichain network but not published as a TIP.

# License
unichain-core is released under the [LGPLv3 license](https://github.com/uniworld-io/unichain-core/blob/master/LICENSE).
