import {GuEc2App} from '@guardian/cdk';
import {AccessScope} from '@guardian/cdk/lib/constants';
import type {GuStackProps} from '@guardian/cdk/lib/constructs/core';
import {GuParameter, GuStack, GuStringParameter} from '@guardian/cdk/lib/constructs/core';
import {GuAllowPolicy} from '@guardian/cdk/lib/constructs/iam';
import {type App, Duration, Tags} from 'aws-cdk-lib';
import {InstanceClass, InstanceSize, InstanceType, UserData,} from 'aws-cdk-lib/aws-ec2';
import {CfnRecordSet, RecordType} from 'aws-cdk-lib/aws-route53';

export class StatusApp extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stage, stack } = props;

		const app = 'status-app';
		const region = 'eu-west-1';

		new GuParameter(this, 'OAuthHost', {
			description: 'Host domain for the Status App',
			default: `/${stage}/status-app/oauth/host`,
			type: 'AWS::SSM::Parameter::Value<String>',
		});

		new GuParameter(this, 'OAuthProtocol', {
			description: 'Protocol for the Status App',
			default: `/${stage}/status-app/oauth/protocol`,
			type: 'AWS::SSM::Parameter::Value<String>',
		});

		new GuParameter(this, 'OAuthClientId', {
			description: 'Google OAuth client ID for authentication',
			default: `/${stage}/status-app/oauth/clientId`,
			type: 'AWS::SSM::Parameter::Value<String>',
		});

		new GuParameter(this, 'OAuthClientSecret', {
			description: 'Google OAuth client secret for authentication',
			default: `/${stage}/status-app/oauth/clientSecret`,
			type: 'AWS::SSM::Parameter::Value<String>',
		});

		new GuParameter(this, 'OAuthAllowedDomain', {
			description: 'Allowed domain for Google OAuth authentication',
			default: `/${stage}/status-app/oauth/allowedDomain`,
			type: 'AWS::SSM::Parameter::Value<String>',
		});

		const hostedZone = new GuStringParameter(this, 'HostedZone', {
			description:
				"DNS hosted zone for which A CNAME will be created. e.g. example.com (note, no trailing full-stop) for status.example.com. Leave empty if you don't want to add a CNAME to the status app",
		});

		const userData = UserData.custom(`#!/bin/bash -ev
          aws --region ${region} s3 cp s3://membership-dist/${stack}/${stage}/status-app/status-app_1.0_all.deb .
          dpkg -i status-app_1.0_all.deb
          /opt/cloudwatch-logs/configure-logs application ${stack} ${stage} status-app /var/log/status-app/status-app.log
          `);

		const ec2 = new GuEc2App(this, {
			app,
			access: {
				scope: AccessScope.PUBLIC,
			},
			instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
			applicationPort: 9000,
			monitoringConfiguration: { noMonitoring: true },
			scaling: { minimumInstances: 1, maximumInstances: 2 },
			userData,
			imageRecipe: 'ophan-ubuntu-jammy-ARM-CDK',
			roleConfiguration: {
				additionalPolicies: [
					new GuAllowPolicy(this, 'status-app-s3-access', {
						resources: [`arn:aws:s3::*:membership-dist/*`],
						actions: ['s3:GetObject'],
					}),
					new GuAllowPolicy(this, 'status-app-dynamo-access', {
						resources: [
							`arn:aws:dynamodb:${region}:${this.account}:table/StatusAppConfig`,
						],
						actions: ['dynamodb:GetItem'],
					}),
				],
			},
		});

		ec2.targetGroup.healthCheck = {
			...ec2.targetGroup.healthCheck,
			path: '/management/healthcheck',
			healthyThresholdCount: 2,
			unhealthyThresholdCount: 2,
			interval: Duration.seconds(10),
			timeout: Duration.seconds(5),
		};

		Tags.of(ec2.autoScalingGroup).add('SystemdUnit', `${stack}.service`);

		if (hostedZone.valueAsString) {
			new CfnRecordSet(this, "foo", {
				name:  `status.${hostedZone.valueAsString}`,
				comment: "CNAME for status app",
				type: RecordType.CNAME,
				hostedZoneName: hostedZone.valueAsString,
				ttl: "900",
				resourceRecords: [
					ec2.loadBalancer.loadBalancerDnsName
				],
			})
		}
	}
}
