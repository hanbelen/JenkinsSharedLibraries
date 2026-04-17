# Jenkins Shared Libraries
Standardized CI/CD blocks for the SRE Ecosystem.

## Contents
- `vars/`: Contains the global pipeline functions.
  - `standardNetworkDeploy.groovy`: The master workflow for network changes.

## How to use in a Jenkinsfile
```groovy
@Library('sre-lib') _
standardNetworkDeploy()
```
