# Java tools and SDK for Directus 7


test

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

This is work in progress it is based on some kotlin gradle tasks use internally to promote Directus collection schema changes into different staging environments, which was  decompiled into Java to be used outside of gradle in a more generally known format.

We decided to push this out to allow us to publish bug reports in a consistent manner.
We attempted to reduce the amount of dependencies to keep the jar small (the JDK is large enough).

Currently it consists of two parts:

* Directus Admin Java SDK
* Directus Admin Tools Java CLI

## Directus Java Admin SDK

A single class wrapping a few api endpoints to 

* create collections
* delete collections - but this is not exposed in the CLI
* patch collections
* rename fields - the main reason we started this, since the only way to do it is to create a new field with the new name and then copy the data and drop the old field
* drop fields
* create/delete M2O

and some other tidbits

## Directus Admin Tools CLI

An executable wrapping the Directus Java Admin SDK

20190927_163104
20190927_163209
