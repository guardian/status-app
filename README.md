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

For best results, you'll want to allow the security group created by the Cloud 
Formation script, called something like status-app-EC2SecurityGroup-XXXXXXXXXXXX, 
access to the management port of your apps.

Running locally
---------------

If you just want to run it locally, it's a standard [Play 2](http://www.playframework.com/) 
app and can be run with the 'run' command from an [SBT](http://www.scala-sbt.org/) prompt.

You'll need a file in ~/.gu/statusapp.conf that has contents something like
```
accessKey=<AWS ACCESSKEY>
secretKey=<AWS SECRETKEY>
managementPort=<XXXX>
```

or to set environment variables analogously:
```
ACCESS_KEY=<AWS ACCESSKEY> SECRET_KEY=<AWS SECRETKEY> sbt
```

Contributing
------------

Pull requests are welcomed. If you hit a problem, or have an idea for improvments, 
open an issue, or let me know directly.

@philwills
