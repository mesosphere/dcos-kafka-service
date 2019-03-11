

**make enter-container**

Launches the container mesosphere/dcos-commons with version present in build.gradle 

**make get-container-version**

Prints the container version that would be launched. 

```
root@00d5a2d9bd1a:/build# make get-container-version
mesosphere/dcos-commons:0.55.2
```

**make validate-aws-credentials**

Does a head bucket operation against `S3_BUCKET` to verify if valid credentials are configrued.

**make create-service-accounts**

Creates the service-account and secret necessary for the package to be installed. 

**make generate-package-options**

Creates a json file with pacakge options. As for now just configures the service account if they are needed.

**make detect-security-mode**

```
root@a6b3eda7abb6:/build# make detect-security-mode
Cluster security mode is strict
```

