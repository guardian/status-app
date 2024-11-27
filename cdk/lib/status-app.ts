import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import {Tags, type App } from "aws-cdk-lib";
import {InstanceClass, InstanceSize, InstanceType, UserData} from "aws-cdk-lib/aws-ec2";
import {AccessScope} from "@guardian/cdk/lib/constants";
import {GuEc2App} from "@guardian/cdk";

export class StatusApp extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const {stage: Stage} = props;
    const app = "status-app";

    const region = "eu-west-1"

    const userData = UserData.custom(`#!/bin/bash -ev
          aws --region ${region} s3 cp s3://membership-dist/${app}/${Stage}/status-app/status-app_1.0_all.deb .
          dpkg -i status-app_1.0_all.deb
          /opt/cloudwatch-logs/configure-logs application ${app} ${Stage} status-app /var/log/status-app/status-app.log
          `);

    const ec2 = new GuEc2App(this, {
      app,
      access: {
        scope: AccessScope.PUBLIC,
      },
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
      applicationPort: 9000,
      monitoringConfiguration: {noMonitoring: true},
      scaling: {minimumInstances: 1, maximumInstances: 2},
      userData,
    })
    ec2.targetGroup.healthCheck = {
      ...ec2.targetGroup.healthCheck,
      path: "/health-check"
    }

    Tags.of(ec2.autoScalingGroup).add("SystemdUnit", `${app}.service`);
  }
}
