# Java tools and SDK for Directus 7

Always backup your data before using any of this! Many of the functions will destroy existing data.


## Quickstart

Requires JDK11+

And set api and token via environment variables:

    export DIRECTUS_ADMIN_TOKEN=some-uuid-set-as-token-for-an-admin ; export DIRECTUS_API_HOST=http://localhost:7000
    
Then build with:    

    ./gradlew  # build an executable jar, the very first time on a virgin environment this will download a few jars for gradle and one for this project
    
Then use with e.g. :
    
    alias directus="java -jar $PWD/build/libs/directus7toolsCli-0.1.1.jar" # set this in your profile or create a batch/cmd/shell command to wrap it 

    directus  # this prints some 'help'


    directus api_info # print api info


## What's this about

work in progress....

This is used in gradle tasks to promote Directus collection schema changes into different staging environments.

It can be used to publish bug reports in a consistent manner.
The number of dependencies is intentionally kept small to keep the jar small (the JDK is large enough).

Currently it consists of two parts:

* Directus Admin Java SDK
* Directus Admin Tools Java CLI

## Directus Java Admin SDK

A single class wrapping a few api endpoints to 

* create collections
* delete collections - this is not exposed in the CLI
* patch collections
* rename fields - this the main reason we started this, since the only way to do it is to create a new field with the new name and then copy the data and drop the old field
* drop fields
* create/delete M2O


## Directus Admin Tools CLI

An executable wrapping the Directus Java Admin SDK

## Use in Gradle Tassk

Check out https://github.com/markus-mnm/directus-demo-article-schema for an example of how to use this this project.

## To Dos

* Create a changelog of changes to keep track of schema changes, similar to liquibase (or implement it against as a liquibase extension via API calls in case that is feasible & sensible)
* Add permission grants (e.g. when adding a new collection, grant permission to a set of groups)
* Clean up
