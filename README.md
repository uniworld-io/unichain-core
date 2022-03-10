<p align="center">
    <img alt="uniworld" width="1412" src="https://uniworld.io/img/banner/1.jpg">
</p>

<p align="center">
    <a href="https://circleci.com/gh/AleoHQ/snarkOS"><img src="https://circleci.com/gh/AleoHQ/snarkOS.svg?style=svg&circle-token=6e9ad6d39d95350544f352d34e0e5c62ef54db26"></a>
    <a href="https://codecov.io/gh/AleoHQ/snarkOS"><img src="https://codecov.io/gh/AleoHQ/snarkOS/branch/master/graph/badge.svg?token=cck8tS9HpO"/></a>
    <a href="https://discord.com/channels/950290816981688340/950290816981688343"><img src="https://img.shields.io/discord/700454073459015690?logo=discord"/></a>
</p>

## UINICHAIN CORE
UniChain is a high secure and scalable blockchain platform for Smart Society 5.0. This repos implements the core components of UniChain blockchain including unichain-core node, database and key management tools. For more information, please check out UniChain website and UniWolrd ecosystem.
- [UniChain](https://unichain.world)
- [UniWorld](https://uniworld.io) 
- [UniMe](https://unime.world)
- [UniBot](https://unibot.org)
- [Mia Social Network](https://mia.world)


## Build application
### Prepare dependencies

* JDK 1.8 (JDK 1.9+ are not supported yet)
* On Linux Ubuntu system (e.g. Ubuntu 16.04.4 LTS), ensure that the machine has [__Oracle JDK 8__](https://www.digitalocean.com/community/tutorials/how-to-install-java-with-apt-get-on-ubuntu-16-04), instead of having __Open JDK 8__ in the system. If you are building the source code by using __Open JDK 8__, you may get fails

### Build application from source code
```bash
git clone https://github.com/uniworld-io/unichain-core.git
# checkout to stable version 
cd unichain-core
./gradlew build 
```
Binary files located in `build/libs` folder. 

### Run application
```bash 
cd build/libs
java -jar unichain-core.jar
     _   _ _ __ (_) ___| |__   __ _(_)_ __  
    | | | | '_ \| |/ __| '_ \ / _` | | '_ \ 
    | |_| | | | | | (__| | | | (_| | | | | |
     \__,_|_| |_|_|\___|_| |_|\__,_|_|_| |_|

```
Run unichain node with customized config
```bash
cd build/libs
java -jar unichain-core.jar -c ./your_localtion/of_config_file.conf
```
If you are witness, run unichain node with *--witness* and *-p* options (-p: witness private key)
```
java -jar unichain-core --witness -p your_witness_private_key
java -jar unichain-core --witness -p d06f6fbea126162c1bfac04869cf94331ca2a98610737e4b05b56527b0b8bf45
``` 
## Contributing
unichain-core is an open source project.
It is the work of contributors. We appreciate your help!
Thank you for all of [our contributors](https://github.com/uniworld-io/unichain-core/graphs/contributors). This project wouldn’t be what it is without you!


If you'd like to contribute to unichain-core, please fork, fix, commit and send a pull request for the maintainers to review and merge into the main code base.   

### Pull requests

First of all, unichain-core follows [gitflow workflow](
https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).
Please open pull requests to the **develop** branch. Once approved,
we will close the pull request and merge into master branch.

We are always happy to receive pull requests, and do our best to
review them as fast as possible. Not sure if that typo is worth a pull
request? Do it! We would appreciate it.

If your pull request is not accepted on the first try, don't be
discouraged as it can be a possible oversight. Please explain your code as
detailed as possible to make it easier for us to understand.

### Create issues

Any significant improvement should be documented as [a GitHub
issue](https://github.com/uniworld-io/unichain-core/issues) before anyone
starts working on it.

When filing an issue, make sure to answer these three questions:

- What did you do?
- What did you expect to see?
- What did you see instead?

## License
* [MIT](https://github.com/unichainprotocol/unichain-core/blob/master/LICENSE)
