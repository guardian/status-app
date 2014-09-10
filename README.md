Guardian Status App
=====================================

Shows an overview of the status of autoscaling groups in an AWS account:

![Status app in action](screenshot.png)

How do I run it?
----------------

The simplest way to run it is to use the [Cloud Formation scripts](cloud-formation/). 

If you're tagging your auto-scaling groups according to the [Guardian conventions](https://github.com/guardian/prism/wiki/Tagging-conventions-for-AWS-and-Openstack), you should then
have something to see. Note that these Cloud Formation templates require the creation 
of new IAM resources.

The Status App uses OAuth with Google as the provider for authentication. You'll need to
create client ID in the Google API console and then copy the details into the DynamoDB table
created by the CloudFormation scripts.

![Example of filling in the the config details](dynamo-config.png)

Ensure that you have switched on access to the Google+ API for your credentials.

For best results, you'll want to allow the security group created by the Cloud 
Formation script, called something like status-app-EC2SecurityGroup-XXXXXXXXXXXX, 
access to the management port of your apps.

Running locally
---------------

If you just want to run it locally, it's a standard [Play 2](http://www.playframework.com/) 
app and can be run with the 'run' command from an [SBT](http://www.scala-sbt.org/) prompt.

Credentials are retrieved using from the configuration file used for the AWS CLI.

Contributing
------------

Pull requests are welcomed. If you hit a problem, or have an idea for improvments, 
open an issue, or let me know directly.

@philwills
